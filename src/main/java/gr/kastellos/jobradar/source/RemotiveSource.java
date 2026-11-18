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
import java.util.Locale;

/**
 * Remotive public API for remote jobs.
 * GET https://remotive.com/api/remote-jobs?category=software-dev&search={term}
 * Their terms ask you to link back to Remotive and not to call the API more than a few times a day,
 * which the default schedule respects. Only jobs whose allowed location includes Greece/Europe/Worldwide are kept.
 */
@Component
public class RemotiveSource implements JobSource {

    private static final Logger log = LoggerFactory.getLogger(RemotiveSource.class);
    private static final List<String> ALLOWED_REGIONS =
            List.of("greece", "europe", "eu", "emea", "worldwide", "anywhere");

    private final RestClient http;
    private final List<String> searches;

    public RemotiveSource(RestClient.Builder builder, RadarProperties props) {
        this.http = builder.baseUrl("https://remotive.com").build();
        this.searches = props.sources().remotiveSearches();
    }

    @Override
    public String name() {
        return "remotive";
    }

    @Override
    public List<JobPosting> fetch() {
        List<JobPosting> all = new ArrayList<>();
        for (String term : searches) {
            try {
                JsonNode body = http.get()
                        .uri("/api/remote-jobs?category=software-dev&search={term}", term)
                        .retrieve()
                        .body(JsonNode.class);
                all.addAll(parse(body));
            } catch (RuntimeException e) {
                log.warn("Remotive search '{}' failed: {}", term, e.getMessage());
            }
        }
        return all;
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
