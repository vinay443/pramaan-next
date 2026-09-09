-- Pramaan Next — Phase 1 schema (PostgreSQL).

CREATE TABLE applications (
    id             UUID PRIMARY KEY,
    slug           VARCHAR(120) NOT NULL UNIQUE,
    name           VARCHAR(200) NOT NULL,
    business_unit  VARCHAR(120),
    criticality    VARCHAR(20)  NOT NULL DEFAULT 'MEDIUM',
    owner          VARCHAR(160),
    technology     VARCHAR(400),
    auto_created   BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMPTZ  NOT NULL,
    updated_at     TIMESTAMPTZ  NOT NULL
);

CREATE TABLE evidence_record (
    id               UUID PRIMARY KEY,
    evidence_key     VARCHAR(600) NOT NULL UNIQUE,
    application_id   UUID NOT NULL REFERENCES applications(id),
    application_slug VARCHAR(120) NOT NULL,
    control_id       VARCHAR(120) NOT NULL,
    framework        VARCHAR(120) NOT NULL,
    source_system    VARCHAR(120) NOT NULL,
    source_object_id VARCHAR(200),
    title            VARCHAR(400),
    current_version  INT NOT NULL DEFAULT 0,
    latest_sha256    CHAR(64),
    latest_collected_at TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL,
    updated_at       TIMESTAMPTZ NOT NULL
);
CREATE INDEX ix_evidence_record_app       ON evidence_record(application_slug);
CREATE INDEX ix_evidence_record_framework ON evidence_record(framework);
CREATE INDEX ix_evidence_record_control   ON evidence_record(control_id);
CREATE INDEX ix_evidence_record_source    ON evidence_record(source_system);

CREATE TABLE evidence_version (
    id                UUID PRIMARY KEY,
    evidence_record_id UUID NOT NULL REFERENCES evidence_record(id),
    version_number    INT NOT NULL,
    sha256            CHAR(64) NOT NULL,
    content_type      VARCHAR(120) NOT NULL DEFAULT 'application/octet-stream',
    size_bytes        BIGINT NOT NULL,
    object_key        VARCHAR(600) NOT NULL,
    collected_at      TIMESTAMPTZ NOT NULL,
    collected_by      VARCHAR(160),
    ingestion_run_id  UUID,
    created_at        TIMESTAMPTZ NOT NULL,
    UNIQUE (evidence_record_id, version_number)
);
CREATE INDEX ix_evidence_version_sha ON evidence_version(sha256);

CREATE TABLE evidence_version_metadata (
    evidence_version_id UUID NOT NULL REFERENCES evidence_version(id) ON DELETE CASCADE,
    meta_key            VARCHAR(160) NOT NULL,
    meta_value          VARCHAR(2000),
    PRIMARY KEY (evidence_version_id, meta_key)
);

CREATE TABLE evidence_tag (
    id                 UUID PRIMARY KEY,
    evidence_record_id UUID NOT NULL REFERENCES evidence_record(id) ON DELETE CASCADE,
    tag_key            VARCHAR(160) NOT NULL,
    tag_value          VARCHAR(600),
    UNIQUE (evidence_record_id, tag_key)
);

CREATE TABLE scheduler_run (
    id            UUID PRIMARY KEY,
    trigger_type  VARCHAR(20) NOT NULL,
    status        VARCHAR(20) NOT NULL,
    requested_by  VARCHAR(160),
    applications  VARCHAR(2000),
    frameworks    VARCHAR(2000),
    sources       VARCHAR(2000),
    received      INT NOT NULL DEFAULT 0,
    ingested      INT NOT NULL DEFAULT 0,
    duplicates    INT NOT NULL DEFAULT 0,
    failed        INT NOT NULL DEFAULT 0,
    message       VARCHAR(2000),
    created_at    TIMESTAMPTZ NOT NULL,
    started_at    TIMESTAMPTZ,
    finished_at   TIMESTAMPTZ
);
CREATE INDEX ix_scheduler_run_status ON scheduler_run(status);

CREATE TABLE scheduler_run_source (
    scheduler_run_id UUID NOT NULL REFERENCES scheduler_run(id) ON DELETE CASCADE,
    source_key       VARCHAR(120) NOT NULL,
    source_summary   VARCHAR(600),
    PRIMARY KEY (scheduler_run_id, source_key)
);
