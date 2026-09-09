-- Dashboard trends — capture evidence volume + hash-integrity in each posture snapshot.

ALTER TABLE compliance_snapshot ADD COLUMN evidence_count    INT NOT NULL DEFAULT 0;
ALTER TABLE compliance_snapshot ADD COLUMN integrity_checked INT NOT NULL DEFAULT 0;
ALTER TABLE compliance_snapshot ADD COLUMN integrity_intact  INT NOT NULL DEFAULT 0;
