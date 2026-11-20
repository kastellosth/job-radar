package gr.kastellos.jobradar.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class BoardHealthTest {

    private static final Instant T1 = Instant.parse("2026-11-18T06:00:00Z");
    private static final Instant T2 = Instant.parse("2026-11-18T16:00:00Z");
    private static final Instant T3 = Instant.parse("2026-11-19T06:00:00Z");

    @Test
    void aGoodReadRecordsWhenAndHowManyJobs() {
        BoardHealth h = BoardHealth.of("workable:acme");

        h.read(12, T1);

        assertThat(h.getLastOkAt()).isEqualTo(T1);
        assertThat(h.getJobCount()).isEqualTo(12);
        assertThat(h.isFailing()).isFalse();
    }

    @Test
    void failingKeepsTheLastGoodReadAndTheTimeItStartedFailing() {
        BoardHealth h = BoardHealth.of("workable:acme");
        h.read(12, T1);

        h.failed("503 Service Unavailable", T2);
        h.failed("timeout", T3);

        assertThat(h.getLastOkAt()).isEqualTo(T1);
        assertThat(h.getJobCount()).isEqualTo(12);
        assertThat(h.getFailingSince()).isEqualTo(T2);
        assertThat(h.getLastError()).isEqualTo("timeout");
        assertThat(h.getLastAttemptAt()).isEqualTo(T3);
    }

    @Test
    void aGoodReadAfterFailuresClearsThem() {
        BoardHealth h = BoardHealth.of("workable:acme");
        h.read(12, T1);
        h.failed("503", T2);

        h.read(11, T3);

        assertThat(h.isFailing()).isFalse();
        assertThat(h.getLastError()).isNull();
        assertThat(h.getLastOkAt()).isEqualTo(T3);
    }

    @Test
    void noJobsAfterSomeIsAFailureNotAnEmptyBoard() {
        BoardHealth h = BoardHealth.of("smartrecruiters:AcmeCo");
        h.read(30, T1);

        h.read(0, T2);

        assertThat(h.isFailing()).isTrue();
        assertThat(h.getLastOkAt()).isEqualTo(T1);
        assertThat(h.getJobCount()).isEqualTo(30);
        assertThat(h.getLastError()).isEqualTo("listed no jobs, after 30 last time");
    }

    @Test
    void aBoardThatNeverHadJobsCanStayEmpty() {
        BoardHealth h = BoardHealth.of("workable:quiet");

        h.read(0, T1);
        h.read(0, T2);

        assertThat(h.isFailing()).isFalse();
        assertThat(h.getLastOkAt()).isEqualTo(T2);
    }

    @Test
    void aLongErrorIsCut() {
        BoardHealth h = BoardHealth.of("workable:acme");

        h.failed("x".repeat(5000), T1);

        assertThat(h.getLastError()).hasSize(BoardHealth.MAX_ERROR + 1).endsWith("…");
    }
}
