package gr.kastellos.jobradar.domain;

import java.util.Locale;

/** Job boards where a company has its own list of jobs, so it can be tracked by its slug. */
public enum Provider {
    WORKABLE("Workable"),
    SMARTRECRUITERS("SmartRecruiters"),
    GREENHOUSE("Greenhouse"),
    LEVER("Lever"),
    WORKDAY("Workday");

    private final String displayName;

    Provider(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    /** The value stored in job.source, e.g. "workable". */
    public String sourceName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
