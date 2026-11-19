package gr.kastellos.jobradar.service;

import gr.kastellos.jobradar.domain.Bucket;
import gr.kastellos.jobradar.domain.Job;
import gr.kastellos.jobradar.domain.JobPosting;
import gr.kastellos.jobradar.domain.JobRepository;
import gr.kastellos.jobradar.domain.JobStatus;
import gr.kastellos.jobradar.support.MutableClock;
import gr.kastellos.jobradar.support.PostgresContainerConfig;
import gr.kastellos.jobradar.support.StubSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The whole ingest inside the real Spring context: real scorer with the application.yml profile,
 * real transaction, real PostgreSQL. Only the job boards are replaced by stubs.
 * The unit test cannot catch a refresh that is never written back; this one reads the row again from the database.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "radar.schedule=-",
                // The real sources stay in the context with nothing to fetch, so no test calls the internet.
                "radar.sources.workable=",
                "radar.sources.smartrecruiters=",
                "radar.sources.greenhouse=",
                "radar.sources.lever=",
                "radar.sources.remotive=false"
        })
@Import({PostgresContainerConfig.class, IngestServiceIT.Stubs.class})
class IngestServiceIT {

    private static final Instant DAY_1 = Instant.parse("2026-11-18T06:00:00Z");
    private static final Instant DAY_2 = Instant.parse("2026-11-19T06:00:00Z");

    @TestConfiguration(proxyBeanMethods = false)
    static class Stubs {

        @Bean
        StubSource boardA() {
            return new StubSource("board-a");
        }

        @Bean
        StubSource boardB() {
            return new StubSource("board-b");
        }

        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(DAY_1);
        }
    }

    @Autowired
    private IngestService ingest;
    @Autowired
    private JobRepository jobs;
    @Autowired
    private StubSource boardA;
    @Autowired
    private StubSource boardB;
    @Autowired
    private MutableClock clock;

    private static JobPosting junior(String source, String company, String description) {
        return new JobPosting(source, "1", company, "Junior Java Developer", "Athens, Greece", false,
                "https://" + source + ".example/1", description, DAY_1);
    }

    private static final JobPosting SENIOR_IN_BERLIN = new JobPosting("board-a", "2", "Acme S.A.",
            "Senior Java Engineer", "Berlin, Germany", false, "https://board-a.example/2", "Java, 7+ years", DAY_1);

    @BeforeEach
    void cleanStart() {
        jobs.deleteAll();
        boardA.willReturn();
        boardB.willReturn();
        clock.set(DAY_1);
    }

    @Test
    void firstRunScoresWithTheRealProfileAndStoresTheSameJobOnce() {
        boardA.willReturn(junior("board-a", "Acme S.A.", "Java and Spring Boot. 0-2 years."), SENIOR_IN_BERLIN);
        boardB.willReturn(junior("board-b", "ACME S.A", "Java and Spring Boot. 0-2 years."));

        var report = ingest.run();

        assertThat(report).isEqualTo(new IngestService.IngestReport(3, 2, 2, 0));
        assertThat(jobs.findAll()).hasSize(2);
        assertThat(jobs.findByFingerprint("acme s a|junior java developer|athens greece")).get()
                .satisfies(job -> {
                    assertThat(job.getBucket()).isEqualTo(Bucket.STRONG);
                    assertThat(job.getSource()).isEqualTo("board-a");
                    assertThat(job.getReasons()).contains("+3 java", "+4 title:junior");
                    assertThat(job.getStatus()).isEqualTo(JobStatus.NEW);
                });
        assertThat(jobs.findByFingerprint(SENIOR_IN_BERLIN.fingerprint())).get()
                .extracting(Job::getBucket).isEqualTo(Bucket.SKIP);
    }

    @Test
    void secondRunKeepsHisStatusAndWritesTheNewScoreToTheDatabase() {
        JobPosting posting = junior("board-a", "Acme S.A.", "Java and Spring Boot. 0-2 years.");
        boardA.willReturn(posting);
        ingest.run();
        Job stored = jobs.findByFingerprint(posting.fingerprint()).orElseThrow();
        stored.setStatus(JobStatus.APPLIED);
        jobs.save(stored);

        clock.set(DAY_2);
        boardA.willReturn(junior("board-a", "Acme S.A.", "Java and Spring Boot. Senior team, 6+ years."));
        var report = ingest.run();

        assertThat(report).isEqualTo(new IngestService.IngestReport(1, 1, 0, 1));
        Job reloaded = jobs.findByFingerprint(posting.fingerprint()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(JobStatus.APPLIED);
        assertThat(reloaded.getBucket()).isEqualTo(Bucket.SKIP);
        assertThat(reloaded.getReasons()).contains("-8 senior", "-8 asks for 6+ years");
        assertThat(reloaded.getFirstSeenAt()).isEqualTo(DAY_1);
        assertThat(reloaded.getLastSeenAt()).isEqualTo(DAY_2);
    }

    @Test
    void aBoardThatIsDownDoesNotStopTheOthersFromBeingSaved() {
        boardA.willFail(new IllegalStateException("HTTP 503"));
        boardB.willReturn(junior("board-b", "Acme S.A.", "Java"));

        var report = ingest.run();

        assertThat(report.inserted()).isEqualTo(1);
        assertThat(jobs.count()).isEqualTo(1);
    }
}
