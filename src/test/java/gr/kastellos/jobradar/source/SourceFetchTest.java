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
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
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
        when(companies.findByProvider(provider)).thenReturn(Arrays.stream(slugs)
                .map(slug -> TrackedCompany.of(provider, slug, slug, Instant.EPOCH))
                .toList());
    }

    /** Only what decides whether a SmartRecruiters ad is worth reading: Greek locations and software titles. */
    private final JobScorer scorer = new JobScorer(new RadarProperties(new RadarProperties.Sources(false),
            new RadarProperties.Profile(Map.of("java", 3), null, null, null,
                    List.of("thessaloniki", "athens"), List.of("engineer", "developer"), 9, 5, 2)));

    private static List<JobPosting> postings(List<BoardRead> reads) {
        return reads.stream().flatMap(r -> r.postings().stream()).toList();
    }

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

        List<BoardRead> reads = new WorkableSource(builder, companies).fetch();

        server.verify();
        assertThat(reads).extracting(BoardRead::board).containsExactly("workable:gone", "workable:acme");
        assertThat(reads.get(0).failed()).isTrue();
        assertThat(reads.get(0).error()).contains("404");
        assertThat(reads.get(1).failed()).isFalse();
        assertThat(reads.get(1).postings()).hasSize(2);
    }

    @Test
    void jobsFromABoardWithoutCompanyNamesGetTheTrackedCompanysName() throws IOException {
        when(companies.findByProvider(Provider.LEVER)).thenReturn(
                List.of(TrackedCompany.of(Provider.LEVER, "acme", "Acme Robotics", Instant.EPOCH)));
        server.expect(requestTo("https://api.lever.co/v0/postings/acme?mode=json"))
                .andRespond(withSuccess(fixture("lever.json"), MediaType.APPLICATION_JSON));

        List<JobPosting> jobs = postings(new LeverSource(builder, companies).fetch());

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

        List<BoardRead> reads = new RemotiveSource(builder, remotive(true)).fetch();

        server.verify();
        assertThat(reads).extracting(BoardRead::board).containsExactly("remotive");
        assertThat(postings(reads)).extracting(JobPosting::title)
                .containsExactly("Junior Java Developer", "Junior React Developer");
    }

    @Test
    void remotiveSwitchedOffMakesNoCall() {
        List<BoardRead> reads = new RemotiveSource(builder, remotive(false)).fetch();

        server.verify();
        assertThat(reads).isEmpty();
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

        List<JobPosting> jobs = postings(smartRecruiters().fetch());

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

        List<JobPosting> jobs = postings(smartRecruiters().fetch());

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

    @Test
    void remotiveDownIsAFailedReadNotAnException() {
        server.expect(requestTo("https://remotive.com/api/remote-jobs?category=software-dev"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        List<BoardRead> reads = new RemotiveSource(builder, remotive(true)).fetch();

        assertThat(reads).singleElement().satisfies(r -> {
            assertThat(r.board()).isEqualTo("remotive");
            assertThat(r.failed()).isTrue();
        });
    }

    private static final String WORKDAY_SEARCH = "https://acme.wd3.myworkdayjobs.com/wday/cxs/acme/AcmeCareers/jobs";
    private static final String WORKDAY_SLUG = "acme.wd3/AcmeCareers?locationCountry=566388c1eb974c42bd9e3da4c2f57d60";

    private WorkdaySource workday() {
        return new WorkdaySource(builder, companies, scorer,
                Clock.fixed(Instant.parse("2026-11-20T06:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void workdayRepeatsTheFilteredSearchPageByPageAndReadsFullAdsOnlyForSoftwareJobs() throws IOException {
        when(companies.findByProvider(Provider.WORKDAY)).thenReturn(
                List.of(TrackedCompany.of(Provider.WORKDAY, WORKDAY_SLUG, "Acme", Instant.EPOCH)));
        server.expect(requestTo(WORKDAY_SEARCH))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(content().json("""
                        {"appliedFacets":{"locationCountry":["566388c1eb974c42bd9e3da4c2f57d60"]},
                         "limit":20,"offset":0,"searchText":""}"""))
                .andRespond(withSuccess(fixture("workday-page1.json"), MediaType.APPLICATION_JSON));
        // 22 in total: a second page. (The saved pages are shortened; a real first page has 20 jobs.)
        server.expect(requestTo(WORKDAY_SEARCH))
                .andExpect(content().json("{\"offset\":20}"))
                .andRespond(withSuccess(fixture("workday-page2.json"), MediaType.APPLICATION_JSON));
        // The engineer in Athens and the data engineer whose place the list did not say; not the tax associate.
        server.expect(once(), requestTo("https://acme.wd3.myworkdayjobs.com/wday/cxs/acme/AcmeCareers/job/Athens/Junior-Software-Engineer_R001"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(once(), requestTo("https://acme.wd3.myworkdayjobs.com/wday/cxs/acme/AcmeCareers/job/London/Data-Engineer_R003"))
                .andRespond(withSuccess(fixture("workday-detail.json"), MediaType.APPLICATION_JSON));

        List<BoardRead> reads = workday().fetch();

        server.verify();
        assertThat(reads).singleElement().satisfies(r -> assertThat(r.board()).isEqualTo("workday:" + WORKDAY_SLUG));
        List<JobPosting> jobs = postings(reads);
        assertThat(jobs).extracting(JobPosting::title)
                .containsExactly("Junior Software Engineer", "Tax Associate", "Data Engineer");
        assertThat(jobs).extracting(JobPosting::company).containsOnly("Acme");
        assertThat(jobs.get(0).description()).as("its ad could not be read: kept as listed").isEmpty();
        JobPosting data = jobs.get(2);
        assertThat(data.location()).as("the accepted office, not London first").isEqualTo("Athens, 112 A Vasilissis Sofia");
        assertThat(data.description()).contains("Build data pipelines in Java and SQL.").doesNotContain("<p>");
        assertThat(data.postedAt()).isEqualTo(Instant.parse("2026-11-10T22:00:00Z"));
        assertThat(data.url()).isEqualTo("https://acme.wd3.myworkdayjobs.com/AcmeCareers/job/London/Data-Engineer_R003");
    }

    @Test
    void workdayRefusesASearchThatWasNotFilteredDown() {
        server.expect(requestTo(WORKDAY_SEARCH))
                .andRespond(withSuccess("{\"total\":12480,\"jobPostings\":[]}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> workday().fetchCompany("acme.wd3/AcmeCareers"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("12480 jobs")
                .hasMessageContaining("Filter it to Greece");
    }

    private static final String EY_SLUG = "careers.ey.com/ey?locationsearch=Greece";

    @Test
    void successFactorsWalksTheResultPagesAndReadsJobPagesOnlyForSoftwareJobs() throws IOException {
        when(companies.findByProvider(Provider.SUCCESSFACTORS)).thenReturn(
                List.of(TrackedCompany.of(Provider.SUCCESSFACTORS, EY_SLUG, "EY", Instant.EPOCH)));
        server.expect(requestTo("https://careers.ey.com/ey/search/?locationsearch=Greece&startrow=0"))
                .andRespond(withSuccess(fixture("successfactors-table-1.html"), MediaType.TEXT_HTML));
        server.expect(requestTo("https://careers.ey.com/ey/search/?locationsearch=Greece&startrow=2"))
                .andRespond(withSuccess(fixture("successfactors-table-2.html"), MediaType.TEXT_HTML));
        // The Java developer in Athens and the data engineer in Thessaloniki; not the tax senior.
        server.expect(once(), requestTo("https://careers.ey.com/ey/job/Athens-Junior-Java-Developer-15125/1431959333/"))
                .andRespond(withSuccess(fixture("successfactors-job.html"), MediaType.TEXT_HTML));
        server.expect(once(), requestTo("https://careers.ey.com/ey/job/Thessaloniki-Data-Engineer-54625/1431959555/"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        List<BoardRead> reads = new SuccessFactorsSource(builder, companies, scorer).fetch();

        server.verify();
        assertThat(reads).singleElement().satisfies(r -> assertThat(r.board()).isEqualTo("successfactors:" + EY_SLUG));
        List<JobPosting> jobs = postings(reads);
        assertThat(jobs).extracting(JobPosting::title).containsExactly("Junior Java Developer", "Tax Senior", "Data Engineer");
        assertThat(jobs).extracting(JobPosting::company).containsOnly("EY");
        assertThat(jobs.get(0).description()).contains("Spring Boot");
        assertThat(jobs.get(2).description()).as("its page failed: kept as listed").isEmpty();
    }

    @Test
    void successFactorsRefusesASearchThatWasNotNarrowedDown() {
        server.expect(requestTo("https://careers.ey.com/ey/search/?startrow=0")).andRespond(withSuccess(
                "<span class=\"paginationLabel\">Results <b>1 – 25</b> of <b>4210</b></span>"
                        + "<table><tr class=\"data-row\"><td><a class=\"jobTitle-link\" href=\"/ey/job/x/123456/\">X</a></td></tr></table>",
                MediaType.TEXT_HTML));

        assertThatThrownBy(() -> new SuccessFactorsSource(builder, companies, scorer).fetchCompany("careers.ey.com/ey"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("4210 jobs");
    }

    @Test
    void successFactorsStopsWhenAPageAddsNoNewJobs() throws IOException {
        // A site that ignores startrow keeps sending the first page: read it once, not forever.
        server.expect(requestTo("https://careers.ey.com/ey/search/?locationsearch=Greece&startrow=0"))
                .andRespond(withSuccess(fixture("successfactors-table-1.html"), MediaType.TEXT_HTML));
        server.expect(requestTo("https://careers.ey.com/ey/search/?locationsearch=Greece&startrow=2"))
                .andRespond(withSuccess(fixture("successfactors-table-1.html"), MediaType.TEXT_HTML));

        assertThat(new SuccessFactorsSource(builder, companies, scorer).fetchCompany(EY_SLUG)).hasSize(2);
        server.verify();
    }
}
