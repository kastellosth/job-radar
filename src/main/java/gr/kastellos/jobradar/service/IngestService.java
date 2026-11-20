package gr.kastellos.jobradar.service;

import gr.kastellos.jobradar.domain.BoardHealth;
import gr.kastellos.jobradar.domain.BoardHealthRepository;
import gr.kastellos.jobradar.domain.Job;
import gr.kastellos.jobradar.domain.JobPosting;
import gr.kastellos.jobradar.domain.JobRepository;
import gr.kastellos.jobradar.domain.MatchResult;
import gr.kastellos.jobradar.matching.JobScorer;
import gr.kastellos.jobradar.source.BoardRead;
import gr.kastellos.jobradar.source.JobSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Fetch from every source -> drop duplicates -> score -> insert new / refresh known.
 * A job he already marked APPLIED or HIDDEN keeps that status when it shows up again.
 *
 * The job boards are called before the database transaction starts, so slow boards never hold a connection,
 * and only one run at a time is allowed: two runs inserting the same new job would make one of them fail.
 */
@Service
public class IngestService {

    private static final Logger log = LoggerFactory.getLogger(IngestService.class);

    private final List<JobSource> sources;
    private final JobScorer scorer;
    private final JobRepository jobs;
    private final BoardHealthRepository health;
    private final Clock clock;
    private final TransactionOperations tx;
    private final AtomicBoolean running = new AtomicBoolean();

    public IngestService(List<JobSource> sources, JobScorer scorer, JobRepository jobs, BoardHealthRepository health,
                         Clock clock, TransactionOperations tx) {
        this.sources = sources;
        this.scorer = scorer;
        this.jobs = jobs;
        this.health = health;
        this.clock = clock;
        this.tx = tx;
    }

    public record IngestReport(int fetched, int unique, int inserted, int updated) {
    }

    /** Thrown when a run is asked for while another one is still going. */
    public static class AlreadyRunningException extends RuntimeException {
        public AlreadyRunningException() {
            super("A fetch is already running. Its new jobs will show up when it finishes.");
        }
    }

    @Scheduled(cron = "${radar.schedule:0 0 8,18 * * *}", zone = "Europe/Athens")
    public void scheduledRun() {
        try {
            IngestReport report = run();
            log.info("Ingest finished: {}", report);
        } catch (AlreadyRunningException e) {
            log.info("Scheduled ingest skipped: a fetch started from the dashboard is still running");
        }
    }

    /** @throws AlreadyRunningException another run has not finished yet */
    public IngestReport run() {
        if (!running.compareAndSet(false, true)) {
            throw new AlreadyRunningException();
        }
        try {
            Instant now = clock.instant();
            List<BoardRead> reads = fetchAll();
            return tx.execute(status -> save(reads, now));
        } finally {
            running.set(false);
        }
    }

    /** Every board of every source, failed reads included. A source that throws instead is only logged. */
    private List<BoardRead> fetchAll() {
        List<BoardRead> reads = new ArrayList<>();
        for (JobSource source : sources) {
            try {
                List<BoardRead> sourceReads = source.fetch();
                reads.addAll(sourceReads);
                log.info("Source {} returned {} postings from {} boards ({} failed)", source.name(),
                        sourceReads.stream().mapToInt(r -> r.postings().size()).sum(), sourceReads.size(),
                        sourceReads.stream().filter(BoardRead::failed).count());
            } catch (RuntimeException e) {
                log.warn("Source {} failed, skipping: {}", source.name(), e.getMessage());
            }
        }
        return reads;
    }

    private IngestReport save(List<BoardRead> reads, Instant now) {
        int fetched = 0;
        // Same job on two boards (or twice on one) collapses to one entry; the first board wins.
        Map<String, JobPosting> unique = new LinkedHashMap<>();
        Map<String, String> boardOf = new LinkedHashMap<>();
        for (BoardRead read : reads) {
            BoardHealth h = health.findById(read.board()).orElseGet(() -> BoardHealth.of(read.board()));
            if (read.failed()) {
                h.failed(read.error(), now);
            } else {
                h.read(read.postings().size(), now);
            }
            health.save(h);

            fetched += read.postings().size();
            for (JobPosting p : read.postings()) {
                if (p.title() != null && p.url() != null && unique.putIfAbsent(p.fingerprint(), p) == null) {
                    boardOf.put(p.fingerprint(), read.board());
                }
            }
        }

        int inserted = 0;
        int updated = 0;
        for (JobPosting posting : unique.values()) {
            MatchResult match = scorer.score(posting);
            var existing = jobs.findByFingerprint(posting.fingerprint());
            Job job;
            if (existing.isPresent()) {
                job = existing.get();
                job.refresh(posting, match, now);
                updated++;
            } else {
                job = jobs.save(Job.from(posting, match, now));
                inserted++;
            }
            job.listedOn(boardOf.get(posting.fingerprint()));
        }
        return new IngestReport(fetched, unique.size(), inserted, updated);
    }
}
