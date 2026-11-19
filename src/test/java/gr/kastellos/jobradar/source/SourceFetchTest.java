package gr.kastellos.jobradar.source;

import gr.kastellos.jobradar.config.RadarProperties;
import gr.kastellos.jobradar.domain.JobPosting;
import gr.kastellos.jobradar.domain.Provider;
import gr.kastellos.jobradar.domain.TrackedCompany;
import gr.kastellos.jobradar.domain.TrackedCompanyRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * What each source asks the provider for, checked against a fake HTTP server instead of the real API.
 * (SourceParsingTest covers how the answers are mapped.)
 */
@ExtendWith(OutputCaptureExtension.class)
class SourceFetchTest {

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final TrackedCompanyRepository companies = mock(TrackedCompanyRepository.class);

    private static String fixture(String name) throws IOException {
        try (InputStream in = SourceFetchTest.class.getResourceAsStream("/fixtures/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private void tracking(Provider provider, String... slugs) {
        when(companies.findByProvider(provider)).thenReturn(java.util.Arrays.stream(slugs)
                .map(slug -> TrackedCompany.of(provider, slug, slug, Instant.EPOCH))
                .toList());
    }

    private static RadarProperties remotive(boolean on) {
        return new RadarProperties(new RadarProperties.Sources(on), null);
    }

    @Test
    void boardSourceReadsEveryTrackedCompanyAndSkipsOneThatFails() throws IOException {
        tracking(Provider.WORKABLE, "gone", "acme");
        server.expect(requestTo("https://apply.workable.com/api/v1/widget/accounts/gone?details=true"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));
        server.expect(requestTo("https://apply.workable.com/api/v1/widget/accounts/acme?details=true"))
                .andRespond(withSuccess(fixture("workable.json"), MediaType.APPLICATION_JSON));

        List<JobPosting> jobs = new WorkableSource(builder, companies).fetch();

        server.verify();
        assertThat(jobs).hasSize(2);
    }

    @Test
    void fetchingOneUnknownCompanyThrowsSoItCanBeRejected() {
        server.expect(requestTo("https://boards-api.greenhouse.io/v1/boards/nope/jobs?content=true"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> new GreenhouseSource(builder, companies).fetchCompany("nope"))
                .isInstanceOf(HttpClientErrorException.NotFound.class);
    }

    @Test
    void remotiveIsCalledOncePerRun() throws IOException {
        server.expect(once(), requestTo("https://remotive.com/api/remote-jobs?category=software-dev"))
                .andRespond(withSuccess(fixture("remotive.json"), MediaType.APPLICATION_JSON));

        List<JobPosting> jobs = new RemotiveSource(builder, remotive(true)).fetch();

        server.verify();
        assertThat(jobs).extracting(JobPosting::title)
                .containsExactly("Junior Java Developer", "Junior React Developer");
    }

    @Test
    void remotiveSwitchedOffMakesNoCall() {
        List<JobPosting> jobs = new RemotiveSource(builder, remotive(false)).fetch();

        server.verify();
        assertThat(jobs).isEmpty();
    }

    @Test
    void smartRecruitersWarnsWhenACompanyHasNoJobs(CapturedOutput output) {
        server.expect(requestTo("https://api.smartrecruiters.com/v1/companies/NoSuchCo/postings?limit=100"))
                .andRespond(withSuccess("{\"offset\":0,\"limit\":100,\"totalFound\":0,\"content\":[]}",
                        MediaType.APPLICATION_JSON));

        List<JobPosting> jobs = new SmartRecruitersSource(builder, companies).fetchCompany("NoSuchCo");

        assertThat(jobs).isEmpty();
        assertThat(output).contains("SmartRecruiters company 'NoSuchCo' has no jobs");
    }

    @Test
    void smartRecruitersDoesNotWarnWhenThereAreJobs(CapturedOutput output) throws IOException {
        server.expect(requestTo("https://api.smartrecruiters.com/v1/companies/AcmeCo/postings?limit=100"))
                .andRespond(withSuccess(fixture("smartrecruiters.json"), MediaType.APPLICATION_JSON));

        List<JobPosting> jobs = new SmartRecruitersSource(builder, companies).fetchCompany("AcmeCo");

        assertThat(jobs).hasSize(1);
        assertThat(output).doesNotContain("has no jobs");
    }
}
