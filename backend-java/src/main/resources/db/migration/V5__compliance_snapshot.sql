-- UC19 — compliance trend snapshots (PostgreSQL).

CREATE TABLE compliance_snapshot (
    id               UUID PRIMARY KEY,
    taken_at         TIMESTAMPTZ NOT NULL,
    applications     INT NOT NULL DEFAULT 0,
    expected         INT NOT NULL DEFAULT 0,
    compliant        INT NOT NULL DEFAULT 0,
    compliance_pct   DOUBLE PRECISION NOT NULL DEFAULT 0,
    completeness_pct DOUBLE PRECISION NOT NULL DEFAULT 0,
    approved_evidence INT NOT NULL DEFAULT 0,
    open_findings    INT NOT NULL DEFAULT 0
);
CREATE INDEX ix_compliance_snapshot_taken_at ON compliance_snapshot(taken_at);
