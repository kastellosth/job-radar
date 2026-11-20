package gr.kastellos.jobradar.source;

import java.util.TreeSet;
import java.util.Locale;
import java.net.URLEncoder;
import com.fasterxml.jackson.databind.JsonNode;
import gr.kastellos.jobradar.domain.JobPosting;
import gr.kastellos.jobradar.domain.Provider;
import gr.kastellos.jobradar.domain.TrackedCompanyRepository;
import gr.kastellos.jobradar.matching.JobScorer;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Workday career sites (Accenture, PwC and many large employers), through the JSON search their own pages use:
 * POST https://{tenant}.wd{n}.myworkdayjobs.com/wday/cxs/{tenant}/{site}/jobs
 * It is not a documented API, so the parsing here is kept small and tested against saved answers.
 *
 * The tracked "slug" is the search itself, e.g. "accenture.wd103/AccentureCareers?locationCountry=566388c1...":
 * he filters the company's site to Greece and pastes that link (see CareersLink), and every run repeats that search.
 * The list has a title and a rough date at most; during a fetch run the full ad is read for jobs that could score,
 * which gives the description, the offices and the real posting date.
 */
@Component
public class WorkdaySource extends CompanyBoardSource {

    /** Workday answers at most 20 per page. */
    static final int PAGE = 20;
    /** More than this means the link was not filtered: reading a whole multinational's list helps no one. */
    static final int MAX_JOBS = 500;
    static final int MAX_DETAILS = 50;

    private static final ZoneId ATHENS = ZoneId.of("Europe/Athens");
    private static final Pattern DAYS_AGO = Pattern.compile("Posted (\\d+) Days? Ago", Pattern.CASE_INSENSITIVE);
    private static final Pattern SEVERAL_PLACES = Pattern.compile("\\d+ Locations?", Pattern.CASE_INSENSITIVE);

    private static final Pattern HOST = Pattern.compile("([a-z0-9][a-z0-9-]*)\\.(wd\\d{1,3})\\.myworkdayjobs\\.com");
    private static final Pattern LOCALE = Pattern.compile("[a-z]{2}-[A-Z]{2}");
    private static final Pattern SITE = Pattern.compile("[A-Za-z0-9_-]{1,100}");
    /** Filter values are ids like 566388c1eb974c42bd9e3da4c2f57d60; anything else in the link is ignored. */
    private static final Pattern FACET_ID = Pattern.compile("[0-9a-fA-F]{32}");
    private static final Pattern FACET_NAME = Pattern.compile("[A-Za-z][A-Za-z0-9_]{0,60}");

    private final RestClient http;
    private final JobScorer scorer;
    private final Clock clock;

    public WorkdaySource(RestClient.Builder builder, TrackedCompanyRepository companies, JobScorer scorer,
                         Clock clock) {
        super(companies);
        this.http = builder.build();
        this.scorer = scorer;
        this.clock = clock;
    }

    @Override
    public Provider provider() {
        return Provider.WORKDAY;
    }

    /** A tracked Workday search, read back from its slug. */
    record Search(String tenant, String instance, String site, Map<String, List<String>> facets, String text) {

        private static final Pattern SLUG = Pattern.compile("([a-z0-9-]+)\\.(wd\\d{1,3})/([A-Za-z0-9_-]+)(?:\\?(.*))?");

        static Search of(String slug) {
            Matcher m = SLUG.matcher(slug);
            if (!m.matches()) {
                throw new IllegalArgumentException("Not a Workday search: " + slug);
            }
            Map<String, List<String>> facets = new LinkedHashMap<>();
            String text = "";
            for (QueryString.Param param : QueryString.parse(m.group(4))) {
                if (param.key().equals("q")) {
                    text = param.value();
                } else {
                    facets.computeIfAbsent(param.key(), k -> new ArrayList<>()).add(param.value());
                }
            }
            return new Search(m.group(1), m.group(2), m.group(3), facets, text);
        }

        String host() {
            return tenant + "." + instance + ".myworkdayjobs.com";
        }

        String api() {
            return "https://" + host() + "/wday/cxs/" + tenant + "/" + site;
        }
    }

    @Override
    public List<JobPosting> fetchCompany(String slug) {
        Search search = Search.of(slug);
        List<JobPosting> out = new ArrayList<>();
        int total = -1;
        for (int offset = 0; total < 0 || offset < total; offset += PAGE) {
            JsonNode page = http.post()
                    .uri(search.api() + "/jobs")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(Map.of("appliedFacets", search.facets(), "limit", PAGE, "offset", offset,
                            "searchText", search.text()))
                    .retrieve()
                    .body(JsonNode.class);
            if (total < 0) {
                // Only the first page carries the total; later pages say 0.
                total = page == null ? 0 : page.path("total").asInt(0);
                if (total > MAX_JOBS) {
                    throw new IllegalArgumentException("This Workday search lists " + total
                            + " jobs, more than the " + MAX_JOBS + " Job Radar reads. Filter it to Greece on the"
                            + " careers site and paste that link");
                }
            }
            List<JobPosting> postings = parse(search, page, clock);
            if (postings.isEmpty()) {
                break;
            }
            out.addAll(postings);
        }
        return out;
    }

    static List<JobPosting> parse(Search search, JsonNode page, Clock clock) {
        List<JobPosting> out = new ArrayList<>();
        if (page == null) {
            return out;
        }
        for (JsonNode j : page.path("jobPostings")) {
            String path = Json.text(j, "externalPath");
            String title = Json.text(j, "title");
            if (path == null || title == null) {
                continue;
            }
            String places = Json.text(j, "locationsText");
            // "2 Locations" says nothing about where: left empty, the full ad fills it in.
            String location = places == null || SEVERAL_PLACES.matcher(places).matches() ? null : places;
            out.add(new JobPosting(
                    "workday",
                    path,
                    null, // Workday's list has no company name; the tracked company's is used
                    title,
                    location,
                    false,
                    "https://" + search.host() + "/" + search.site() + path,
                    "",
                    postedOn(Json.text(j, "postedOn"), clock)));
        }
        return out;
    }

    /** "Posted Today", "Posted Yesterday", "Posted 9 Days Ago" as an Athens day; "30+ Days Ago" is too vague: null. */
    static Instant postedOn(String text, Clock clock) {
        if (text == null) {
            return null;
        }
        LocalDate today = LocalDate.now(clock.withZone(ATHENS));
        String t = text.trim();
        if (t.equalsIgnoreCase("Posted Today")) {
            return today.atStartOfDay(ATHENS).toInstant();
        }
        if (t.equalsIgnoreCase("Posted Yesterday")) {
            return today.minusDays(1).atStartOfDay(ATHENS).toInstant();
        }
        Matcher m = DAYS_AGO.matcher(t);
        return m.matches() ? today.minusDays(Integer.parseInt(m.group(1))).atStartOfDay(ATHENS).toInstant() : null;
    }

    @Override
    protected List<JobPosting> complete(String slug, List<JobPosting> postings) {
        Search search = Search.of(slug);
        return readAds(slug, postings, scorer::worthReading, p -> withAd(p, readAd(search, p)), MAX_DETAILS);
    }

    private JsonNode readAd(Search search, JobPosting p) {
        URI uri = UriComponentsBuilder.fromUriString(search.api()).path(p.externalId()).build().encode().toUri();
        return http.get().uri(uri).accept(MediaType.APPLICATION_JSON).retrieve().body(JsonNode.class);
    }

    /** The full ad's description, offices (the accepted ones first), remote type, real date and link. */
    JobPosting withAd(JobPosting p, JsonNode detail) {
        JsonNode info = detail == null ? null : detail.path("jobPostingInfo");
        if (info == null || info.isMissingNode()) {
            return p;
        }
        List<String> places = new ArrayList<>();
        String main = Json.text(info, "location");
        if (main != null) {
            places.add(main);
        }
        info.path("additionalLocations").forEach(l -> places.add(l.asText()));
        List<String> accepted = places.stream().filter(scorer::acceptsPlace).toList();
        String location = String.join("; ", accepted.isEmpty() ? places : accepted);
        if (location.isBlank()) {
            location = p.location();
        } else if (location.length() > 250) {
            location = location.substring(0, 249) + "…";
        }

        String remoteType = Json.text(info, "remoteType");
        String url = Json.text(info, "externalUrl");
        Instant posted = p.postedAt();
        String start = Json.text(info, "startDate");
        if (start != null) {
            try {
                posted = LocalDate.parse(start).atStartOfDay(ATHENS).toInstant();
            } catch (DateTimeParseException ignored) {
                // keep the date from the list
            }
        }
        return p.withAd(location,
                remoteType != null && remoteType.toLowerCase().contains("remote"),
                url != null && url.startsWith("https://") ? url : p.url(),
                Json.plain(Json.text(info, "jobDescription")),
                posted);
    }

    /**
     * Its links: a job search with the filters chosen on the site (e.g. country Greece) kept in the slug, sorted,
     * so the same search pasted twice is the same company: "accenture.wd103/AccentureCareers?locationCountry=...".
     * Null for any other link.
     */
    static CareersLink link(Link link) {
        Matcher host = HOST.matcher(link.host());
        if (!host.matches()) {
            return null;
        }
        List<String> rest = link.path().stream().dropWhile(s -> LOCALE.matcher(s).matches()).toList();
        if (rest.isEmpty() || !SITE.matcher(rest.get(0)).matches()) {
            throw new IllegalArgumentException("Paste the link of the company's job search on Workday, e.g. "
                    + "https://acme.wd3.myworkdayjobs.com/AcmeCareers?locationCountry=...");
        }
        if (rest.size() > 1 && rest.get(1).equals("job")) {
            throw new IllegalArgumentException("That is a link to one job. Paste the company's job search instead");
        }
        TreeSet<String> filters = new TreeSet<>();
        for (QueryString.Param param : QueryString.parse(link.rawQuery())) {
            if (param.key().equals("q")) {
                String search = param.value().length() > 100 ? param.value().substring(0, 100) : param.value();
                if (!search.isBlank()) {
                    filters.add("q=" + URLEncoder.encode(search, StandardCharsets.UTF_8));
                }
            } else if (FACET_NAME.matcher(param.key()).matches() && FACET_ID.matcher(param.value()).matches()) {
                filters.add(param.key() + "=" + param.value().toLowerCase(Locale.ROOT));
            }
        }
        String slug = host.group(1) + "." + host.group(2) + "/" + rest.get(0)
                + (filters.isEmpty() ? "" : "?" + String.join("&", filters));
        if (slug.length() > 255) {
            throw new IllegalArgumentException("That Workday link has too many filters. Keep only the country");
        }
        return new CareersLink(Provider.WORKDAY, slug);
    }

    /** Workday gives no company name: the tenant in the link, "accenture" -> "Accenture", "pwc" -> "PWC". */
    static String nameFor(String slug) {
        return CareersLink.acronymOrName(slug.substring(0, slug.indexOf('.')));
    }
}
