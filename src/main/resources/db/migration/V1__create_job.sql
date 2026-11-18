CREATE TABLE job (
    id            BIGSERIAL PRIMARY KEY,
    fingerprint   VARCHAR(512)  NOT NULL UNIQUE,
    source        VARCHAR(32)   NOT NULL,
    external_id   VARCHAR(255),
    company       VARCHAR(255)  NOT NULL,
    title         VARCHAR(512)  NOT NULL,
    location      VARCHAR(255),
    remote        BOOLEAN       NOT NULL DEFAULT FALSE,
    url           VARCHAR(2048) NOT NULL,
    posted_at     TIMESTAMPTZ,
    score         INTEGER       NOT NULL,
    bucket        VARCHAR(16)   NOT NULL CHECK (bucket IN ('STRONG', 'GOOD', 'STRETCH', 'SKIP')),
    reasons       VARCHAR(1024),
    status        VARCHAR(16)   NOT NULL DEFAULT 'NEW'
                  CHECK (status IN ('NEW', 'SEEN', 'APPLIED', 'REJECTED', 'HIDDEN')),
    first_seen_at TIMESTAMPTZ   NOT NULL,
    last_seen_at  TIMESTAMPTZ   NOT NULL
);

CREATE INDEX job_bucket_status_idx ON job (bucket, status, score DESC);
