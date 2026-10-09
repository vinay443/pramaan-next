-- Per-job collection schedules (UC01-FT001). The @Scheduled tick finds active rows
-- with next_run_at <= now and triggers the existing collection-run service.
CREATE TABLE schedule_job (
    id            UUID PRIMARY KEY,
    name          VARCHAR(200) NOT NULL,
    source_system VARCHAR(120) NOT NULL,
    application   VARCHAR(128) NOT NULL,
    evidence_type VARCHAR(120) NOT NULL,
    frequency     VARCHAR(20)  NOT NULL,
    active        BOOLEAN      NOT NULL,
    created_by    VARCHAR(160),
    created_at    TIMESTAMPTZ  NOT NULL,
    next_run_at   TIMESTAMPTZ,
    last_run_at   TIMESTAMPTZ
);
CREATE INDEX ix_schedule_job_due ON schedule_job(active, next_run_at);
