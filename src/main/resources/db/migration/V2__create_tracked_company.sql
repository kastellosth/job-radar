-- Companies whose job boards are read on every run. Added and removed from the dashboard.
CREATE TABLE tracked_company (
    id       BIGSERIAL PRIMARY KEY,
    provider VARCHAR(32)  NOT NULL CHECK (provider IN ('WORKABLE', 'SMARTRECRUITERS', 'GREENHOUSE', 'LEVER')),
    slug     VARCHAR(255) NOT NULL,
    name     VARCHAR(255) NOT NULL,
    added_at TIMESTAMPTZ  NOT NULL
);

-- The same company pasted twice with different capitals is still the same company.
CREATE UNIQUE INDEX tracked_company_provider_slug_uq ON tracked_company (provider, lower(slug));

-- The list that used to live in application.yml, checked against the live APIs on 2026-11-19.
INSERT INTO tracked_company (provider, slug, name, added_at) VALUES
    ('WORKABLE', 'european-dynamics', 'European Dynamics', now()),
    ('WORKABLE', 'qualcogroup', 'Qualco Group', now()),
    ('WORKABLE', 'agileactors', 'Agile Actors', now()),
    ('WORKABLE', 'uni-systems', 'Uni Systems', now()),
    ('WORKABLE', 'learnworlds', 'LearnWorlds', now()),
    ('WORKABLE', 'epignosis', 'Epignosis', now()),
    ('WORKABLE', 'upstream', 'Upstream', now()),
    ('WORKABLE', 'welcomepickups', 'Welcome Pickups', now()),
    ('WORKABLE', 'adaptit', 'ADAPTIT', now()),
    ('WORKABLE', 'performance-technologies', 'Performance Technologies', now()),
    ('WORKABLE', 'stoiximan', 'Stoiximan', now()),
    ('WORKABLE', 'skroutz', 'Skroutz', now()),
    ('WORKABLE', 'orfium', 'Orfium', now()),
    ('WORKABLE', 'deeplab', 'DeepLab', now()),
    ('SMARTRECRUITERS', 'NetcompanyIntrasoft', 'Netcompany-Intrasoft', now()),
    ('GREENHOUSE', 'kaizengaming', 'Kaizen Gaming', now());
