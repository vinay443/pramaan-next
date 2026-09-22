-- Per-application rows behind each compliance_snapshot, so the trend can be scoped
-- to a business unit / function. Older snapshots have no rows here.

CREATE TABLE compliance_snapshot_app (
    id               UUID PRIMARY KEY,
    snapshot_id      UUID NOT NULL REFERENCES compliance_snapshot(id) ON DELETE CASCADE,
    application_slug VARCHAR(128) NOT NULL,
    business_unit    VARCHAR(128),
    expected         INT NOT NULL DEFAULT 0,
    compliant        INT NOT NULL DEFAULT 0,
    covered          INT NOT NULL DEFAULT 0
);
CREATE INDEX ix_compliance_snapshot_app_snapshot ON compliance_snapshot_app(snapshot_id);
CREATE INDEX ix_compliance_snapshot_app_bu ON compliance_snapshot_app(business_unit);
