-- Blocked-request audit (UC01-FT012). Written in its own transaction; never stores bodies or credentials.
CREATE TABLE security_event (
    id          UUID PRIMARY KEY,
    occurred_at TIMESTAMPTZ  NOT NULL,
    event_type  VARCHAR(30)  NOT NULL,
    outcome     VARCHAR(10)  NOT NULL,
    role        VARCHAR(64),
    actor       VARCHAR(64),
    method      VARCHAR(10),
    path        VARCHAR(300),
    detail      VARCHAR(300),
    remote_addr VARCHAR(64)
);
CREATE INDEX ix_security_event_time ON security_event(occurred_at);
