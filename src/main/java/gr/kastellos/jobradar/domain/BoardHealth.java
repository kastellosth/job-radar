package gr.kastellos.jobradar.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * How the last reads of one board went. A job counts as closed only when a good read of its board left it out,
 * so a board that is down or broken keeps its jobs instead of emptying the dashboard.
 */
@Entity
@Table(name = "board_health")
public class BoardHealth {

    /** Longest error kept; job boards can answer with a whole HTML page. */
    static final int MAX_ERROR = 300;

    @Id
    private String board;

    @Column(nullable = false)
    private Instant lastAttemptAt;

    /** The last read that worked. Null until one has. */
    private Instant lastOkAt;

    /** Jobs the last good read listed. */
    private Integer jobCount;

    /** Set on the first failed read after a good one, cleared by the next good read. */
    private Instant failingSince;

    private String lastError;

    protected BoardHealth() {
    }

    public static BoardHealth of(String board) {
        BoardHealth health = new BoardHealth();
        health.board = board;
        return health;
    }

    /**
     * A read that listed {@code jobs} jobs. Zero jobs from a board that listed some last time is far more often a
     * broken page or a wrong id than a company closing every opening at once, so it counts as a failure.
     */
    public void read(int jobs, Instant at) {
        lastAttemptAt = at;
        if (jobs == 0 && jobCount != null && jobCount > 0) {
            failed("listed no jobs, after " + jobCount + " last time", at);
            return;
        }
        lastOkAt = at;
        jobCount = jobs;
        failingSince = null;
        lastError = null;
    }

    public void failed(String error, Instant at) {
        lastAttemptAt = at;
        if (failingSince == null) {
            failingSince = at;
        }
        lastError = error == null || error.length() <= MAX_ERROR ? error : error.substring(0, MAX_ERROR) + "…";
    }

    public boolean isFailing() {
        return failingSince != null;
    }

    public String getBoard() { return board; }
    public Instant getLastAttemptAt() { return lastAttemptAt; }
    public Instant getLastOkAt() { return lastOkAt; }
    public Integer getJobCount() { return jobCount; }
    public Instant getFailingSince() { return failingSince; }
    public String getLastError() { return lastError; }
}
