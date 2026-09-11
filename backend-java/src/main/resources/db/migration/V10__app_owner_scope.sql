-- App Owner scope: application ownership mapping, Target Date (TD) tracking on
-- evidence, view-only notifications, and a general-purpose audit log for TD /
-- status changes. Purely additive — no existing table/column is altered or
-- dropped, and no existing row is touched beyond the new nullable columns below.

CREATE TABLE application_owner (
    id               UUID PRIMARY KEY,
    application_slug VARCHAR(120) NOT NULL REFERENCES applications(slug),
    username         VARCHAR(64) NOT NULL REFERENCES evidence_approval_user(username),
    created_at       TIMESTAMPTZ NOT NULL,
    UNIQUE (application_slug, username)
);

-- Target Date (TD): the date an App Owner commits to for a pending item.
-- Nullable — most evidence never gets one.
ALTER TABLE evidence_record ADD COLUMN target_date DATE;
ALTER TABLE evidence_record ADD COLUMN target_date_comment VARCHAR(1000);

CREATE TABLE target_date_history (
    id                  UUID PRIMARY KEY,
    evidence_record_id  UUID NOT NULL REFERENCES evidence_record(id),
    old_target_date     DATE,
    new_target_date     DATE NOT NULL,
    reason              VARCHAR(1000),
    actor_username      VARCHAR(64) NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_target_date_history_record ON target_date_history(evidence_record_id);

-- View-only for the App Owner persona; nothing in this phase writes acknowledgements.
CREATE TABLE app_owner_notification (
    id                  UUID PRIMARY KEY,
    username            VARCHAR(64) NOT NULL,
    type                VARCHAR(40) NOT NULL,
    message             VARCHAR(500) NOT NULL,
    evidence_record_id  UUID,
    application_slug    VARCHAR(120),
    created_at          TIMESTAMPTZ NOT NULL,
    read_at             TIMESTAMPTZ
);

CREATE INDEX idx_app_owner_notification_username ON app_owner_notification(username);

-- General "who did what when" trail for App-Owner actions (evidence upload /
-- resubmit, TD share / update). Distinct from evidence_lifecycle_event (V4),
-- which only covers lifecycle-state transitions.
CREATE TABLE app_owner_audit_log (
    id             UUID PRIMARY KEY,
    actor_username VARCHAR(64) NOT NULL,
    action         VARCHAR(60) NOT NULL,
    entity_type    VARCHAR(40) NOT NULL,
    entity_id      VARCHAR(80) NOT NULL,
    old_value      VARCHAR(1000),
    new_value      VARCHAR(1000),
    reason         VARCHAR(1000),
    created_at     TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_app_owner_audit_log_entity ON app_owner_audit_log(entity_type, entity_id);

-- Demo application_owner rows are seeded at boot by AppOwnerSeedRunner (Java),
-- not here — evidence_approval_user's own demo rows are likewise seeded at boot
-- (ApprovalRbacSeedRunner), so they don't exist yet at migration time.
