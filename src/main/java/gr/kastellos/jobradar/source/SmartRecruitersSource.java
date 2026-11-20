package gr.kastellos.jobradar.source;

import com.fasterxml.jackson.databind.JsonNode;
import gr.kastellos.jobradar.domain.JobPosting;
import gr.kastellos.jobradar.domain.Provider;
import gr.kastellos.jobradar.domain.TrackedCompanyRepository;
import gr.kastellos.jobradar.matching.JobScorer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

/**
 * SmartRecruiters public Posting API (used e.g. by Netcompany-Intrasoft).
 * GET https://api.smartrecruiters.com/v1/companies/{companyId}/postings
 * The list endpoint has no description. During a fetch run, the full ad (GET .../postings/{id}) is read for jobs
 * that can score at all (accepted location, software title), at most {@value #MAX_DETAILS} per company. Other jobs,
 * and any whose ad cannot be read, are scored on title, department and experience level.
 */
@Component
public class SmartRecruitersSource extends CompanyBoardSource {

    private static final Logger log = LoggerFactory.getLogger(SmartRecruitersSource.class);

    /** One extra request per job: a cap keeps a big company from slowing every run down. */
    static final int MAX_DETAILS = 50;

    private final RestClient http;
    private final JobScorer scorer;

    public SmartRecruitersSource(RestClient.Builder builder, TrackedCompanyRepository companies, JobScorer scorer) {
        super(companies);
        this.http = builder.baseUrl("https://api.smartrecruiters.com").build();
        this.scorer = scorer;
    }

    @Override
    public Provider provider() {
        return Provider.SMARTRECRUITERS;
    }

    @Override
    public List<JobPosting> fetchCompany(String company) {
        JsonNode body = http.get()
                .uri("/v1/companies/{company}/postings?limit=100", company)
                .retrieve()
                .body(JsonNode.class);
        List<JobPosting> jobs = parse(company, body);
        if (jobs.isEmpty()) {
            // SmartRecruiters answers 200 with an empty list even for an id that does not exist.
            log.warn("SmartRecruiters company '{}' has no jobs: check the id, or it has no openings now", company);
        }
        return jobs;
    }

    @Override
    protected List<JobPosting> complete(String company, List<JobPosting> postings) {
        List<JobPosting> out = new ArrayList<>(postings.size());
        int read = 0;
        int failed = 0;
        for (JobPosting p : postings) {
            if (read >= MAX_DETAILS || !scorer.worthReading(p)) {
                out.add(p);
                continue;
            }
            read++;
            try {
                JsonNode detail = http.get()
                        .uri("/v1/companies/{company}/postings/{id}", company, p.externalId())
                        .retrieve()
                        .body(JsonNode.class);
                out.add(withDescription(p, adText(detail)));
            } catch (RuntimeException e) {
                failed++;
                out.add(p);
            }
        }
        if (failed > 0) {
            log.warn("SmartRecruiters company '{}': {} of {} full ads could not be read; scored on title only",
                    company, failed, read);
        }
        return out;
    }

    /**
     * The ad's own sections as plain text. The company description is left out: it is the same boilerplate on
     * every ad and would give every job the same skill points.
     */
    static String adText(JsonNode detail) {
        if (detail == null) {
            return "";
        }
        JsonNode sections = detail.path("jobAd").path("sections");
        List<String> parts = new ArrayList<>();
        for (String section : List.of("jobDescription", "qualifications", "additionalInformation")) {
            String text = Json.plain(Json.text(sections.path(section), "text"));
            if (!text.isBlank()) {
                parts.add(text);
            }
        }
        return String.join("\n", parts);
    }

    /** Keeps the labels (e.g. "Entry Level") the list gave, in front of the ad text. */
    private static JobPosting withDescription(JobPosting p, String adText) {
        if (adText.isBlank()) {
            return p;
        }
        String description = p.description() == null || p.description().isBlank()
                ? adText
                : p.description() + "\n" + adText;
        return new JobPosting(p.source(), p.externalId(), p.company(), p.title(), p.location(), p.remote(),
                p.url(), description, p.postedAt());
    }

    static List<JobPosting> parse(String companyId, JsonNode body) {
        List<JobPosting> out = new ArrayList<>();
        if (body == null) {
            return out;
        }
        for (JsonNode p : body.path("content")) {
            JsonNode loc = p.path("location");
            String city = Json.text(loc, "city");
            String country = Json.text(loc, "country");
            String location = city == null ? country : city + (country == null ? "" : ", " + country.toUpperCase());
            String id = Json.text(p, "id");
            String companyName = Json.text(p.path("company"), "name");
            // No description in the list response: give the scorer what we have.
            String pseudoDescription = String.join(" ",
                    nz(Json.text(p.path("department"), "label")),
                    nz(Json.text(p.path("experienceLevel"), "label")),
                    nz(Json.text(p.path("function"), "label")));
            out.add(new JobPosting(
                    "smartrecruiters",
                    id,
                    companyName != null ? companyName : companyId,
                    Json.text(p, "name"),
                    location,
                    loc.path("remote").asBoolean(false),
                    "https://jobs.smartrecruiters.com/" + companyId + "/" + id,
                    pseudoDescription.trim(),
                    Json.instant(p, "releasedDate")));
        }
        return out;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
