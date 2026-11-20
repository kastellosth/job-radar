package gr.kastellos.jobradar.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JobTest {

    private static final Instant SEEN = Instant.parse("2026-11-20T06:00:00Z");
    private static final MatchResult MATCH = new MatchResult(10, Bucket.STRONG, List.of());

    private static JobPosting posting(Instant postedAt) {
        return new JobPosting("workday", "/job/1", "Acme", "Data Engineer", "Athens", false, "https://x", "",
                postedAt);
    }

    @Test
    void aReadWithoutADateKeepsTheDateAnEarlierReadGave() {
        Instant posted = Instant.parse("2026-11-10T22:00:00Z");
        Job job = Job.from(posting(posted), MATCH, SEEN);

        // Later Workday only says "Posted 30+ Days Ago".
        job.refresh(posting(null), MATCH, SEEN.plusSeconds(86_400));

        assertThat(job.getPostedAt()).isEqualTo(posted);
    }

    @Test
    void aNewDateReplacesTheOldOne() {
        Job job = Job.from(posting(Instant.parse("2026-11-10T22:00:00Z")), MATCH, SEEN);

        job.refresh(posting(Instant.parse("2026-11-11T22:00:00Z")), MATCH, SEEN.plusSeconds(86_400));

        assertThat(job.getPostedAt()).isEqualTo(Instant.parse("2026-11-11T22:00:00Z"));
    }
}
