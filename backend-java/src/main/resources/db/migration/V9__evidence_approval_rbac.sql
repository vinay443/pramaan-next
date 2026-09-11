-- RBAC for evidence approval — role/scope reference table + a demo user roster
-- surfaced through the existing ECS Admin ("Users & Roles") API. No credentials;
-- identity is simulated via the X-User-Role / X-User-Framework request headers
-- (see EvidenceApprovalAuthorizer) — this table only carries scope config + demo
-- personas, matching AdminService's existing no-credentials design.

CREATE TABLE evidence_approval_role (
    role                 VARCHAR(40) PRIMARY KEY,
    framework_scope      VARCHAR(40),
    control_family_scope VARCHAR(40),
    can_submit           BOOLEAN NOT NULL DEFAULT FALSE,
    can_approve          BOOLEAN NOT NULL DEFAULT FALSE,
    description          VARCHAR(200) NOT NULL
);

CREATE TABLE evidence_approval_user (
    username     VARCHAR(64) PRIMARY KEY,
    display_name VARCHAR(120) NOT NULL,
    role         VARCHAR(40) NOT NULL REFERENCES evidence_approval_role(role),
    created_at   TIMESTAMPTZ NOT NULL
);
