-- Upcoming audit scheduling: which framework audits are booked, when, and which
-- applications are in scope for each. One row per (audit, application) in the join
-- table, matching this codebase's existing convention (see compliance_snapshot_app)
-- of a plain join/detail table over an array column.

CREATE TABLE audit_schedule (
    id             UUID PRIMARY KEY,
    framework      VARCHAR(40) NOT NULL,
    audit_name     VARCHAR(200) NOT NULL,
    scheduled_date DATE NOT NULL
);
CREATE INDEX ix_audit_schedule_date ON audit_schedule(scheduled_date);

CREATE TABLE audit_schedule_application (
    audit_schedule_id UUID NOT NULL REFERENCES audit_schedule(id) ON DELETE CASCADE,
    application_slug  VARCHAR(128) NOT NULL
);
CREATE INDEX ix_audit_schedule_application_schedule ON audit_schedule_application(audit_schedule_id);
