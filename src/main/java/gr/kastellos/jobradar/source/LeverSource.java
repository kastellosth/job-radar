package gr.kastellos.jobradar.source;

import com.fasterxml.jackson.databind.JsonNode;
import gr.kastellos.jobradar.domain.JobPosting;
import gr.kastellos.jobradar.domain.Provider;
import gr.kastellos.jobradar.domain.TrackedCompanyRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

/**
 * Lever public Postings API.
 * GET https://api.lever.co/v0/postings/{company}?mode=json
 * The company slug is the part after jobs.lever.co/ in a careers URL.
 */
@Component
public class LeverSource extends CompanyBoardSource {

    private final RestClient http;

    public LeverSource(RestClient.Builder builder, TrackedCompanyRepository companies) {
        super(companies);
        this.http = builder.baseUrl("https://api.lever.co").build();
    }

    @Override
    public Provider provider() {
        return Provider.LEVER;
    }

    @Override
    public List<JobPosting> fetchCompany(String company) {
        JsonNode body = http.get()
                .uri("/v0/postings/{company}?mode=json", company)
                .retrieve()
                .body(JsonNode.class);
        return parse(company, body);
    }

    static List<JobPosting> parse(String company, JsonNode body) {
        List<JobPosting> out = new ArrayList<>();
        if (body == null) {
            return out;
        }
        for (JsonNode j : body) {
            String location = Json.text(j.path("categories"), "location");
            String workplace = Json.text(j, "workplaceType");
            out.add(new JobPosting(
                    "lever",
                    Json.text(j, "id"),
                    company,
                    Json.text(j, "text"),
                    location,
                    "remote".equalsIgnoreCase(workplace) || Json.looksRemote(location),
                    Json.text(j, "hostedUrl"),
                    Json.text(j, "descriptionPlain"),
                    Json.instant(j, "createdAt")));
        }
        return out;
    }
}
