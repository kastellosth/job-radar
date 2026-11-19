package gr.kastellos.jobradar.source;

import gr.kastellos.jobradar.config.RadarProperties;
import gr.kastellos.jobradar.domain.JobPosting;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * What each source asks the provider for, checked against a fake HTTP server instead of the real API.
 * (SourceParsingTest covers how the answers are mapped.)
 */
@ExtendWith(OutputCaptureExtension.class)
class SourceFetchTest {

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

    private static RadarProperties sources(List<String> smartrecruiters, boolean remotive) {
        return new RadarProperties(
                new RadarProperties.Sources(List.of(), smartrecruiters, List.of(), List.of(), remotive), null);
    }

    private static String fixture(String name) throws IOException {
        try (InputStream in = SourceFetchTest.class.getResourceAsStream("/fixtures/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void remotiveIsCalledOncePerRun() throws IOException {
        server.expect(once(), requestTo("https://remotive.com/api/remote-jobs?category=software-dev"))
                .andRespond(withSuccess(fixture("remotive.json"), MediaType.APPLICATION_JSON));

        List<JobPosting> jobs = new RemotiveSource(builder, sources(List.of(), true)).fetch();

        server.verify();
        assertThat(jobs).extracting(JobPosting::title)
                .containsExactly("Junior Java Developer", "Junior React Developer");
    }

    @Test
    void remotiveSwitchedOffMakesNoCall() {
        List<JobPosting> jobs = new RemotiveSource(builder, sources(List.of(), false)).fetch();

        server.verify();
        assertThat(jobs).isEmpty();
    }

    @Test
    void smartRecruitersWarnsWhenACompanyHasNoJobs(CapturedOutput output) {
        server.expect(requestTo("https://api.smartrecruiters.com/v1/companies/NoSuchCo/postings?limit=100"))
                .andRespond(withSuccess("{\"offset\":0,\"limit\":100,\"totalFound\":0,\"content\":[]}",
                        MediaType.APPLICATION_JSON));

        List<JobPosting> jobs = new SmartRecruitersSource(builder, sources(List.of("NoSuchCo"), false)).fetch();

        assertThat(jobs).isEmpty();
        assertThat(output).contains("SmartRecruiters company 'NoSuchCo' has no jobs");
    }

    @Test
    void smartRecruitersDoesNotWarnWhenThereAreJobs(CapturedOutput output) throws IOException {
        server.expect(requestTo("https://api.smartrecruiters.com/v1/companies/AcmeCo/postings?limit=100"))
                .andRespond(withSuccess(fixture("smartrecruiters.json"), MediaType.APPLICATION_JSON));

        List<JobPosting> jobs = new SmartRecruitersSource(builder, sources(List.of("AcmeCo"), false)).fetch();

        assertThat(jobs).hasSize(1);
        assertThat(output).doesNotContain("has no jobs");
    }
}
