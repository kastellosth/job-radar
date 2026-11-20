package gr.kastellos.jobradar.source;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gr.kastellos.jobradar.domain.JobPosting;
import gr.kastellos.jobradar.domain.Pay;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    void smartRecruitersAdTextIsTheAdsOwnSectionsWithoutTheCompanyBlurb() throws IOException {
        String text = SmartRecruitersSource.adText(fixture("smartrecruiters-detail.json"));

        assertThat(text).contains("Build REST services in Java and Spring Boot.", "0-2 years of experience.",
                "Hybrid, Thessaloniki office.");
        assertThat(text).doesNotContain("<p>", "We are a leading company");
    }

    private static final Clock NOV_20_MORNING =
            Clock.fixed(Instant.parse("2026-11-20T06:00:00Z"), ZoneOffset.UTC);

    @Test
    void workdayListGivesTitleLinkPlaceAndRoughDate() throws IOException {
        WorkdaySource.Search search = WorkdaySource.Search.of("acme.wd3/AcmeCareers?locationCountry=x");
        List<JobPosting> jobs = WorkdaySource.parse(search, fixture("workday-page1.json"), NOV_20_MORNING);

        assertThat(jobs).hasSize(2);
        JobPosting first = jobs.get(0);
        assertThat(first.title()).isEqualTo("Junior Software Engineer");
        assertThat(first.company()).isNull();
        assertThat(first.location()).isEqualTo("Athens");
        assertThat(first.url()).isEqualTo("https://acme.wd3.myworkdayjobs.com/AcmeCareers/job/Athens/Junior-Software-Engineer_R001");
        // "2 Days Ago" seen on the morning of 20 Nov in Athens: 18 Nov, Athens midnight.
        assertThat(first.postedAt()).isEqualTo(Instant.parse("2026-11-17T22:00:00Z"));
        assertThat(jobs.get(1).postedAt()).as("30+ days ago is too vague to show as a date").isNull();
    }

    @Test
    void workdayLeavesThePlaceEmptyWhenTheListOnlySaysHowManyPlaces() throws IOException {
        WorkdaySource.Search search = WorkdaySource.Search.of("acme.wd3/AcmeCareers");
        JobPosting job = WorkdaySource.parse(search, fixture("workday-page2.json"), NOV_20_MORNING).get(0);

        assertThat(job.location()).isNull();
        assertThat(job.postedAt()).isEqualTo(Instant.parse("2026-11-19T22:00:00Z"));
    }

    @Test
    void workdayReadsItsSearchBackFromTheSlug() {
        WorkdaySource.Search s = WorkdaySource.Search.of(
                "accenture.wd103/AccentureCareers?locationCountry=abc&locations=d1&locations=d2&q=java+developer");

        assertThat(s.api()).isEqualTo("https://accenture.wd103.myworkdayjobs.com/wday/cxs/accenture/AccentureCareers");
        assertThat(s.facets()).containsEntry("locationCountry", List.of("abc"))
                .containsEntry("locations", List.of("d1", "d2"));
        assertThat(s.text()).isEqualTo("java developer");
    }

    private String page(String name) throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/fixtures/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static final SuccessFactorsSource.Search EY_SEARCH =
            SuccessFactorsSource.Search.of("careers.ey.com/ey?locationsearch=Greece");

    @Test
    void successFactorsTableGivesTitleLinkPlaceDateAndTheTotal() throws IOException {
        SuccessFactorsSource.Page page = SuccessFactorsSource.parse(EY_SEARCH, page("successfactors-table-1.html"));

        assertThat(page.total()).isEqualTo(3);
        assertThat(page.postings()).hasSize(2);
        JobPosting first = page.postings().get(0);
        assertThat(first.externalId()).isEqualTo("1431959333");
        assertThat(first.title()).isEqualTo("Junior Java Developer");
        assertThat(first.company()).isNull();
        assertThat(first.location()).isEqualTo("Athens, Greece, 15125");
        assertThat(first.url()).isEqualTo("https://careers.ey.com/ey/job/Athens-Junior-Java-Developer-15125/1431959333/");
        assertThat(first.postedAt()).isEqualTo(Instant.parse("2026-11-04T22:00:00Z"));
        assertThat(page.postings().get(1).location()).isEqualTo("Greece");
        assertThat(page.postings().get(1).postedAt()).isNull();
    }

    @Test
    void successFactorsCardsGiveTheValueWithoutItsLabelAndTheTotalFromTheScript() throws IOException {
        SuccessFactorsSource.Page page = SuccessFactorsSource.parse(
                SuccessFactorsSource.Search.of("career.kotsovolos.gr?locale=el_GR"), page("successfactors-tiles.html"));

        assertThat(page.total()).isEqualTo(1);
        JobPosting job = page.postings().get(0);
        assertThat(job.title()).isEqualTo("Software Engineer");
        assertThat(job.location()).isEqualTo("ΝΕΟ ΗΡΑΚΛΕΙΟ");
        assertThat(job.postedAt()).as("a Greek date is left to the job page").isNull();
        assertThat(job.url()).isEqualTo("https://career.kotsovolos.gr/job/Software-Engineer-14121/1373941055/");
    }

    @Test
    void successFactorsJobPageGivesDescriptionAndPostingDate() throws IOException {
        JobPosting listed = SuccessFactorsSource.parse(EY_SEARCH, page("successfactors-table-1.html")).postings().get(0);

        JobPosting full = SuccessFactorsSource.withAd(listed, page("successfactors-job.html"));

        assertThat(full.description()).contains("Build REST services in Java and Spring Boot.", "0-2 years")
                .doesNotContain("<p>");
        assertThat(full.postedAt()).isEqualTo(Instant.parse("2026-10-28T02:00:00Z"));
        assertThat(full.location()).isEqualTo(listed.location());
    }

    @Test
    void aPageThatIsNotASuccessFactorsSearchIsRefused() {
        assertThatThrownBy(() -> SuccessFactorsSource.parse(EY_SEARCH, "<html><body><h1>Welcome</h1></body></html>"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not a SuccessFactors job search");
    }

    @Test
    void successFactorsSearchIsReadBackFromItsSlug() {
        assertThat(SuccessFactorsSource.Search.of("careers.ey.com/ey?locationsearch=Greece").page(25)).hasToString(
                "https://careers.ey.com/ey/search/?locationsearch=Greece&startrow=25");
        assertThat(SuccessFactorsSource.Search.of("career.kotsovolos.gr").page(0)).hasToString(
                "https://career.kotsovolos.gr/search/?startrow=0");
    }

    @Test
    void leverAndRemotiveGiveTheirOwnPayFields() throws IOException {
        assertThat(LeverSource.parse("acme", fixture("lever.json")).get(0).pay())
                .isEqualTo(new Pay(30000, 40000, "EUR",
                        Pay.Period.YEAR, ""));
        var remotive = RemotiveSource.parse(fixture("remotive.json"));
        assertThat(remotive.get(0).pay().min()).isEqualTo(35300);
        assertThat(remotive.get(0).pay().max()).isEqualTo(52000);
        assertThat(remotive.get(1).pay()).as("no salary field: read from the ad at ingest").isNull();
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
    void greenhouseDateIsWhenTheJobWasFirstPublishedNotLastEdited() throws IOException {
        assertThat(GreenhouseSource.parse("acme", fixture("greenhouse.json")).get(0).postedAt())
                .isEqualTo(Instant.parse("2026-10-22T08:43:36Z"));

        JsonNode noPublishDate = mapper.readTree(
                "{\"jobs\":[{\"id\":1,\"title\":\"Developer\",\"updated_at\":\"2026-11-11T10:00:00-05:00\"}]}");
        assertThat(GreenhouseSource.parse("acme", noPublishDate).get(0).postedAt())
                .isEqualTo(Instant.parse("2026-11-11T15:00:00Z"));
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
