package gr.kastellos.jobradar.source;

import com.fasterxml.jackson.databind.JsonNode;
import gr.kastellos.jobradar.domain.JobPosting;
import gr.kastellos.jobradar.domain.Provider;
import gr.kastellos.jobradar.domain.TrackedCompanyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

/**
 * SmartRecruiters public Posting API (used e.g. by Netcompany-Intrasoft).
 * GET https://api.smartrecruiters.com/v1/companies/{companyId}/postings
 * The list endpoint has no description, so scoring here relies on title, department and experience level.
 * (Next step idea: fetch /postings/{id} for the full jobAd text, only for jobs whose title already looks promising.)
 */
@Component
public class SmartRecruitersSource extends CompanyBoardSource {

    private static final Logger log = LoggerFactory.getLogger(SmartRecruitersSource.class);

    private final RestClient http;

    public SmartRecruitersSource(RestClient.Builder builder, TrackedCompanyRepository companies) {
        super(companies);
        this.http = builder.baseUrl("https://api.smartrecruiters.com").build();
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
