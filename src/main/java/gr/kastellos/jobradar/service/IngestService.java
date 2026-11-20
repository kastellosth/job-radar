package gr.kastellos.jobradar.service;

import gr.kastellos.jobradar.domain.Job;
import gr.kastellos.jobradar.domain.JobPosting;
import gr.kastellos.jobradar.domain.JobRepository;
import gr.kastellos.jobradar.domain.MatchResult;
import gr.kastellos.jobradar.matching.JobScorer;
import gr.kastellos.jobradar.source.JobSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Clock;
import java.time.Instant;
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
    private final Clock clock;
    private final TransactionOperations tx;
    private final AtomicBoolean running = new AtomicBoolean();

    public IngestService(List<JobSource> sources, JobScorer scorer, JobRepository jobs, Clock clock,
                         TransactionOperations tx) {
        this.sources = sources;
        this.scorer = scorer;
        this.jobs = jobs;
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
            Map<String, JobPosting> unique = new LinkedHashMap<>();
            int fetched = fetchAll(unique);
            return tx.execute(status -> save(unique, fetched, now));
        } finally {
            running.set(false);
        }
    }

    /** Fills {@code unique} with every source's postings, one per job, and returns how many came back in total. */
    private int fetchAll(Map<String, JobPosting> unique) {
        int fetched = 0;
        // Same job from two sources (or twice from one) collapses to one entry; first one wins.
        for (JobSource source : sources) {
            try {
                List<JobPosting> postings = source.fetch();
                fetched += postings.size();
                for (JobPosting p : postings) {
                    if (p.title() != null && p.url() != null) {
                        unique.putIfAbsent(p.fingerprint(), p);
                    }
                }
                log.info("Source {} returned {} postings", source.name(), postings.size());
            } catch (RuntimeException e) {
                log.warn("Source {} failed, skipping: {}", source.name(), e.getMessage());
            }
        }
        return fetched;
    }

    private IngestReport save(Map<String, JobPosting> unique, int fetched, Instant now) {
        int inserted = 0;
        int updated = 0;
        for (JobPosting posting : unique.values()) {
            MatchResult match = scorer.score(posting);
            var existing = jobs.findByFingerprint(posting.fingerprint());
            if (existing.isPresent()) {
                existing.get().refresh(posting, match, now);
                updated++;
            } else {
                jobs.save(Job.from(posting, match, now));
                inserted++;
            }
        }
        return new IngestReport(fetched, unique.size(), inserted, updated);
    }
}
