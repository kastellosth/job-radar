package gr.kastellos.jobradar.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "job")
public class Job {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String fingerprint;

    @Column(nullable = false)
    private String source;

    private String externalId;

    @Column(nullable = false)
    private String company;

    @Column(nullable = false)
    private String title;

    private String location;

    private boolean remote;

    @Column(nullable = false, length = 2048)
    private String url;

    private Instant postedAt;

    @Column(nullable = false)
    private int score;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Bucket bucket;

    /** Why it got this score, e.g. "+3 java, +3 spring boot, +2 title:junior". Shown in the UI. */
    @Column(length = 1024)
    private String reasons;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JobStatus status = JobStatus.NEW;

    @Column(nullable = false)
    private Instant firstSeenAt;

    @Column(nullable = false)
    private Instant lastSeenAt;

    /** The board it was last listed on, e.g. "workable:learnworlds"; see BoardHealth. */
    private String board;

    /** What it pays, when the board or the ad says; see Pay. All null when it does not. */
    private Integer payMin;
    private Integer payMax;
    private String payCurrency;
    @Enumerated(EnumType.STRING)
    private Pay.Period payPeriod;
    private String payText;

    protected Job() {
    }

    public static Job from(JobPosting posting, MatchResult match, Instant now) {
        Job job = new Job();
        job.fingerprint = posting.fingerprint();
        job.firstSeenAt = now;
        job.refresh(posting, match, now);
        return job;
    }

    /** Called when the same job shows up again: keep your status, update everything else. */
    public void refresh(JobPosting posting, MatchResult match, Instant now) {
        this.source = posting.source();
        this.externalId = posting.externalId();
        this.company = posting.company();
        this.title = posting.title();
        this.location = posting.location();
        this.remote = posting.remote();
        this.url = posting.url();
        // A board that only sometimes gives a date (Workday: "30+ Days Ago") must not erase one it gave before.
        if (posting.postedAt() != null) {
            this.postedAt = posting.postedAt();
        }
        this.score = match.score();
        this.bucket = match.bucket();
        this.reasons = truncate(String.join(", ", match.reasons()), 1024);
        this.lastSeenAt = now;
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }

    public Long getId() { return id; }
    public String getFingerprint() { return fingerprint; }
    public String getSource() { return source; }
    public String getExternalId() { return externalId; }
    public String getCompany() { return company; }
    public String getTitle() { return title; }
    public String getLocation() { return location; }
    public boolean isRemote() { return remote; }
    public String getUrl() { return url; }
    public Instant getPostedAt() { return postedAt; }
    public int getScore() { return score; }
    public Bucket getBucket() { return bucket; }
    public String getReasons() { return reasons; }
    public JobStatus getStatus() { return status; }
    public Instant getFirstSeenAt() { return firstSeenAt; }
    public Instant getLastSeenAt() { return lastSeenAt; }
    public String getBoard() { return board; }

    public void listedOn(String board) { this.board = board; }

    public Integer getPayMin() { return payMin; }
    public Integer getPayMax() { return payMax; }
    public String getPayCurrency() { return payCurrency; }
    public Pay.Period getPayPeriod() { return payPeriod; }
    public String getPayText() { return payText; }

    /**
     * The pay this read found. A read that found none keeps what an earlier one found: some boards give the full
     * ad (and so the pay) only on some runs, when its page could be read.
     */
    public void paid(Pay pay) {
        if (pay == null) {
            return;
        }
        this.payMin = pay.min();
        this.payMax = pay.max();
        this.payCurrency = pay.currency();
        this.payPeriod = pay.period();
        this.payText = pay.text();
    }

    public void setStatus(JobStatus status) { this.status = status; }
}
