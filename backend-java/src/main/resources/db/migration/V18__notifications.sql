-- Run notifications (UC01-FT011). Email is simulated: EMAIL_SIMULATED rows are delivery records only.
CREATE TABLE notification (
    id             UUID PRIMARY KEY,
    created_at     TIMESTAMPTZ  NOT NULL,
    type           VARCHAR(20)  NOT NULL,
    severity       VARCHAR(10)  NOT NULL,
    title          VARCHAR(300) NOT NULL,
    message        VARCHAR(2000) NOT NULL,
    run_id         UUID,
    schedule_name  VARCHAR(200),
    recipient_role VARCHAR(20)  NOT NULL,
    channel        VARCHAR(20)  NOT NULL,
    read_at        TIMESTAMPTZ
);
CREATE INDEX ix_notification_role ON notification(recipient_role, created_at);
CREATE INDEX ix_notification_run ON notification(run_id);

-- Single settings row (id = 1); absent row means the defaults below.
CREATE TABLE notification_settings (
    id                 INT PRIMARY KEY,
    enabled            BOOLEAN NOT NULL,
    notify_on_success  BOOLEAN NOT NULL,
    notify_on_failure  BOOLEAN NOT NULL,
    recipient_roles    VARCHAR(200) NOT NULL,
    email_simulated    BOOLEAN NOT NULL
);
