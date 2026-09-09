-- Deterministic rule-evaluation outcomes. One row per (evidence record, check).

CREATE TABLE check_result (
    id                 UUID PRIMARY KEY,
    evidence_record_id UUID NOT NULL REFERENCES evidence_record(id) ON DELETE CASCADE,
    check_id           VARCHAR(120) NOT NULL,
    application_slug   VARCHAR(120) NOT NULL,
    control_id         VARCHAR(120) NOT NULL,
    framework          VARCHAR(120) NOT NULL,
    source_system      VARCHAR(120) NOT NULL,
    status             VARCHAR(20)  NOT NULL,
    observed           VARCHAR(2000),
    expected           VARCHAR(2000),
    detail             VARCHAR(2000),
    evidence_version   INT NOT NULL DEFAULT 0,
    evaluated_at       TIMESTAMPTZ NOT NULL,
    UNIQUE (evidence_record_id, check_id)
);
CREATE INDEX ix_check_result_app       ON check_result(application_slug);
CREATE INDEX ix_check_result_framework ON check_result(framework);
CREATE INDEX ix_check_result_control   ON check_result(control_id);
CREATE INDEX ix_check_result_status    ON check_result(status);
