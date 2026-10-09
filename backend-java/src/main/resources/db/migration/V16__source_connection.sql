-- Simulated credential state per source system (absent row = VALID), and retry linkage on runs.
CREATE TABLE source_connection (
    source_system VARCHAR(120) PRIMARY KEY,
    status        VARCHAR(20)  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL,
    updated_by    VARCHAR(160)
);
ALTER TABLE scheduler_run ADD COLUMN retry_of_run_id UUID;
