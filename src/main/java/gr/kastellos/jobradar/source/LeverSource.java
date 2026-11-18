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
 * Lever public Postings API.
 * GET https://api.lever.co/v0/postings/{company}?mode=json
 * The company slug is the part after jobs.lever.co/ in a careers URL.
 */
@Component
public class LeverSource implements JobSource {

    private static final Logger log = LoggerFactory.getLogger(LeverSource.class);

    private final RestClient http;
    private final List<String> companies;

    public LeverSource(RestClient.Builder builder, RadarProperties props) {
        this.http = builder.baseUrl("https://api.lever.co").build();
        this.companies = props.sources().lever();
    }

    @Override
    public String name() {
        return "lever";
    }

    @Override
    public List<JobPosting> fetch() {
        List<JobPosting> all = new ArrayList<>();
        for (String company : companies) {
            try {
                JsonNode body = http.get()
                        .uri("/v0/postings/{company}?mode=json", company)
                        .retrieve()
                        .body(JsonNode.class);
                all.addAll(parse(company, body));
            } catch (RuntimeException e) {
                log.warn("Lever company '{}' failed: {}", company, e.getMessage());
            }
        }
        return all;
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
