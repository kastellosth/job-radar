package gr.kastellos.jobradar.source;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gr.kastellos.jobradar.domain.JobPosting;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Each source is tested against a saved sample response, so a change in mapping code
 * is caught without calling the real APIs.
 */
class SourceParsingTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private JsonNode fixture(String name) throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/fixtures/" + name)) {
            return mapper.readTree(in);
        }
    }

    @Test
    void workable() throws IOException {
        List<JobPosting> jobs = WorkableSource.parse(fixture("workable.json"));

        assertThat(jobs).hasSize(2);
        JobPosting first = jobs.get(0);
        assertThat(first.company()).isEqualTo("Acme Hellas");
        assertThat(first.title()).isEqualTo("Junior Backend Developer (Java)");
        assertThat(first.location()).isEqualTo("Athens, Greece");
        assertThat(first.remote()).isFalse();
        assertThat(first.description()).isEqualTo("Work with Java & Spring Boot. 0-2 years.");
        assertThat(first.postedAt()).isEqualTo(Instant.parse("2026-11-10T00:00:00Z"));
        assertThat(jobs.get(1).remote()).isTrue();
    }

    @Test
    void smartRecruiters() throws IOException {
        List<JobPosting> jobs = SmartRecruitersSource.parse("AcmeCo", fixture("smartrecruiters.json"));

        assertThat(jobs).hasSize(1);
        JobPosting job = jobs.get(0);
        assertThat(job.company()).isEqualTo("Acme Co");
        assertThat(job.location()).isEqualTo("Thessaloniki, GR");
        assertThat(job.url()).isEqualTo("https://jobs.smartrecruiters.com/AcmeCo/744000012345");
        assertThat(job.description()).contains("Entry Level");
    }

    @Test
    void greenhouseUnescapesHtmlContent() throws IOException {
        List<JobPosting> jobs = GreenhouseSource.parse("acme", fixture("greenhouse.json"));

        assertThat(jobs).singleElement().satisfies(job -> {
            assertThat(job.title()).isEqualTo("Graduate Software Engineer");
            assertThat(job.description()).isEqualTo("Java and React. Remote within Europe.");
            assertThat(job.remote()).isTrue();
        });
    }

    @Test
    void lever() throws IOException {
        List<JobPosting> jobs = LeverSource.parse("acme", fixture("lever.json"));

        assertThat(jobs).singleElement().satisfies(job -> {
            assertThat(job.title()).isEqualTo("Junior Full Stack Engineer");
            assertThat(job.remote()).isTrue();
            assertThat(job.postedAt()).isEqualTo(Instant.ofEpochMilli(1762790400000L));
        });
    }

    @Test
    void remotiveKeepsOnlyJobsOpenToGreece() throws IOException {
        List<JobPosting> jobs = RemotiveSource.parse(fixture("remotive.json"));

        assertThat(jobs).extracting(JobPosting::title)
                .containsExactly("Junior Java Developer", "Junior React Developer");
    }
}
