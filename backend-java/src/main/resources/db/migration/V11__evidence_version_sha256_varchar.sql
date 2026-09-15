-- EvidenceVersion.sha256 is mapped as a plain String with @Column(length = 64),
-- which Hibernate's schema validator checks against VARCHAR, not the CHAR/bpchar
-- type V1__init.sql originally used. Align the column type to the entity mapping.
ALTER TABLE evidence_version ALTER COLUMN sha256 TYPE VARCHAR(64);
