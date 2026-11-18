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
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fetch from every source -> drop duplicates -> score -> insert new / refresh known.
 * A job he already marked APPLIED or HIDDEN keeps that status when it shows up again.
 */
@Service
public class IngestService {

    private static final Logger log = LoggerFactory.getLogger(IngestService.class);

    private final List<JobSource> sources;
    private final JobScorer scorer;
    private final JobRepository jobs;
    private final Clock clock;

    public IngestService(List<JobSource> sources, JobScorer scorer, JobRepository jobs, Clock clock) {
        this.sources = sources;
        this.scorer = scorer;
        this.jobs = jobs;
        this.clock = clock;
    }

    public record IngestReport(int fetched, int unique, int inserted, int updated) {
    }

    @Scheduled(cron = "${radar.schedule:0 0 8,18 * * *}", zone = "Europe/Athens")
    public void scheduledRun() {
        IngestReport report = run();
        log.info("Ingest finished: {}", report);
    }

    @Transactional
    public IngestReport run() {
        Instant now = clock.instant();
        int fetched = 0;
        // Same job from two sources (or twice from one) collapses to one entry; first one wins.
        Map<String, JobPosting> unique = new LinkedHashMap<>();
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
