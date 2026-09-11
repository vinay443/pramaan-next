-- Onboarding: additive JSON profile column + staged onboarding-scan tracking (mirrors scheduler_run).

ALTER TABLE applications ADD COLUMN onboarding_profile JSONB;

CREATE TABLE onboarding_scan (
    id                UUID PRIMARY KEY,
    application_slug  VARCHAR(120) NOT NULL,
    status            VARCHAR(20)  NOT NULL,
    requested_by      VARCHAR(160),
    frameworks        VARCHAR(2000),
    sources           VARCHAR(2000),
    current_phase     VARCHAR(60),
    scheduler_run_id  VARCHAR(64),
    completeness_pct  DOUBLE PRECISION,
    compliance_pct    DOUBLE PRECISION,
    message           VARCHAR(2000),
    created_at        TIMESTAMPTZ NOT NULL,
    started_at        TIMESTAMPTZ,
    finished_at       TIMESTAMPTZ
);
CREATE INDEX ix_onboarding_scan_app    ON onboarding_scan(application_slug);
CREATE INDEX ix_onboarding_scan_status ON onboarding_scan(status);

CREATE TABLE onboarding_scan_phase (
    onboarding_scan_id UUID NOT NULL REFERENCES onboarding_scan(id) ON DELETE CASCADE,
    phase_key          VARCHAR(60) NOT NULL,
    phase_status        VARCHAR(600),
    PRIMARY KEY (onboarding_scan_id, phase_key)
);
