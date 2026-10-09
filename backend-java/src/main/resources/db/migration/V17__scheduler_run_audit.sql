-- Audit attribution for runs (all nullable; existing rows stay null). started_at / finished_at already exist (V1).
ALTER TABLE scheduler_run ADD COLUMN triggered_by       VARCHAR(160);
ALTER TABLE scheduler_run ADD COLUMN schedule_frequency VARCHAR(20);
ALTER TABLE scheduler_run ADD COLUMN schedule_created_by VARCHAR(160);
