package gr.kastellos.jobradar.domain;

import gr.kastellos.jobradar.support.PostgresContainerConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;

import static gr.kastellos.jobradar.domain.Bucket.GOOD;
import static gr.kastellos.jobradar.domain.Bucket.SKIP;
import static gr.kastellos.jobradar.domain.Bucket.STRETCH;
import static gr.kastellos.jobradar.domain.Bucket.STRONG;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs against a real PostgreSQL with the Flyway schema, so it checks what a mock cannot:
 * the UNIQUE constraint, the CHECK constraints on bucket/status, and the SQL behind the dashboard query.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PostgresContainerConfig.class)
class JobRepositoryIT {

    private static final Instant T0 = Instant.parse("2026-11-18T08:00:00Z");

    @Autowired
    private JobRepository jobs;

    private static Job job(String title, int score, Bucket bucket, Instant firstSeen) {
        JobPosting posting = new JobPosting("test", null, "Acme", title, "Athens", false,
                "https://example.com/" + title, "", null);
        return Job.from(posting, new MatchResult(score, bucket, List.of("+" + score + " test")), firstSeen);
    }

    @Test
    void findsAJobByItsFingerprint() {
        Job saved = jobs.saveAndFlush(job("Junior Java Developer", 10, STRONG, T0));

        assertThat(jobs.findByFingerprint("acme|junior java developer|athens"))
                .get().extracting(Job::getId).isEqualTo(saved.getId());
    }

    @Test
    void databaseRejectsASecondJobWithTheSameFingerprint() {
        jobs.saveAndFlush(job("Junior Java Developer", 10, STRONG, T0));

        assertThatThrownBy(() -> jobs.saveAndFlush(job("Junior Java Developer", 3, STRETCH, T0)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void dashboardQueryHidesSkippedAndHiddenJobsAndSortsBestThenNewestFirst() {
        Job hidden = job("Hidden", 15, STRONG, T0);
        hidden.setStatus(JobStatus.HIDDEN);
        jobs.saveAllAndFlush(List.of(
                job("Strong older", 12, STRONG, T0),
                job("Strong newer", 12, STRONG, T0.plusSeconds(60)),
                job("Good", 6, GOOD, T0),
                job("Skipped", 0, SKIP, T0),
                hidden));

        List<Job> result = jobs.findByBucketInAndStatusInOrderByScoreDescFirstSeenAtDesc(
                EnumSet.of(STRONG, GOOD, STRETCH),
                EnumSet.of(JobStatus.NEW, JobStatus.SEEN, JobStatus.APPLIED));

        assertThat(result).extracting(Job::getTitle).containsExactly("Strong newer", "Strong older", "Good");
    }
}
