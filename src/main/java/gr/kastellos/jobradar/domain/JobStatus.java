package gr.kastellos.jobradar.domain;

/** Where he is with a job. Only NEW jobs count as "new" in the dashboard. */
public enum JobStatus {
    NEW,
    SEEN,
    APPLIED,
    REJECTED,
    HIDDEN
}
