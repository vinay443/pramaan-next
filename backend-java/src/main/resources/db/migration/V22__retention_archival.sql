-- Retention policy + archival workflow (UC01-FT017). Archival only changes lifecycle state; objects, hashes and
-- versions are never touched.
ALTER TABLE evidence_record ADD COLUMN archived_at TIMESTAMPTZ;

-- Single row (id = 1); no row means the configured default (pramaan.evidence.retention-days, 365).
CREATE TABLE retention_policy (
    id             INT PRIMARY KEY,
    retention_days INT         NOT NULL,
    updated_by     VARCHAR(64),
    updated_at     TIMESTAMPTZ NOT NULL,
    version        BIGINT      NOT NULL DEFAULT 0
);

CREATE TABLE retention_run (
    id             UUID PRIMARY KEY,
    mode           VARCHAR(10) NOT NULL,
    triggered_by   VARCHAR(64),
    started_at     TIMESTAMPTZ NOT NULL,
    finished_at    TIMESTAMPTZ,
    retention_days INT         NOT NULL,
    application    VARCHAR(128),
    scanned        INT         NOT NULL,
    archived       INT         NOT NULL,
    skipped        INT         NOT NULL
);
CREATE INDEX ix_retention_run_started ON retention_run(started_at);
