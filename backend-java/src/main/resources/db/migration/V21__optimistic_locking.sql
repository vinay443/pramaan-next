-- Optimistic locking for concurrent users (UC01-FT015). Existing rows start at version 0.
ALTER TABLE schedule_job ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE notification_settings ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- One notification per (run, role, channel): concurrent finalisation can never duplicate rows.
CREATE UNIQUE INDEX ux_notification_run_role_channel ON notification(run_id, recipient_role, channel);
