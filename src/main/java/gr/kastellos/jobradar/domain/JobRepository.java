package gr.kastellos.jobradar.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface JobRepository extends JpaRepository<Job, Long> {

    Optional<Job> findByFingerprint(String fingerprint);

    List<Job> findByBucketInAndStatusInOrderByScoreDescFirstSeenAtDesc(
            Collection<Bucket> buckets, Collection<JobStatus> statuses);
}
