package gr.kastellos.jobradar.service;

import gr.kastellos.jobradar.domain.Bucket;
import gr.kastellos.jobradar.domain.Job;
import gr.kastellos.jobradar.domain.JobPosting;
import gr.kastellos.jobradar.domain.JobRepository;
import gr.kastellos.jobradar.domain.JobStatus;
import gr.kastellos.jobradar.domain.MatchResult;
import gr.kastellos.jobradar.matching.JobScorer;
import gr.kastellos.jobradar.source.JobSource;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
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

    private static JobPosting posting(String source, String title) {
        return new JobPosting(source, "id", "Acme", title, "Athens", false, "https://x/" + title, "", null);
    }

    private static JobSource source(String name, List<JobPosting> postings) {
        return new JobSource() {
            public String name() { return name; }
            public List<JobPosting> fetch() { return postings; }
        };
    }

    private static JobSource failing() {
        return new JobSource() {
            public String name() { return "broken"; }
            public List<JobPosting> fetch() { throw new IllegalStateException("HTTP 503"); }
        };
    }

    @Test
    void sameJobFromTwoSourcesIsStoredOnce() {
        when(scorer.score(any())).thenReturn(new MatchResult(10, Bucket.STRONG, List.of()));
        when(repo.findByFingerprint(any())).thenReturn(Optional.empty());
        var service = new IngestService(List.of(
                source("workable", List.of(posting("workable", "Junior Java Developer"))),
                source("lever", List.of(posting("lever", "Junior Java Developer"), posting("lever", "QA Engineer")))),
                scorer, repo, clock);

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
        var service = new IngestService(List.of(source("workable", List.of(p))), scorer, repo, clock);

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
                scorer, repo, clock);

        var report = service.run();

        assertThat(report.inserted()).isEqualTo(1);
    }
}
