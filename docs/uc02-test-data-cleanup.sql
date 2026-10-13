-- UC02 live-test data cleanup (PostgreSQL). NOT RUN BY ANYONE YET - review the preview output first.
--
-- Removes only the data created by the 10/13 live tests:
--   * application slug 'no-such-app' (auto-created by the old bulk path)
--   * evidence under controlId 'ZZZ-999' or framework 'NOT_A_FW' (and any evidence of 'no-such-app')
--   * evidence whose file name carries a test-run suffix (regex -mv6xxxxx before the extension, e.g. mv6fudft,
--     mv6hxlr5), and the BULK batches made up ONLY of such files
-- Real uploads (ECS_Consolidated..., timesheet xlsx, ...) are not matched by any predicate below. A BULK batch that
-- mixes a test-suffix file with other files is NOT deleted; it is listed in preview 3 for manual review.
--
-- Tables/columns checked against db/migration/V1..V22 and the JPA entities:
--   evidence_record(id, evidence_key, application_slug, control_id, framework, source_object_id)
--   evidence_version(evidence_record_id)  evidence_tag(evidence_record_id)  check_result(evidence_record_id)
--   evidence_lifecycle_event(evidence_record_id)  audit_observation(evidence_id)
--   scheduler_run(id, trigger_type, applications, frameworks)  scheduler_run_source(scheduler_run_id, source_key)
--   notification(run_id)  applications(slug)
--
-- Usage:  psql -d pramaan -f docs/uc02-test-data-cleanup.sql      (it ends in ROLLBACK: nothing is changed)
--         then change the last line to COMMIT; and run again once the previews look right.

BEGIN;

-- ---- 1. what will be removed -------------------------------------------------------------------------------------
CREATE TEMP TABLE doomed_evidence ON COMMIT DROP AS
SELECT id, evidence_key
FROM evidence_record
WHERE control_id = 'ZZZ-999'
   OR framework = 'NOT_A_FW'
   OR application_slug = 'no-such-app'
   OR source_object_id ~* '-mv6[0-9a-z]{5}(\.|$)';

CREATE TEMP TABLE doomed_runs ON COMMIT DROP AS
SELECT r.id
FROM scheduler_run r
WHERE r.trigger_type = 'BULK'
  AND (
        r.applications ILIKE '%no-such-app%'
     OR r.frameworks   ILIKE '%NOT_A_FW%'
     OR (
          EXISTS (SELECT 1 FROM scheduler_run_source s
                  WHERE s.scheduler_run_id = r.id AND s.source_key ~* '-mv6[0-9a-z]{5}(\.|$)')
          AND NOT EXISTS (SELECT 1 FROM scheduler_run_source s
                          WHERE s.scheduler_run_id = r.id AND s.source_key !~* '-mv6[0-9a-z]{5}(\.|$)')
        )
      );

-- preview 1: evidence records that will be deleted
SELECT e.id, e.evidence_key, e.application_slug, e.framework, e.control_id
FROM evidence_record e JOIN doomed_evidence d ON d.id = e.id
ORDER BY e.evidence_key;

-- preview 2: BULK batches that will be deleted
SELECT r.id, r.status, r.applications, r.frameworks, r.created_at
FROM scheduler_run r JOIN doomed_runs d ON d.id = r.id
ORDER BY r.created_at;

-- preview 3: BULK batches that contain a test-suffix file AND other files - kept, review by hand
SELECT r.id, r.status, r.created_at,
       string_agg(s.source_key, ', ' ORDER BY s.source_key) AS files
FROM scheduler_run r JOIN scheduler_run_source s ON s.scheduler_run_id = r.id
WHERE r.trigger_type = 'BULK'
  AND r.id NOT IN (SELECT id FROM doomed_runs)
  AND r.id IN (SELECT scheduler_run_id FROM scheduler_run_source WHERE source_key ~* '-mv6[0-9a-z]{5}(\.|$)')
GROUP BY r.id, r.status, r.created_at;

-- preview 4: evidence ids, to remove their vectors from the pgvector DB (evidence_embedding) and their stored
-- objects (object store keys evidence/<id>/...). Those live outside this database.
SELECT id AS evidence_id FROM doomed_evidence ORDER BY id;

-- preview 5 (PREVIEW ONLY, NEVER DELETED HERE): evidence that looks like earlier test junk. Review by hand.
SELECT e.id, e.application_slug, e.framework, e.control_id, e.source_object_id, e.created_at
FROM evidence_record e
WHERE e.id NOT IN (SELECT id FROM doomed_evidence)
  AND (
        e.source_object_id ILIKE ANY (ARRAY['rc-%', 'rs-%', 'r20-%', 'cc-%', 'FT013-%', 'simfail-%', 'fine-%'])
     OR e.source_object_id LIKE '%..%'
     OR e.source_object_id LIKE '%/etc/passwd%'
     OR e.control_id IN ('MW TLS 1', 'MW-ÜÑÏ-1')
      )
ORDER BY e.created_at;

-- ---- 2. delete, children first -------------------------------------------------------------------------------------
DELETE FROM audit_observation       WHERE evidence_id        IN (SELECT id FROM doomed_evidence);
DELETE FROM check_result            WHERE evidence_record_id IN (SELECT id FROM doomed_evidence);
DELETE FROM evidence_lifecycle_event WHERE evidence_record_id IN (SELECT id FROM doomed_evidence);
DELETE FROM evidence_tag            WHERE evidence_record_id IN (SELECT id FROM doomed_evidence);
DELETE FROM evidence_version        WHERE evidence_record_id IN (SELECT id FROM doomed_evidence);  -- cascades its metadata
DELETE FROM evidence_record         WHERE id                 IN (SELECT id FROM doomed_evidence);

DELETE FROM notification            WHERE run_id             IN (SELECT id FROM doomed_runs);
DELETE FROM scheduler_run           WHERE id                 IN (SELECT id FROM doomed_runs);      -- cascades scheduler_run_source

-- the auto-created test application and rows keyed by its slug (only when no evidence is left under it)
DELETE FROM onboarding_scan         WHERE application_slug = 'no-such-app';
DELETE FROM compliance_snapshot_app WHERE application_slug = 'no-such-app';
DELETE FROM audit_schedule_application WHERE application_slug = 'no-such-app';
DELETE FROM applications a
WHERE a.slug = 'no-such-app'
  AND NOT EXISTS (SELECT 1 FROM evidence_record e WHERE e.application_id = a.id);

-- ---- 3. verify -------------------------------------------------------------------------------------------------------
SELECT (SELECT count(*) FROM applications     WHERE slug = 'no-such-app')                              AS apps_left,
       (SELECT count(*) FROM evidence_record  WHERE control_id = 'ZZZ-999' OR framework = 'NOT_A_FW'
                                                  OR application_slug = 'no-such-app'
                                                  OR source_object_id ~* '-mv6[0-9a-z]{5}(\.|$)')              AS evidence_left;

-- Review the previews above, then replace ROLLBACK with COMMIT.
ROLLBACK;
