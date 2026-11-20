package gr.kastellos.jobradar.source;

import java.util.Set;
import com.fasterxml.jackson.databind.JsonNode;
import gr.kastellos.jobradar.domain.JobPosting;
import gr.kastellos.jobradar.domain.Provider;
import gr.kastellos.jobradar.domain.TrackedCompanyRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Greenhouse Job Board API.
 * GET https://boards-api.greenhouse.io/v1/boards/{boardToken}/jobs?content=true
 * The board token is the part after boards.greenhouse.io/ (or job-boards.greenhouse.io/) in a careers URL.
 */
@Component
public class GreenhouseSource extends CompanyBoardSource {

    private final RestClient http;

    public GreenhouseSource(RestClient.Builder builder, TrackedCompanyRepository companies) {
        super(companies);
        this.http = builder.baseUrl("https://boards-api.greenhouse.io").build();
    }

    @Override
    public Provider provider() {
        return Provider.GREENHOUSE;
    }

    @Override
    public List<JobPosting> fetchCompany(String board) {
        JsonNode body = http.get()
                .uri("/v1/boards/{board}/jobs?content=true", board)
                .retrieve()
                .body(JsonNode.class);
        return parse(board, body);
    }

    static List<JobPosting> parse(String board, JsonNode body) {
        List<JobPosting> out = new ArrayList<>();
        if (body == null) {
            return out;
        }
        for (JsonNode j : body.path("jobs")) {
            String location = Json.text(j.path("location"), "name");
            String company = Json.text(j, "company_name");
            out.add(new JobPosting(
                    "greenhouse",
                    Json.text(j, "id"),
                    company != null ? company : board,
                    Json.text(j, "title"),
                    location,
                    Json.looksRemote(location),
                    Json.text(j, "absolute_url"),
                    Json.plain(Json.text(j, "content")),
                    postedAt(j)));
        }
        return out;
    }

    /** updated_at changes whenever the ad is edited; first_published is when it went online. */
    private static Instant postedAt(JsonNode job) {
        Instant published = Json.instant(job, "first_published");
        return published != null ? published : Json.instant(job, "updated_at");
    }

    /** Its links: boards.greenhouse.io/kaizengaming or job-boards.greenhouse.io/kaizengaming. Null otherwise. */
    static CareersLink link(Link link) {
        return Set.of("boards.greenhouse.io", "job-boards.greenhouse.io").contains(link.host())
                ? link.simple(Provider.GREENHOUSE, link.first(), false) : null;
    }
}
