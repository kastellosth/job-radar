package gr.kastellos.jobradar.source;

import com.fasterxml.jackson.databind.JsonNode;
import gr.kastellos.jobradar.config.RadarProperties;
import gr.kastellos.jobradar.domain.JobPosting;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Remotive public API for remote jobs.
 * GET https://remotive.com/api/remote-jobs?category=software-dev
 * One call per run: the free API returns the same short list whatever the search term, and its terms ask for
 * at most four calls a day and a link back to Remotive (the dashboard shows "via Remotive" and links their page).
 * Only jobs whose allowed location includes Greece/Europe/Worldwide are kept.
 */
@Component
public class RemotiveSource implements JobSource {

    private static final List<String> ALLOWED_REGIONS =
            List.of("greece", "europe", "eu", "emea", "worldwide", "anywhere");

    private final RestClient http;
    private final boolean enabled;

    public RemotiveSource(RestClient.Builder builder, RadarProperties props) {
        this.http = builder.baseUrl("https://remotive.com").build();
        this.enabled = props.sources().remotive();
    }

    @Override
    public String name() {
        return "remotive";
    }

    /** One board, one call. Switched off, it reads nothing, so it has no health to report either. */
    @Override
    public List<BoardRead> fetch() {
        if (!enabled) {
            return List.of();
        }
        try {
            JsonNode body = http.get()
                    .uri("/api/remote-jobs?category=software-dev")
                    .retrieve()
                    .body(JsonNode.class);
            return List.of(BoardRead.ok(name(), parse(body)));
        } catch (RuntimeException e) {
            return List.of(BoardRead.failed(name(), e));
        }
    }

    static List<JobPosting> parse(JsonNode body) {
        List<JobPosting> out = new ArrayList<>();
        if (body == null) {
            return out;
        }
        for (JsonNode j : body.path("jobs")) {
            String allowed = Json.text(j, "candidate_required_location");
            if (!openToGreece(allowed)) {
                continue;
            }
            out.add(new JobPosting(
                    "remotive",
                    Json.text(j, "id"),
                    Json.text(j, "company_name"),
                    Json.text(j, "title"),
                    allowed,
                    true,
                    Json.text(j, "url"),
                    Json.plain(Json.text(j, "description")),
                    Json.instant(j, "publication_date")));
        }
        return out;
    }

    static boolean openToGreece(String allowed) {
        if (allowed == null || allowed.isBlank()) {
            return true;
        }
        String a = allowed.toLowerCase(Locale.ROOT);
        return ALLOWED_REGIONS.stream().anyMatch(a::contains);
    }
}
