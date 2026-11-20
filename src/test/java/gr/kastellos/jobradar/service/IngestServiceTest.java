package gr.kastellos.jobradar.service;

import gr.kastellos.jobradar.domain.BoardHealth;
import gr.kastellos.jobradar.domain.BoardHealthRepository;
import gr.kastellos.jobradar.domain.Bucket;
import gr.kastellos.jobradar.domain.Job;
import gr.kastellos.jobradar.domain.JobPosting;
import gr.kastellos.jobradar.domain.JobRepository;
import gr.kastellos.jobradar.domain.JobStatus;
import gr.kastellos.jobradar.domain.MatchResult;
import gr.kastellos.jobradar.matching.JobScorer;
import gr.kastellos.jobradar.source.BoardRead;
import gr.kastellos.jobradar.source.JobSource;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IngestServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-18T10:00:00Z");
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final JobRepository repo = mock(JobRepository.class);
    private final JobScorer scorer = mock(JobScorer.class);
    private final BoardHealthRepository health = mock(BoardHealthRepository.class);

    IngestServiceTest() {
        when(health.findById(any())).thenReturn(Optional.empty());
        when(repo.save(any(Job.class))).thenAnswer(call -> call.getArgument(0));
    }

    private static JobPosting posting(String source, String title) {
        return new JobPosting(source, "id", "Acme", title, "Athens", false, "https://x/" + title, "", null);
    }

    private static JobSource source(String name, List<JobPosting> postings) {
        return new JobSource() {
            public String name() { return name; }
            public List<BoardRead> fetch() { return List.of(BoardRead.ok(name, postings)); }
        };
    }

    private static JobSource failing() {
        return new JobSource() {
            public String name() { return "broken"; }
            public List<BoardRead> fetch() { throw new IllegalStateException("HTTP 503"); }
        };
    }

    @Test
    void sameJobFromTwoSourcesIsStoredOnce() {
        when(scorer.score(any())).thenReturn(new MatchResult(10, Bucket.STRONG, List.of()));
        when(repo.findByFingerprint(any())).thenReturn(Optional.empty());
        var service = new IngestService(List.of(
                source("workable", List.of(posting("workable", "Junior Java Developer"))),
                source("lever", List.of(posting("lever", "Junior Java Developer"), posting("lever", "QA Engineer")))),
                scorer, repo, health, clock, TransactionOperations.withoutTransaction());

        var report = service.run();

        assertThat(report).isEqualTo(new IngestService.IngestReport(3, 2, 2, 0));
        verify(repo, times(2)).save(any(Job.class));
    }

    @Test
    void knownJobKeepsItsStatusButGetsNewScore() {
        JobPosting p = posting("workable", "Junior Java Developer");
        Job existing = Job.from(p, new MatchResult(3, Bucket.STRETCH, List.of()), NOW.minusSeconds(86_400));
        existing.setStatus(JobStatus.APPLIED);
        when(repo.findByFingerprint(p.fingerprint())).thenReturn(Optional.of(existing));
        when(scorer.score(any())).thenReturn(new MatchResult(10, Bucket.STRONG, List.of("+3 java")));
        var service = new IngestService(List.of(source("workable", List.of(p))), scorer, repo, health, clock,
                TransactionOperations.withoutTransaction());

        var report = service.run();

        assertThat(report.updated()).isEqualTo(1);
        assertThat(existing.getStatus()).isEqualTo(JobStatus.APPLIED);
        assertThat(existing.getBucket()).isEqualTo(Bucket.STRONG);
        assertThat(existing.getLastSeenAt()).isEqualTo(NOW);
        verify(repo, never()).save(any());
    }

    @Test
    void oneFailingSourceDoesNotStopTheOthers() {
        when(scorer.score(any())).thenReturn(new MatchResult(5, Bucket.GOOD, List.of()));
        when(repo.findByFingerprint(any())).thenReturn(Optional.empty());
        var service = new IngestService(List.of(
                failing(),
                source("workable", List.of(posting("workable", "Junior Developer")))),
                scorer, repo, health, clock, TransactionOperations.withoutTransaction());

        var report = service.run();

        assertThat(report.inserted()).isEqualTo(1);
    }

    @Test
    void aSecondRunIsRefusedWhileTheFirstIsStillFetching() {
        when(scorer.score(any())).thenReturn(new MatchResult(5, Bucket.GOOD, List.of()));
        when(repo.findByFingerprint(any())).thenReturn(Optional.empty());
        AtomicReference<IngestService> service = new AtomicReference<>();
        JobSource startsAnotherRun = new JobSource() {
            public String name() { return "slow"; }
            public List<BoardRead> fetch() {
                // "Fetch now" pressed while this run is still reading the boards.
                assertThatThrownBy(() -> service.get().run()).isInstanceOf(IngestService.AlreadyRunningException.class);
                return List.of(BoardRead.ok("slow", List.of(posting("slow", "Junior Developer"))));
            }
        };
        service.set(new IngestService(List.of(startsAnotherRun), scorer, repo, health, clock,
                TransactionOperations.withoutTransaction()));

        assertThat(service.get().run().inserted()).isEqualTo(1);
        // Once the first run is over, the next one is allowed again.
        assertThat(service.get().run().fetched()).isEqualTo(1);
    }

    @Test
    void recordsEachBoardsHealthAndWhichBoardEachJobCameFrom() {
        when(scorer.score(any())).thenReturn(new MatchResult(5, Bucket.GOOD, List.of()));
        when(repo.findByFingerprint(any())).thenReturn(Optional.empty());
        JobSource twoBoards = new JobSource() {
            public String name() { return "workable"; }
            public List<BoardRead> fetch() {
                return List.of(
                        BoardRead.ok("workable:acme", List.of(posting("workable", "Junior Developer"))),
                        BoardRead.failed("workable:gone", new IllegalStateException("404 Not Found")));
            }
        };
        var service = new IngestService(List.of(twoBoards), scorer, repo, health, clock,
                TransactionOperations.withoutTransaction());

        service.run();

        ArgumentCaptor<BoardHealth> saved = ArgumentCaptor.forClass(BoardHealth.class);
        verify(health, times(2)).save(saved.capture());
        assertThat(saved.getAllValues()).satisfiesExactly(
                ok -> {
                    assertThat(ok.getBoard()).isEqualTo("workable:acme");
                    assertThat(ok.getLastOkAt()).isEqualTo(NOW);
                    assertThat(ok.getJobCount()).isEqualTo(1);
                },
                failed -> {
                    assertThat(failed.getBoard()).isEqualTo("workable:gone");
                    assertThat(failed.getFailingSince()).isEqualTo(NOW);
                    assertThat(failed.getLastError()).isEqualTo("404 Not Found");
                });
        ArgumentCaptor<Job> job = ArgumentCaptor.forClass(Job.class);
        verify(repo).save(job.capture());
        assertThat(job.getValue().getBoard()).isEqualTo("workable:acme");
    }
}
