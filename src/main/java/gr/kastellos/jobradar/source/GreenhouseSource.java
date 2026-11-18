package gr.kastellos.jobradar.source;

import com.fasterxml.jackson.databind.JsonNode;
import gr.kastellos.jobradar.config.RadarProperties;
import gr.kastellos.jobradar.domain.JobPosting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

/**
 * Greenhouse Job Board API.
 * GET https://boards-api.greenhouse.io/v1/boards/{boardToken}/jobs?content=true
 * The board token is the part after boards.greenhouse.io/ (or job-boards.greenhouse.io/) in a careers URL.
 */
@Component
public class GreenhouseSource implements JobSource {

    private static final Logger log = LoggerFactory.getLogger(GreenhouseSource.class);

    private final RestClient http;
    private final List<String> boards;

    public GreenhouseSource(RestClient.Builder builder, RadarProperties props) {
        this.http = builder.baseUrl("https://boards-api.greenhouse.io").build();
        this.boards = props.sources().greenhouse();
    }

    @Override
    public String name() {
        return "greenhouse";
    }

    @Override
    public List<JobPosting> fetch() {
        List<JobPosting> all = new ArrayList<>();
        for (String board : boards) {
            try {
                JsonNode body = http.get()
                        .uri("/v1/boards/{board}/jobs?content=true", board)
                        .retrieve()
                        .body(JsonNode.class);
                all.addAll(parse(board, body));
            } catch (RuntimeException e) {
                log.warn("Greenhouse board '{}' failed: {}", board, e.getMessage());
            }
        }
        return all;
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
                    Json.instant(j, "updated_at")));
        }
        return out;
    }
}
