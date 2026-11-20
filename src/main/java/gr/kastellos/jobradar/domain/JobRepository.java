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
     * The dashboard list, best first. A job no board has listed since {@code listedSince} is taken as closed
     * and left out, unless he applied to it: those stay, so he can follow up.
     */
    @Query("""
            select j from Job j
            where j.bucket in :buckets and j.status in :statuses
              and (j.lastSeenAt >= :listedSince or j.status = gr.kastellos.jobradar.domain.JobStatus.APPLIED)
            order by j.score desc, j.firstSeenAt desc""")
    List<Job> dashboard(Collection<Bucket> buckets, Collection<JobStatus> statuses, Instant listedSince);

    /** Jobs of one bucket first seen since {@code firstSeenSince} and still listed, best first: the weekly email. */
    @Query("""
            select j from Job j
            where j.bucket = :bucket and j.status in :statuses
              and j.firstSeenAt >= :firstSeenSince and j.lastSeenAt >= :listedSince
            order by j.score desc, j.firstSeenAt desc""")
    List<Job> newMatches(Bucket bucket, Collection<JobStatus> statuses, Instant firstSeenSince, Instant listedSince);
}
