-- First-class audit observations linked to evidence + control (UC01-FT014 follow-up).
CREATE TABLE audit_observation (
    id             UUID PRIMARY KEY,
    evidence_id    UUID          NOT NULL REFERENCES evidence_record(id),
    control_id     VARCHAR(120)  NOT NULL,
    framework      VARCHAR(120)   NOT NULL,
    application    VARCHAR(128)  NOT NULL,
    text           VARCHAR(1000) NOT NULL,
    status         VARCHAR(10)   NOT NULL,
    raised_by_role VARCHAR(20)   NOT NULL,
    raised_by      VARCHAR(64),
    created_at     TIMESTAMPTZ   NOT NULL,
    closed_at      TIMESTAMPTZ
);
CREATE INDEX ix_audit_observation_evidence ON audit_observation(evidence_id);
CREATE INDEX ix_audit_observation_control ON audit_observation(application, framework, control_id, status);
