-- How the reads of each board went (a tracked company's job list, or Remotive), so that a board that is down
-- or broken keeps its jobs instead of having them hidden as closed, and so the dashboard can show it.
CREATE TABLE board_health (
    board           VARCHAR(300) PRIMARY KEY,
    last_attempt_at TIMESTAMPTZ  NOT NULL,
    last_ok_at      TIMESTAMPTZ,
    job_count       INTEGER,
    failing_since   TIMESTAMPTZ,
    last_error      VARCHAR(400)
);

-- The board a job was last listed on, e.g. 'workable:learnworlds'. Empty for jobs not seen since this migration:
-- those fall back to the plain rule (hidden after radar.hide-unlisted-after without being listed).
ALTER TABLE job ADD COLUMN board VARCHAR(300);
