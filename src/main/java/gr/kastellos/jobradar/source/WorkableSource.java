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
 * Workable public widget API, used by many Greek companies.
 * GET https://apply.workable.com/api/v1/widget/accounts/{account}?details=true
 * The account slug is the part after apply.workable.com/ in the company's careers URL.
 */
@Component
public class WorkableSource extends CompanyBoardSource {

    private final RestClient http;

    public WorkableSource(RestClient.Builder builder, TrackedCompanyRepository companies) {
        super(companies);
        this.http = builder.baseUrl("https://apply.workable.com").build();
    }

    @Override
    public Provider provider() {
        return Provider.WORKABLE;
    }

    @Override
    public List<JobPosting> fetchCompany(String account) {
        JsonNode body = http.get()
                .uri("/api/v1/widget/accounts/{account}?details=true", account)
                .retrieve()
                .body(JsonNode.class);
        return parse(body);
    }

    static List<JobPosting> parse(JsonNode body) {
        List<JobPosting> out = new ArrayList<>();
        if (body == null) {
            return out;
        }
        String company = Json.text(body, "name");
        for (JsonNode j : body.path("jobs")) {
            String city = Json.text(j, "city");
            String country = Json.text(j, "country");
            String location = join(city, country);
            boolean remote = j.path("telecommuting").asBoolean(false) || Json.looksRemote(location);
            out.add(new JobPosting(
                    "workable",
                    Json.text(j, "shortcode"),
                    company,
                    Json.text(j, "title"),
                    location,
                    remote,
                    Json.text(j, "url"),
                    Json.plain(Json.text(j, "description")),
                    Json.instant(j, "published_on")));
        }
        return out;
    }

    private static String join(String a, String b) {
        if (a == null || a.isBlank()) return b;
        if (b == null || b.isBlank()) return a;
        return a + ", " + b;
    }
}
