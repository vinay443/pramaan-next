-- Link a run to the schedule job that started it (null for manual / bulk runs).
ALTER TABLE scheduler_run ADD COLUMN schedule_id   UUID;
ALTER TABLE scheduler_run ADD COLUMN schedule_name VARCHAR(200);
ALTER TABLE scheduler_run ADD COLUMN evidence_type VARCHAR(120);
