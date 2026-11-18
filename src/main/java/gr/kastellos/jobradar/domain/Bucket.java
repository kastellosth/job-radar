package gr.kastellos.jobradar.domain;

/** The group a job is shown under. SKIP jobs are stored (so they are not re-scored) but hidden. */
public enum Bucket {
    STRONG,
    GOOD,
    STRETCH,
    SKIP
}
