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

/** A company whose job board is read on every run. */
@Entity
@Table(name = "tracked_company")
public class TrackedCompany {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Provider provider;

    @Column(nullable = false)
    private String slug;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private Instant addedAt;

    protected TrackedCompany() {
    }

    public static TrackedCompany of(Provider provider, String slug, String name, Instant addedAt) {
        TrackedCompany company = new TrackedCompany();
        company.provider = provider;
        company.slug = slug;
        company.name = name;
        company.addedAt = addedAt;
        return company;
    }

    /** Key of its board in job.board and board_health, e.g. "workable:learnworlds". */
    public String board() {
        return provider.sourceName() + ":" + slug;
    }

    public Long getId() { return id; }
    public Provider getProvider() { return provider; }
    public String getSlug() { return slug; }
    public String getName() { return name; }
    public Instant getAddedAt() { return addedAt; }
}
