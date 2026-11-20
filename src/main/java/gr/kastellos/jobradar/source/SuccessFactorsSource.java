package gr.kastellos.jobradar.source;

import gr.kastellos.jobradar.domain.JobPosting;
import gr.kastellos.jobradar.domain.Provider;
import gr.kastellos.jobradar.domain.TrackedCompanyRepository;
import gr.kastellos.jobradar.matching.JobScorer;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SAP SuccessFactors career sites (EY, Vodafone, Kotsovolos and many large employers). They have no public API,
 * so this reads the search results page itself, the part robots.txt allows; a redesign can break it, which then
 * shows as a failing board in the dashboard while the jobs it found before stay.
 *
 * The tracked "slug" is the search: host, the site's path (EY has one: /ey) and the filters from the link he pasted,
 * e.g. "careers.ey.com/ey?locationsearch=Greece". Results come 25 or 30 per page (a table, or cards), walked with
 * startrow. The list has title, link and place, sometimes a date; during a fetch run the job's own page is read
 * for the jobs that could score, which gives the description and the posting date.
 */
@Component
public class SuccessFactorsSource extends CompanyBoardSource {

    /** More than this means the link was not filtered. */
    static final int MAX_JOBS = 500;
    static final int MAX_DETAILS = 50;

    private static final ZoneId ATHENS = ZoneId.of("Europe/Athens");
    private static final Pattern ID = Pattern.compile("/(\\d{3,})/?$");
    private static final Pattern MORE_PLACES = Pattern.compile("\\s*\\+\\d+ more.*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern TILE_TOTAL = Pattern.compile("jobRecordsFound:\\s*parseInt\\(\"(\\d+)\"\\)");
    private static final Pattern COUNTRY_CODE = Pattern.compile("[A-Z]{2}");
    /** "Wed Oct 28 02:00:00 UTC 2026", as job pages give datePosted. */
    private static final DateTimeFormatter POSTED =
            DateTimeFormatter.ofPattern("EEE MMM dd HH:mm:ss zzz yyyy", Locale.ENGLISH);
    /** "5 Nov 2026", as some result tables give the date. */
    private static final DateTimeFormatter LIST_DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    private final RestClient http;
    private final JobScorer scorer;

    public SuccessFactorsSource(RestClient.Builder builder, TrackedCompanyRepository companies, JobScorer scorer) {
        super(companies);
        this.http = builder.build();
        this.scorer = scorer;
    }

    @Override
    public Provider provider() {
        return Provider.SUCCESSFACTORS;
    }

    /** A tracked search, read back from its slug "host/site?filters". */
    record Search(String host, String site, String query) {

        static Search of(String slug) {
            int q = slug.indexOf('?');
            String place = q < 0 ? slug : slug.substring(0, q);
            int slash = place.indexOf('/');
            return new Search(slash < 0 ? place : place.substring(0, slash), slash < 0 ? "" : place.substring(slash),
                    q < 0 ? "" : slug.substring(q + 1));
        }

        URI page(int startrow) {
            String filters = query.isEmpty() ? "" : query + "&";
            return URI.create("https://" + host + site + "/search/?" + filters + "startrow=" + startrow);
        }

        URI job(String path) {
            return URI.create("https://" + host + path);
        }
    }

    /** One page of results, and the total the page announces (-1 when it does not say). */
    record Page(List<JobPosting> postings, int total) {
    }

    @Override
    public List<JobPosting> fetchCompany(String slug) {
        Search search = Search.of(slug);
        Map<String, JobPosting> jobs = new LinkedHashMap<>();
        int startrow = 0;
        while (true) {
            String html = http.get().uri(search.page(startrow)).accept(MediaType.TEXT_HTML)
                    .retrieve().body(String.class);
            Page page = parse(search, html);
            if (page.total() > MAX_JOBS) {
                throw new IllegalArgumentException("This job search lists " + page.total() + " jobs, more than the "
                        + MAX_JOBS + " Job Radar reads. Search for Greece on the careers site and paste that link");
            }
            int before = jobs.size();
            page.postings().forEach(p -> jobs.putIfAbsent(p.externalId(), p));
            // Stop when the page adds nothing (end of the list, or a site that ignores startrow).
            if (jobs.size() == before || (page.total() >= 0 && jobs.size() >= page.total())
                    || jobs.size() >= MAX_JOBS) {
                break;
            }
            startrow += page.postings().size();
        }
        return new ArrayList<>(jobs.values());
    }

    /**
     * Results as a table (EY, Vodafone: tr.data-row) or as cards (Kotsovolos: .job-tile). A page that has neither
     * and none of SuccessFactors' marks is not a SuccessFactors search at all.
     */
    static Page parse(Search search, String body) {
        String html = body == null ? "" : body;
        Document doc = Jsoup.parse(html);
        List<Element> rows = doc.select("tr.data-row, li.job-tile, div.job-tile");
        if (rows.isEmpty() && doc.select("a.jobTitle-link").isEmpty() && !html.contains("j2w.")) {
            throw new IllegalArgumentException("That page is not a SuccessFactors job search. Paste the link of the"
                    + " careers site's search results (it contains /search/)");
        }
        List<JobPosting> out = new ArrayList<>();
        for (Element row : rows) {
            Element link = row.selectFirst("a.jobTitle-link");
            if (link == null) {
                continue;
            }
            String path = link.attr("href");
            Matcher id = ID.matcher(path);
            String title = link.text().trim();
            if (!id.find() || title.isEmpty()) {
                continue;
            }
            String place = firstText(row, "span.jobLocation");
            if (place == null) {
                place = fieldValue(row, ".section-field.city");
            }
            String date = firstText(row, "span.jobDate");
            if (date == null) {
                date = fieldValue(row, ".section-field.date");
            }
            out.add(new JobPosting(
                    "successfactors",
                    id.group(1),
                    null, // no company name on the page; the tracked company's is used
                    title,
                    place(place),
                    place != null && place.toLowerCase(Locale.ROOT).contains("remote"),
                    search.job(path).toString(),
                    "",
                    listDate(date)));
        }
        return new Page(out, total(doc, html));
    }

    private static int total(Document doc, String html) {
        // Table layout: "Results <b>1 – 25</b> of <b>76</b>", in the site's language: the last number is the total.
        var bold = doc.select(".paginationLabel b");
        if (!bold.isEmpty()) {
            String digits = bold.last().text().replaceAll("\\D", "");
            if (!digits.isEmpty()) {
                return Integer.parseInt(digits);
            }
        }
        // Card layout: the total is in the page's script settings.
        Matcher m = TILE_TOTAL.matcher(html);
        return m.find() ? Integer.parseInt(m.group(1)) : -1;
    }

    private static String firstText(Element row, String css) {
        for (Element e : row.select(css)) {
            String t = e.text().trim();
            if (!t.isEmpty()) {
                return t;
            }
        }
        return null;
    }

    /** A card field is "label value": the value is what is left without its label. */
    private static String fieldValue(Element row, String css) {
        Element field = row.selectFirst(css);
        if (field == null) {
            return null;
        }
        Element label = field.selectFirst(".section-label");
        String text = field.text();
        if (label != null) {
            text = text.replace(label.text(), "");
        }
        text = text.trim();
        return text.isEmpty() ? null : text;
    }

    /** "Athens, GR, 15125 +4 more…" becomes "Athens, Greece, 15125": a lone country code is written out. */
    static String place(String raw) {
        if (raw == null) {
            return null;
        }
        String s = MORE_PLACES.matcher(raw).replaceAll("").trim();
        List<String> parts = new ArrayList<>();
        for (String part : s.split(",")) {
            String p = part.trim();
            if (COUNTRY_CODE.matcher(p).matches()) {
                String name = Locale.of("", p).getDisplayCountry(Locale.ENGLISH);
                p = name.isEmpty() || name.equals(p) ? p : name;
            }
            if (!p.isEmpty()) {
                parts.add(p);
            }
        }
        String out = String.join(", ", parts);
        return out.isEmpty() ? null : out.length() > 250 ? out.substring(0, 249) + "…" : out;
    }

    private static Instant listDate(String text) {
        if (text == null) {
            return null;
        }
        try {
            return LocalDate.parse(text.trim(), LIST_DATE).atStartOfDay(ATHENS).toInstant();
        } catch (DateTimeParseException e) {
            return null; // another language or format: the job page gives it
        }
    }

    @Override
    protected List<JobPosting> complete(String slug, List<JobPosting> postings) {
        return readAds(slug, postings, scorer::worthReading, p -> withAd(p, http.get().uri(URI.create(p.url()))
                .accept(MediaType.TEXT_HTML).retrieve().body(String.class)), MAX_DETAILS);
    }

    /** The job page's description and posting date (schema.org markup every SuccessFactors job page carries). */
    static JobPosting withAd(JobPosting p, String html) {
        Document doc = Jsoup.parse(html == null ? "" : html);
        Element description = doc.selectFirst(".jobdescription, [itemprop=description]");
        Instant posted = p.postedAt();
        Element date = doc.selectFirst("[itemprop=datePosted]");
        if (date != null) {
            String value = date.hasAttr("content") ? date.attr("content") : date.text();
            try {
                posted = ZonedDateTime.parse(value.trim(), POSTED).toInstant();
            } catch (DateTimeParseException ignored) {
                // keep what the list gave
            }
        }
        return new JobPosting(p.source(), p.externalId(), p.company(), p.title(), p.location(), p.remote(), p.url(),
                description == null ? p.description() : description.text(), posted, p.pay());
    }
}
