-- Use Case 13 — evidence lifecycle management (PostgreSQL).

ALTER TABLE evidence_record ADD COLUMN lifecycle_state VARCHAR(20) NOT NULL DEFAULT 'DRAFT';
ALTER TABLE evidence_record ADD COLUMN reviewed_by     VARCHAR(160);
ALTER TABLE evidence_record ADD COLUMN reviewed_at     TIMESTAMPTZ;
ALTER TABLE evidence_record ADD COLUMN lifecycle_note  VARCHAR(2000);

CREATE INDEX ix_evidence_record_lifecycle ON evidence_record(lifecycle_state);

CREATE TABLE evidence_lifecycle_event (
    id                 UUID PRIMARY KEY,
    evidence_record_id UUID NOT NULL REFERENCES evidence_record(id) ON DELETE CASCADE,
    from_state         VARCHAR(20),
    to_state           VARCHAR(20) NOT NULL,
    action             VARCHAR(40) NOT NULL,
    actor              VARCHAR(160),
    note               VARCHAR(2000),
    occurred_at        TIMESTAMPTZ NOT NULL
);
CREATE INDEX ix_evidence_lifecycle_event_record ON evidence_lifecycle_event(evidence_record_id);
