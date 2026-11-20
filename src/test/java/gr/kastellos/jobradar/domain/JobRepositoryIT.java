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
    @Autowired
    private BoardHealthRepository health;

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

        List<Job> result = jobs.dashboard(
                EnumSet.of(STRONG, GOOD, STRETCH),
                EnumSet.of(JobStatus.NEW, JobStatus.SEEN, JobStatus.APPLIED),
                T0.minusSeconds(60));

        assertThat(result).extracting(Job::getTitle).containsExactly("Strong newer", "Strong older", "Good");
    }

    @Test
    void dashboardQueryLeavesOutJobsNoBoardListsAnyMoreExceptOnesHeAppliedTo() {
        Job applied = job("Closed but applied", 8, GOOD, T0);
        applied.setStatus(JobStatus.APPLIED);
        jobs.saveAllAndFlush(List.of(
                job("Still listed", 8, GOOD, T0.plusSeconds(3 * 86_400)),
                job("Closed", 8, GOOD, T0),
                applied));

        List<Job> result = jobs.dashboard(
                EnumSet.of(STRONG, GOOD, STRETCH),
                EnumSet.of(JobStatus.NEW, JobStatus.SEEN, JobStatus.APPLIED),
                T0.plusSeconds(86_400));

        assertThat(result).extracting(Job::getTitle).containsExactlyInAnyOrder("Still listed", "Closed but applied");
    }

    @Test
    void weeklyEmailQueryFindsNewStrongJobsHeHasNotActedOnThatAreStillListed() {
        Instant weekStart = T0;
        Job applied = job("Applied", 12, STRONG, T0.plusSeconds(3600));
        applied.setStatus(JobStatus.APPLIED);
        Job seen = job("Seen", 10, STRONG, T0.plusSeconds(3600));
        seen.setStatus(JobStatus.SEEN);
        jobs.saveAllAndFlush(List.of(
                job("New strong", 12, STRONG, T0.plusSeconds(3600)),
                seen,
                job("Too old", 12, STRONG, T0.minusSeconds(3600)),
                job("Good only", 8, GOOD, T0.plusSeconds(3600)),
                applied));

        List<Job> result = jobs.newMatches(STRONG, EnumSet.of(JobStatus.NEW, JobStatus.SEEN), weekStart, T0);

        assertThat(result).extracting(Job::getTitle).containsExactly("New strong", "Seen");
    }

    private Job on(String board, Job job) {
        job.listedOn(board);
        return job;
    }

    @Test
    void aJobOfABoardThatIsFailingStaysWhileAGoodReadWithoutItClosesIt() {
        Instant later = T0.plusSeconds(5 * 86_400);
        BoardHealth down = BoardHealth.of("workable:down");
        down.read(1, T0);
        down.failed("503", later);
        BoardHealth fine = BoardHealth.of("workable:fine");
        fine.read(3, later);
        health.saveAllAndFlush(List.of(down, fine));
        jobs.saveAllAndFlush(List.of(
                on("workable:down", job("On a board that is down", 8, GOOD, T0)),
                on("workable:fine", job("Dropped by a board that works", 8, GOOD, T0)),
                on("workable:fine", job("Still listed", 8, GOOD, later)),
                job("From before boards were recorded", 8, GOOD, T0)));

        List<Job> result = jobs.dashboard(
                EnumSet.of(STRONG, GOOD, STRETCH),
                EnumSet.of(JobStatus.NEW, JobStatus.SEEN, JobStatus.APPLIED),
                later.minusSeconds(3 * 86_400));

        assertThat(result).extracting(Job::getTitle)
                .containsExactlyInAnyOrder("On a board that is down", "Still listed");
    }
}
