package gr.kastellos.jobradar.source;

import gr.kastellos.jobradar.config.RadarProperties;
import gr.kastellos.jobradar.domain.JobPosting;
import gr.kastellos.jobradar.domain.Provider;
import gr.kastellos.jobradar.domain.TrackedCompany;
import gr.kastellos.jobradar.domain.TrackedCompanyRepository;
import gr.kastellos.jobradar.matching.JobScorer;
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
import java.util.Map;

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

    /** Only what decides whether a SmartRecruiters ad is worth reading: Greek locations and software titles. */
    private final JobScorer scorer = new JobScorer(new RadarProperties(new RadarProperties.Sources(false),
            new RadarProperties.Profile(Map.of("java", 3), null, null, null,
                    List.of("thessaloniki", "athens"), List.of("engineer", "developer"), 9, 5, 2)));

    private SmartRecruitersSource smartRecruiters() {
        return new SmartRecruitersSource(builder, companies, scorer);
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
    void jobsFromABoardWithoutCompanyNamesGetTheTrackedCompanysName() throws IOException {
        when(companies.findByProvider(Provider.LEVER)).thenReturn(
                List.of(TrackedCompany.of(Provider.LEVER, "acme", "Acme Robotics", Instant.EPOCH)));
        server.expect(requestTo("https://api.lever.co/v0/postings/acme?mode=json"))
                .andRespond(withSuccess(fixture("lever.json"), MediaType.APPLICATION_JSON));

        List<JobPosting> jobs = new LeverSource(builder, companies).fetch();

        assertThat(jobs).extracting(JobPosting::company).containsOnly("Acme Robotics");
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

        List<JobPosting> jobs = smartRecruiters().fetchCompany("NoSuchCo");

        assertThat(jobs).isEmpty();
        assertThat(output).contains("SmartRecruiters company 'NoSuchCo' has no jobs");
    }

    @Test
    void smartRecruitersDoesNotWarnWhenThereAreJobs(CapturedOutput output) throws IOException {
        server.expect(requestTo("https://api.smartrecruiters.com/v1/companies/AcmeCo/postings?limit=100"))
                .andRespond(withSuccess(fixture("smartrecruiters.json"), MediaType.APPLICATION_JSON));

        List<JobPosting> jobs = smartRecruiters().fetchCompany("AcmeCo");

        assertThat(jobs).hasSize(1);
        assertThat(output).doesNotContain("has no jobs");
    }

    @Test
    void smartRecruitersReadsTheFullAdOnlyForSoftwareJobsItCouldScore() throws IOException {
        tracking(Provider.SMARTRECRUITERS, "AcmeCo");
        server.expect(requestTo("https://api.smartrecruiters.com/v1/companies/AcmeCo/postings?limit=100"))
                .andRespond(withSuccess(fixture("smartrecruiters-mixed.json"), MediaType.APPLICATION_JSON));
        // Only the engineer: the accountant is not a software title, the Berlin job is not in an accepted city.
        server.expect(once(), requestTo("https://api.smartrecruiters.com/v1/companies/AcmeCo/postings/111"))
                .andRespond(withSuccess(fixture("smartrecruiters-detail.json"), MediaType.APPLICATION_JSON));

        List<JobPosting> jobs = smartRecruiters().fetch();

        server.verify();
        assertThat(jobs).hasSize(3);
        assertThat(jobs.get(0).description()).startsWith("Software Development Entry Level").contains("Java and Spring Boot");
        assertThat(jobs.get(1).description()).doesNotContain("Spring Boot");
    }

    @Test
    void smartRecruitersKeepsTheShortDescriptionWhenTheFullAdFails(CapturedOutput output) throws IOException {
        tracking(Provider.SMARTRECRUITERS, "AcmeCo");
        server.expect(requestTo("https://api.smartrecruiters.com/v1/companies/AcmeCo/postings?limit=100"))
                .andRespond(withSuccess(fixture("smartrecruiters-mixed.json"), MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.smartrecruiters.com/v1/companies/AcmeCo/postings/111"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        List<JobPosting> jobs = smartRecruiters().fetch();

        assertThat(jobs).hasSize(3);
        assertThat(jobs.get(0).description()).isEqualTo("Software Development Entry Level Information Technology");
        assertThat(output).contains("SmartRecruiters company 'AcmeCo': 1 of 1 full ads could not be read");
    }

    @Test
    void addingASmartRecruitersCompanyReadsOnlyTheList() throws IOException {
        server.expect(once(), requestTo("https://api.smartrecruiters.com/v1/companies/AcmeCo/postings?limit=100"))
                .andRespond(withSuccess(fixture("smartrecruiters-mixed.json"), MediaType.APPLICATION_JSON));

        assertThat(smartRecruiters().fetchCompany("AcmeCo")).hasSize(3);
        server.verify();
    }
}
