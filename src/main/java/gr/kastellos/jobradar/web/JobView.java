package gr.kastellos.jobradar.web;

import gr.kastellos.jobradar.domain.Bucket;
import gr.kastellos.jobradar.domain.Job;
import gr.kastellos.jobradar.domain.JobStatus;
import gr.kastellos.jobradar.domain.Pay;

import java.time.Instant;

/**
 * A job as the API answers it: what the dashboard shows, and nothing it does not (fingerprint, board...).
 * Kept apart from the Job entity, so a change to the database is not a change to the API by accident.
 * frontend/src/api.ts has the same shape.
 */
public record JobView(
        long id,
        String source,
        String company,
        String title,
        String location,
        boolean remote,
        String url,
        Instant postedAt,
        int score,
        Bucket bucket,
        String reasons,
        JobStatus status,
        Instant firstSeenAt,
        Instant lastSeenAt,
        Integer payMin,
        Integer payMax,
        String payCurrency,
        Pay.Period payPeriod,
        String payText) {

    static JobView of(Job j) {
        return new JobView(j.getId(), j.getSource(), j.getCompany(), j.getTitle(), j.getLocation(), j.isRemote(),
                j.getUrl(), j.getPostedAt(), j.getScore(), j.getBucket(), j.getReasons(), j.getStatus(),
                j.getFirstSeenAt(), j.getLastSeenAt(), j.getPayMin(), j.getPayMax(), j.getPayCurrency(),
                j.getPayPeriod(), j.getPayText());
    }
}
