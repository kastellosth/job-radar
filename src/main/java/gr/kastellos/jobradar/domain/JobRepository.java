package gr.kastellos.jobradar.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface JobRepository extends JpaRepository<Job, Long> {

    Optional<Job> findByFingerprint(String fingerprint);

    /**
     * The dashboard list, best first. A job is taken as closed, and left out, when it has not been listed since
     * {@code listedSince} although its board has been read fine after that: a board that is down or broken keeps
     * its jobs (its last good read still includes them). Jobs he applied to always stay, so he can follow up.
     */
    @Query("""
            select j from Job j
            where j.bucket in :buckets and j.status in :statuses
              and (j.status = gr.kastellos.jobradar.domain.JobStatus.APPLIED
                   or j.lastSeenAt >= :listedSince
                   or j.lastSeenAt >= (select h.lastOkAt from BoardHealth h where h.board = j.board))
            order by j.score desc, j.firstSeenAt desc""")
    List<Job> dashboard(Collection<Bucket> buckets, Collection<JobStatus> statuses, Instant listedSince);

    /** Jobs of one bucket first seen since {@code firstSeenSince} and not closed, best first: the weekly email. */
    @Query("""
            select j from Job j
            where j.bucket = :bucket and j.status in :statuses
              and j.firstSeenAt >= :firstSeenSince
              and (j.lastSeenAt >= :listedSince
                   or j.lastSeenAt >= (select h.lastOkAt from BoardHealth h where h.board = j.board))
            order by j.score desc, j.firstSeenAt desc""")
    List<Job> newMatches(Bucket bucket, Collection<JobStatus> statuses, Instant firstSeenSince, Instant listedSince);
}
