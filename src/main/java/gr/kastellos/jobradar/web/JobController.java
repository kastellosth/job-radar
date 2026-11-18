package gr.kastellos.jobradar.web;

import gr.kastellos.jobradar.domain.Bucket;
import gr.kastellos.jobradar.domain.Job;
import gr.kastellos.jobradar.domain.JobRepository;
import gr.kastellos.jobradar.domain.JobStatus;
import gr.kastellos.jobradar.service.IngestService;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api")
public class JobController {

    private final JobRepository jobs;
    private final IngestService ingest;

    public JobController(JobRepository jobs, IngestService ingest) {
        this.jobs = jobs;
        this.ingest = ingest;
    }

    /**
     * Jobs grouped by bucket, best first. By default hides SKIP jobs and anything he hid or was rejected from.
     * Example: GET /api/jobs?status=NEW,SEEN
     */
    @GetMapping("/jobs")
    public Map<Bucket, List<Job>> grouped(@RequestParam(required = false) Set<JobStatus> status) {
        Set<JobStatus> statuses = status == null || status.isEmpty()
                ? EnumSet.of(JobStatus.NEW, JobStatus.SEEN, JobStatus.APPLIED)
                : status;
        Set<Bucket> visible = EnumSet.of(Bucket.STRONG, Bucket.GOOD, Bucket.STRETCH);

        Map<Bucket, List<Job>> grouped = new LinkedHashMap<>();
        for (Bucket b : visible) {
            grouped.put(b, new java.util.ArrayList<>());
        }
        for (Job job : jobs.findByBucketInAndStatusInOrderByScoreDescFirstSeenAtDesc(visible, statuses)) {
            grouped.get(job.getBucket()).add(job);
        }
        return grouped;
    }

    public record StatusUpdate(@NotNull JobStatus status) {
    }

    @PatchMapping("/jobs/{id}/status")
    @Transactional
    public Job updateStatus(@PathVariable long id, @RequestBody @jakarta.validation.Valid StatusUpdate update) {
        Job job = jobs.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "job " + id + " not found"));
        job.setStatus(update.status());
        return job;
    }

    /** Run an ingest now instead of waiting for the schedule. */
    @PostMapping("/ingest")
    public IngestService.IngestReport ingestNow() {
        return ingest.run();
    }
}
