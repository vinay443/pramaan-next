# UC03 live-test analysis report

Analysis only. No source/DB writes, no git, no server started, no HTTP calls. The DB access was SELECT-only, via `docker exec pramaan-pgvector` (db `pramaan_vectors`). All paths below are relative to `pramaan-next/`.

Shorthand: `BE` = `backend-java/src/main/java/com/pramaan/backend`, `FE` = `frontend-react/src`.

Live DB snapshot (read-only): 664 `evidence_record`, 1388 `evidence_version`, 0 ARCHIVED, `retention_policy` has 0 rows (default 365 d applies), oldest `evidence_version.collected_at` = 2026-08-14.

---

## ISSUE 1 — FT006 metadata edit with audit history

### a/b) Does an endpoint exist? No — nothing exists.
- `BE/evidence/EvidenceController.java` maps only these (lines 59, 74, 90, 134, 139, 162, 167, 172, 180, 197, 207, 216, 224, 229): `POST /ingest`, `POST /bulk`, `POST /bulk/upload`, `GET /dashboard`, `GET ""`, `GET /{id}`, `GET /{id}/versions[/{v}]`, `GET /{id}/content`, `GET|POST /{id}/lifecycle`, `GET /{id}/verify`, `POST /{id}/frameworks`, `GET /query/{name}`. No `@PatchMapping`.
- The only `@PatchMapping` in the whole backend is `scheduler/ScheduleJobController.java:54`.
- No metadata DTO, service method, test or OpenAPI path exists. `contracts/openapi/pramaan-backend.yaml` has no `/evidence/{id}/metadata` (it lists `/evidence/{id}`, `/content`, `/lifecycle`, `/versions`, `/verify`).
- The closest existing write is `POST /{id}/frameworks` (`EvidenceIngestionService.addFrameworkMapping`, lines 252-285). It only merges the `frameworks` tag; it does not change name, technology or environment.
- The 404 text comes from `common/GlobalExceptionHandler.java:107-110` (`NoResourceFoundException` → "No endpoint <path>"). That is what Spring raises when no handler matches the path, so it is consistent with "mapping missing", not "mapping unreachable". If only the verb were wrong on an existing path you would get 405.
- Causes you listed (prefix, filter order, profile, scanning, non-bean): not applicable, since there is no code to be unreachable.

### c) Stale classes? No.
- `start.sh:332` and `backend-java/run-local.sh` launch `./mvnw spring-boot:run` (no prebuilt jar). It compiles `target/classes` on every start.
- Timestamps: `EvidenceController.java` 10/14 11:03:45, `target/classes/.../EvidenceController.class` 11:04:16 (newer than the source). Restart was 2:07 PM, so the running code matches the source.
- `grep -c PatchMapping EvidenceController.class` = 0.
- How to tell in general: compare `ls -l --time-style=full-iso` of `target/classes/<pkg>/X.class` and the `.java`; or check `target/classes` for the string.

### d) `evidence_lifecycle_event` — can it carry METADATA_EDITED? Yes, no migration needed for that.
- DDL is `V4__evidence_lifecycle.sql`: `id uuid PK, evidence_record_id FK ON DELETE CASCADE, from_state varchar(20) NULL, to_state varchar(20) NOT NULL, action varchar(40) NOT NULL, actor varchar(160), note varchar(2000), occurred_at timestamptz NOT NULL`.
- Live `pg_constraint` check: only PK and FK. There is no CHECK constraint and no DB enum.
- `action` is a plain `String` in the entity. The `LifecycleAction` enum (SUBMIT/APPROVE/REJECT/RETIRE/RESET) only gates `POST /lifecycle`, not the event table.
- Actions already in the live table: INGESTED 664, NEW_VERSION 724, DUPLICATE_SKIPPED 69. `ARCHIVED` is written by `EvidenceLifecycleService.archive` (line 168). `METADATA_EDITED` is 15 chars (≤40).
- Old/new values go in `note` (≤2000 chars): e.g. `environment: UAT→PROD; technology: nginx→tomcat; reason: ...`. A structured old/new JSON column would need a Flyway migration (V23); that is optional.
- `GET /{id}/lifecycle` already returns the history (`EvidenceLifecycleService.view`, lines 171-187), so METADATA_EDITED events appear with no change there.
- `/versions` is content-only. Metadata edits must not create a version (content/hash are immutable).

### e) Naming, sequence, lock, lifecycle
- Name builder: `EvidenceNaming.namePrefix` (lines 63-74) = `slug(app)_TOKEN(control)_TOKEN(evidenceType)[_ENV]_yyyymmdd` (the date is the UTC date of `collectedAt`). `standardName` (56-60) adds `_%03d`.
- Sequence: `resolveSequence` (81-89). The record keeps its own NNN when the current title already starts with the same prefix (`sequenceOf`, 92-101). Otherwise it takes max+1 over the application's other titles with that prefix.
- Allocation and lock: `EvidenceIngestionService.evidenceName` (291-306). When the title does not already match the new prefix, it calls `applications.lockForNaming(slug)` (line 298) and then `records.findByApplicationSlug(...)` (line 299). The lock is `ApplicationRepository.lockBySlug` (lines 14-16), `PESSIMISTIC_WRITE` on the `applications` row (`ApplicationService.java:92`), held until the ingest transaction commits.
- Name uniqueness is only guaranteed by this lock. `pg_indexes` shows no unique index on `title`. So an edit that renames must take the same lock.
- What an environment edit must do:
  1. The prefix changes (`..._DB-CONFIG_UAT_2026...` ↔ `..._PROD_...`). The old NNN no longer matches, so `sequenceOf(newPrefix, oldTitle)==0` and a new NNN is allocated under the app lock. Existing titles of other records are never renumbered (this is the existing rule).
  2. Rewrite `record.title` and the tags `environment` and `name`.
  3. `environment` is in `BATCH_SCOPED_TAGS` (`EvidenceIngestionService.java:43`) and `storedEnvironment()` (313-320) reads it back. An edit must update that tag or the next duplicate/new-version ingest will resurrect the old environment in the name.
  4. A technology edit changes only the `technology` tag; it is not part of the name (`standardTags`, `EvidenceNaming.java:117`).
  5. `evidenceKey` (409-417) does not include environment, so there is no key collision.
- Reuse for the edit: the private helpers `evidenceName`, `applyTags`, `storedEnvironment` and `EvidenceNaming.standardTags` live in `EvidenceIngestionService`. Add the new method there (`updateMetadata`) rather than a new service, or the logic is duplicated.
- Lifecycle transitions: `EvidenceLifecycleService.ALLOWED_FROM` (36-44), `TARGET` (46-51). `RESET` → DRAFT is allowed from every state except ARCHIVED (line 44). `ARCHIVED` is terminal (comment line 43; `markArchived` `EvidenceRecord.java:175-179`). `effectiveState` (150-155) reads APPROVED past retention as EXPIRED.
- For "APPROVED → DRAFT on edit, ARCHIVED blocked", reuse `record.applyLifecycle(DRAFT, actor, note, now)` (`EvidenceRecord.java:182`) and `append(...)` (143-147). If `lifecycleState==ARCHIVED` throw `ApiException.conflict(...)` (same pattern as line 127). Put an `editMetadata`-aware method in `EvidenceLifecycleService` so the state change and event stay in one place.

### f) Role / ownership
- Roles are headers read by `scheduler/SchedulerAccessPolicy` (`X-Pramaan-Role`, `X-Pramaan-User`; lines 36-37). Known roles: APP, AUDITOR, FH, VH, CIO, ADMIN (line 40).
- The interceptor is registered only for the paths in `Registration.addInterceptors` (167-172). `/api/v1/evidence/{id}/metadata` is NOT in the list, so a new PATCH would get no role check and the header-hygiene check would not run. The frontend only sends `X-Pramaan-Role` for paths matching `SCHEDULER_PATH` (`FE/api/client.ts:5`), which also needs extending.
- There is NO user→application mapping. `applications.owner` is a free-text string (`ApplicationEntity.java:32`, V1 column; seeded `app.owner.netbanking` etc. in V2). The persona name sent as `X-Pramaan-User` is "App Owner" (`FE/pages/PersonaLogin.tsx:14`), which matches no owner value. `AdminService` users have roles (`APP_OWNER`) but no application link (`AdminService.java:74`).
- The FE "owned application" is faked: `useOwnedApplication` (`FE/pages/Dashboard.tsx:837-841`) just picks the first active app.
- Consequence: "APP owns the application" cannot be enforced today without a decision (see questions).
- `POST /lifecycle` uses a different header, `X-User-Role`, via `EvidenceApprovalAuthorizer` (`ApprovalRoleScope` table), and is outside the interceptor. Do not copy that scheme.

### g) Frontend
- The Repository detail panel is `RowDetails` in `FE/pages/EvidenceRepository.tsx:255-299` (read-only `<dl>` with Technology, Environment, etc.). The edit action fits there, or in `FE/pages/EvidenceDetail.tsx` / `FE/components/MetadataTaggingPanel.tsx` (existing tagging panel; check it first for reuse).
- Client conventions: typed functions in `FE/api/endpoints.ts` using `apiFetch<T>(path, {method, body})` (see `transitionEvidenceLifecycle`, lines ~369-385) wrapped in `withFallback(...)`. For a write, do not fall back to mock silently. Hooks use `useAsync`. Role gating uses constants mirroring the backend in `FE/pages/PersonaLogin.tsx:64-70` (add `METADATA_EDIT_ROLES`).
- Types go in `FE/api/types.ts`. History display already exists (`FE/components/LifecycleHistoryTable.tsx`).

### Smallest fix
1. `PATCH /api/v1/evidence/{id}/metadata` body `{technology?, environment?, reason}` (reason required, 400 if blank) in `EvidenceController`; service logic in `EvidenceIngestionService.updateMetadata` (reuses naming/tags helpers) + `EvidenceLifecycleService` (state reset to DRAFT if APPROVED/SUBMITTED, 409 if ARCHIVED, event `METADATA_EDITED` with from/to state, actor = `X-Pramaan-User`, note = diff + reason).
2. Add the path to `SchedulerAccessPolicy` (new role set, probably APP + ADMIN) and to the interceptor registration; add to `client.ts` `SCHEDULER_PATH`.
3. OpenAPI path in `contracts/openapi/pramaan-backend.yaml`.
4. FE: edit form with reason in `RowDetails` (or detail page), `patchEvidenceMetadata` in `endpoints.ts`, role gate.
5. Re-index the embedding/summary if the technology tag feeds them (check `EvidenceEmbeddingIndexer.indexOne`; a metadata edit does not change content, so probably unnecessary — verify).

### Risks
- Name collision under concurrency if the app lock is skipped; ownership model undefined; completeness cache fingerprint (`records.count():maxUpdatedAt`) will refresh because `updated_at` changes. Editing an APPROVED record back to DRAFT drops it from approved counts and dashboards (intended per your spec).
- `environment` accepted values: bulk upload validates against `ENVIRONMENTS` (`BulkUploadService.java:60`: PROD, UAT, DR, DEV). Reuse that list (currently package-private).

### Tests
- New `EvidenceMetadataEditTest` (MockMvc, pattern from `BulkUploadUc02Test` / `EvidenceControllerWebTest`): edit technology; edit environment renames and keeps NNN uniqueness vs a second record; APPROVED → DRAFT; ARCHIVED → 409; empty reason → 400; wrong role → 403; history shows METADATA_EDITED; version count unchanged; sha/object unchanged.
- Extend `EvidenceNamingTest` if a helper is added. Add an FE test (`EvidenceRepository.test.tsx`) for the form and role gate.

---

## ISSUE 2 — FT002 technology optional on bulk upload

- Controller: `EvidenceController.java:97` `@RequestParam(required = false) String technology`.
- Service: `BulkUploadService.java:162` `String technology = blankToNull(in.technology()) == null ? null : security.requireSafe(...)`. Application, framework and control go through `requireSafePath` (157-159) and `requireKnownMetadata` (326-343), which is why they return 400.
- Downstream: `EvidenceNaming.resolveTechnology` (161-174) falls back to tag → metadata → source-system substring → `"unknown"`. The `"unknown"` value is kept off new versions if a known one already exists (`EvidenceIngestionService.java:145-152`).
- Consistency: OpenAPI line 495 says "optional; sets the canonical technology tag. Falls back to source-system inference when omitted". UI: `FE/pages/BulkUpload.tsx:187` sends `technology.trim() || undefined`; the input is labelled `placeholder="postgresql / nginx / … (optional)"` (line 288). `BulkUploadUc02Test` only tests supplying it (lines 106, 174-184, 282-294).
- UC3 text (`docs/use-cases.md:92-123`) lists application, control, framework, evidence type as required tags; technology is not named. Completeness scoring treats missing/"unknown" technology as a failed factor (`EvidenceCompletenessService.java` score(), "Technology" factor ~lines 282-290).
- So the API, UI, and OpenAPI are all consistently "optional". The API is not inconsistent with the UI. The test expectation is the odd one out, unless FT002's acceptance criteria (in `docs/MD_Usecases (6).xlsx`, which I did not open) say mandatory.
- Recommendation: if FT002 says mandatory, require it only on `/bulk/upload` (manual path with a human), not on `/ingest` and `/bulk` (agents infer it). Otherwise keep optional and re-classify the test result. See question 1.
- Smallest fix if mandatory: `BulkUploadService.java:162` throw `ApiException.badRequest("technology is required")` (record `INPUT_REJECTED` like `reject()`); `BulkUpload.tsx` required label + disabled submit; OpenAPI `required`; update `BulkUploadUc02Test` and `BulkUpload.test.tsx`.
- Risks: breaks the retry flow and existing callers that omit it; the existing tests that upload without technology need updating.

---

## ISSUE 3 — FT017 retention and archival

### a) Silent scope field
- `RetentionController.java:62-76`: body is `Map<String, Object>`. Only `mode` and `application` are read (lines 64, 68). `applicationSlug` is never looked at.
- There is no DTO, so Jackson's `FAIL_ON_UNKNOWN_PROPERTIES` is irrelevant. A Map accepts any keys (and Spring Boot disables that feature by default anyway; there is no `jackson` config in `application*.yml`).
- Result: `application` null → `RetentionService.archive` (line 51) scopes with `new EvidenceFilter(null,...)` = all 664 records (line 60-61).
- Danger: EXECUTE with a mistyped scope archives every eligible record. ARCHIVED is terminal (`EvidenceLifecycleService.java:44`; the API cannot un-archive; only SQL can). Mitigations today: ADMIN only (`SchedulerAccessPolicy.java:47, 77-83`) and the policy default is 365 d. With the current data (oldest `collected_at` 2026-08-14) nothing is eligible, so an accidental EXECUTE today would archive 0 — but that is not a safeguard.
- An unknown application value is also not rejected: a non-existent slug just scans 0 records.
- Safe contract (smallest change, in the controller only):
  1. Reject unknown keys: allowed = `mode`, `application`, `applicationSlug`; otherwise 400 + `security.record(INPUT_REJECTED, ...)` (same pattern as lines 50-52).
  2. Accept `applicationSlug` as an alias of `application`; reject if both are given and differ.
  3. For EXECUTE require explicit scope: `application` present, or `"allApplications": true`. PREVIEW may stay unscoped.
  4. Validate the slug with `ApplicationService.exists` (used in `BulkUploadService.java:327`) → 400 for unknown.
- Risks: the FE `RetentionPanel` may send no scope for "all": check `FE/components/RetentionPanel.tsx` and its test before requiring a flag. Update OpenAPI (`contracts/openapi/pramaan-backend.yaml:839`).
- Tests: extend `retention/RetentionTest.java` (new cases: unknown field → 400; `applicationSlug` alias scopes; EXECUTE without scope → 400; unknown app → 400). Its helper `archive(mode, app)` (line ~102) builds `{"mode":..,"application":..}` and would need no change.

### b) Eligibility, effects, reversibility
- Timestamp: `evidence_record.latest_collected_at` (`EvidenceRecord.java:57-58`, set from the version's `collected_at` in `addVersion`, line 113). Not `created_at`, not `evidence_version.created_at`.
- Rule: `RetentionService.java:64-66`: lifecycleState ≠ ARCHIVED and `latest_collected_at + days <= started`. Days from `RetentionPolicyService.days()` (default 365, `retention_policy` row id=1 if set).
- Bulk uploads set `collectedAt = clock.instant()` (`BulkUploadService.java:287`), so freshly uploaded evidence only becomes eligible after the window or if backdated.
- EXECUTE changes (`RetentionService.java:69-76`, `EvidenceLifecycleService.archive` 163-169, `EvidenceRecord.markArchived` 175-179):
  - `evidence_record.lifecycle_state='ARCHIVED'`, `archived_at=now`, `updated_at=now`.
  - one `evidence_lifecycle_event` row: action `ARCHIVED`, from=previous state, to=ARCHIVED, actor=`X-Pramaan-User`, note `retention <N>d elapsed (policy)`.
  - one `retention_run` row (table is singular `retention_run`, V22) for PREVIEW and EXECUTE.
  - Does NOT touch: object store, versions, hashes, tags, embeddings; sends no notification; writes no `security_event`. (`RetentionService` has no notification/security dependency.)
- The whole run is one `@Transactional` (line 50-51): all-or-nothing.
- Reversibility: not via API. By SQL only (reset `lifecycle_state`, `archived_at`, delete the ARCHIVED event). Re-running collection over archived evidence reports duplicates and does not un-archive it (`RetentionTest.rerunningCollectionOverArchivedEvidence...`, line 215).

### c) Safe live-test design (for you to run; I ran nothing)
Prereq: bulk upload "never creates an application" (`BulkUploadService.java:327-330`), so the app must exist first. Use the existing endpoint `POST /api/v1/applications` (`ApplicationController.java:37`, `UpsertRequest`: slug, name, owner, criticality, businessUnit, technology) or the SQL below.

**Step 0 — fixture, application (SQL) — the only write in this step:**
```sql
INSERT INTO applications (id, slug, name, criticality, auto_created, created_at, updated_at, active)
VALUES (gen_random_uuid(), 'uc03-retention-test', 'UC03 Retention Test', 'LOW', false, now(), now(), true);
```
**Step 1 — upload one file through the normal flow** (UI Bulk Upload, as App Owner/Admin): application `uc03-retention-test`, any valid framework/control from the catalog, one small `.txt`. This creates the record, version, tag rows, lifecycle event and object the real way.

**Step 2 — pre-check (read-only):**
```sql
SELECT id, application_slug, control_id, framework, lifecycle_state, latest_collected_at, archived_at, title
FROM evidence_record WHERE application_slug = 'uc03-retention-test';           -- expect exactly 1 row, DRAFT
SELECT count(*) FROM evidence_record WHERE application_slug <> 'uc03-retention-test' AND lifecycle_state = 'ARCHIVED'; -- expect 0
SELECT * FROM retention_policy;                                                 -- expect 0 rows (default 365)
```
**Step 3 — backdate, confined to that application (write):**
```sql
BEGIN;
UPDATE evidence_record
   SET latest_collected_at = now() - interval '400 days'
 WHERE application_slug = 'uc03-retention-test';                               -- expect UPDATE 1
UPDATE evidence_version
   SET collected_at = now() - interval '400 days'
 WHERE evidence_record_id IN (SELECT id FROM evidence_record WHERE application_slug = 'uc03-retention-test'); -- keeps row consistent
COMMIT;
```
Only `evidence_record.latest_collected_at` matters for eligibility; the version update is for consistency.

**Step 4 — PREVIEW, scoped (ADMIN persona or `X-Pramaan-Role: ADMIN`):**
`POST /api/v1/retention/archive` body `{"mode":"PREVIEW","application":"uc03-retention-test"}`. Expect `scanned=1, eligible=1, archived=0`. Use the key `application` (not `applicationSlug`, which is ignored — Issue 3a).
Verify (read-only): the record is still not ARCHIVED; a `retention_run` row with `mode='PREVIEW'` and `application='uc03-retention-test'` exists:
```sql
SELECT mode, application, scanned, archived, skipped, retention_days, triggered_by FROM retention_run ORDER BY started_at DESC LIMIT 3;
SELECT lifecycle_state, archived_at FROM evidence_record WHERE application_slug='uc03-retention-test';
```
**Step 5 — EXECUTE, scoped:** body `{"mode":"EXECUTE","application":"uc03-retention-test"}`. Expect `scanned=1, archived=1`.
Verify (read-only):
```sql
SELECT lifecycle_state, archived_at FROM evidence_record WHERE application_slug='uc03-retention-test';           -- ARCHIVED, archived_at set
SELECT action, from_state, to_state, actor, note FROM evidence_lifecycle_event
 WHERE evidence_record_id IN (SELECT id FROM evidence_record WHERE application_slug='uc03-retention-test') ORDER BY occurred_at; -- INGESTED ... ARCHIVED
SELECT count(*) FROM evidence_record WHERE lifecycle_state='ARCHIVED';                                          -- expect 1 (nothing else touched)
SELECT mode, application, scanned, archived FROM retention_run ORDER BY started_at DESC LIMIT 2;                 -- EXECUTE app=uc03-retention-test archived=1
SELECT count(*) FROM retention_policy;                                                                         -- still 0
SELECT sha256, object_key FROM evidence_version WHERE evidence_record_id IN (SELECT id FROM evidence_record WHERE application_slug='uc03-retention-test'); -- unchanged
```
**Step 6 — cleanup (write; children first, same pattern as `docs/uc02-test-data-cleanup.sql`). Run as one transaction and read the counts before COMMIT:**
```sql
BEGIN;
CREATE TEMP TABLE doomed ON COMMIT DROP AS
  SELECT id FROM evidence_record WHERE application_slug = 'uc03-retention-test';
-- the BULK batch(es) that carried the test file
CREATE TEMP TABLE doomed_runs ON COMMIT DROP AS
  SELECT id FROM scheduler_run WHERE trigger_type='BULK' AND applications ILIKE '%uc03-retention-test%';
DELETE FROM audit_observation        WHERE evidence_id        IN (SELECT id FROM doomed);
DELETE FROM check_result             WHERE evidence_record_id IN (SELECT id FROM doomed);
DELETE FROM evidence_lifecycle_event WHERE evidence_record_id IN (SELECT id FROM doomed);
DELETE FROM evidence_tag             WHERE evidence_record_id IN (SELECT id FROM doomed);
DELETE FROM evidence_embedding       WHERE evidence_record_id IN (SELECT id FROM doomed);
DELETE FROM evidence_version         WHERE evidence_record_id IN (SELECT id FROM doomed);
DELETE FROM evidence_record          WHERE id IN (SELECT id FROM doomed);
DELETE FROM notification             WHERE run_id IN (SELECT id FROM doomed_runs);
DELETE FROM scheduler_run_source     WHERE scheduler_run_id IN (SELECT id FROM doomed_runs);
DELETE FROM scheduler_run            WHERE id IN (SELECT id FROM doomed_runs);
DELETE FROM retention_run            WHERE application = 'uc03-retention-test';
DELETE FROM applications             WHERE slug = 'uc03-retention-test';
-- inspect: SELECT count(*) FROM evidence_record WHERE application_slug='uc03-retention-test'; (expect 0)
COMMIT;  -- or ROLLBACK
```
(Column names verified against `information_schema`: `evidence_embedding`, `audit_observation`, `check_result`, `evidence_tag`, `evidence_version`, `evidence_lifecycle_event` all reference `evidence_record_id`/`evidence_id`. I did not verify `notification.run_id`, `scheduler_run_source.scheduler_run_id`, `scheduler_run.trigger_type/applications` beyond the existing cleanup script's use of them, and `evidence_embedding` lives in the same `pramaan_vectors` DB here. Run the temp-table SELECTs first. The stored object under `evidence/<id>/v1/...` stays in the object store (the store has no delete); remove by hand in MinIO/filesystem if you care.)

Existing coverage of EXECUTE: yes — `backend-java/src/test/java/com/pramaan/backend/retention/RetentionTest.java`: preview vs execute (line 131), application scope (195), policy change (204), re-collection doesn't un-archive (215), ADMIN-only + input validation (229). It uses `jdbc.update("update evidence_record set latest_collected_at ...")` to backdate (`backdate()` lines 95-100). Not covered: unknown JSON fields, the alias, EXECUTE-without-scope.

### d) Policy / "archive real data" not exercised
Steps 0-6 never call `PUT /api/v1/retention/policy` (the `retention_policy` row count stays 0) and EXECUTE is scoped to `uc03-retention-test`, so no real record can be archived. This relies on the `application` key being spelled exactly right — hence the verification SELECT in Step 4 (`scanned=1`; if `scanned` is 664 stop, it is not scoped).

---

## ISSUE 4 — FT016 dashboard

### a) `/insight/evidence-completeness/frameworks` cold 4.9 s / warm 11 ms
- Route: `BE/insight/InsightController.java:98-102` → `EvidenceCompletenessService.frameworkRollup` (lines ~154-165) → `forScope(app, null)` (lines 116-128) → `computeScope` (130-150).
- Cache: in-memory `ConcurrentHashMap<String, CachedScope>` (line 81), key `"<app>|<framework>"`, TTL `pramaan.insight.completeness-cache-seconds` default 30 s (line 88). A hit needs the same fingerprint AND age < TTL. Fingerprint = `records.count() + ":" + records.maxUpdatedAt()` (`EvidenceQueryService.java:366-368`). So it is invalidated by any evidence insert or update (including archive and lifecycle changes, since `updated_at` moves) and also expires every 30 s even with no change. Cold is therefore the normal state for the first dashboard view after ≥30 s idle or any upload. The computation also runs inside `cache.compute(...)`, which blocks other callers for the same key (intentional single-flight).
- Why cold = 4.9 s (confirmed by code, magnitude by inference): `computeScope` loads all records (`recordsMatching` → `pageAll`), then `score(r, now)` (lines ~221-300) per record: `objectStore.get(v.getObjectKey())` (reads the whole object) and `Hashing.sha256Hex(bytes)`. Default object-store driver is `minio` (`application.yml:46`, `start.sh:460` sets minio for option D), so ~664 sequential network GETs + hashes. `r.latestVersion()` and `tagValue(r, ...)` also touch lazy `versions`/`tags` collections (`EvidenceRecord.java:82-86`, LAZY) → N+1 selects if not in one session. The frameworks rollup needs only per-control completeness percentages, but goes through the full per-record content-read path.
- To confirm (not done): time per phase with a temporary log or Hibernate statistics: `recordsMatching` vs. `score` loop; count `objectStore.get` calls; or compare with `pramaan.object-store.driver=filesystem`.
- Cheapest fixes, in order:
  1. Cache the per-version integrity/substantive result keyed by `(version.sha256)` (immutable content): the hash of a given immutable version never changes, so `ConcurrentHashMap<sha256, Boolean[]>` removes ~all object reads after the first compute. Smallest change, keeps behaviour. The existing TAMPERED detection (hash of bytes vs stored hash) would then only run once per version per process. Product decision: do you want per-request re-verification on this path? (`/verify` and the dashboard integrity scan already do that separately.)
  2. Warm the cache at startup (an `ApplicationReadyEvent` listener calling `forScope(null,null)`), plus extend TTL; invalidation on upload keeps working via fingerprint.
  3. Longer term: persist completeness at write time. Not recommended now.
- Is invalidation on every upload what makes the first view after a change slow? Yes, but the 30 s TTL makes it slow regardless of uploads.
- Tests: add to `insight/` a test with a counting `ObjectStore` that asserts the second compute (after a changed fingerprint) does not re-read unchanged versions; keep existing completeness tests green.

### b) Frontend mock fallback
- Banner: `FE/components/Layout.tsx:155-161`, shown when `source === 'mock'` (global flag from `FE/api/dataSource.ts`, single module variable, line 10).
- Trigger: `withFallback` in `FE/api/endpoints.ts:~132-175`. On `ApiError.offline` (network error, or the 8 s abort in `apiFetch`, `FE/api/client.ts:68`, because the AbortError is caught and rethrown as `offline: true`, lines 84-86) it loads the mocks, calls `setDataSource('mock')` and returns mock data. For endpoints with `fallbackOnHttpError=true` it does the same on any 4xx/5xx.
- Whole page replaced? No. The flag is global, but each widget replaces only its own data. The effect is: one slow or failing call flips the banner for the entire app, and that widget silently shows mock numbers (as if live), while others stay live; the next successful call flips it back to `live`. So the page can be mixed live/mock under a "mock data" banner (or the reverse), which is what the tester sees.
- Calls with HTTP-error fallback on the dashboard (flag `true`): `getEvidenceDashboard` (endpoints.ts:329-335), `getEvidenceCompletenessFrameworks` (746-754), `getTrend` (853-860), `getEvidenceLifecycleSummary` (874-883). `getDashboardKpis` (628-633) and `listRuns` (462-470) fall back only when offline/timeout.
- Cold 4.9 s + parallel dashboard calls + 8 s timeout is the realistic trigger. During concurrent uploads the endpoint exceeds 8 s → mock.
- Widgets/endpoints (App Owner dashboard, `FE/pages/Dashboard.tsx`): `getEvidenceDashboard` (`/evidence/dashboard`, line 98), `listRuns` (`/scheduler/runs`, 99), `getTrend` (`/insight/trend`, 100), `getDashboardKpis` (`/dashboard/kpis`, polled every 30 s, 368), `getEvidenceCompletenessFrameworks` (`/insight/evidence-completeness/frameworks`, 369 and `useCompletenessSummary.ts:18`), `useAppOwnerData` → evidence, trend, lifecycle (`/insight/evidence-lifecycle/summary`; `hooks/useAppOwnerData.ts:12-20`), `listCheckResults` (`useControlResultsSummary.ts:11`), `listApplications` (DashboardKpiPanel:32, Dashboard:838), `getAuditSchedule` (693), `getCompliance` (729); Auditor/FH/VH/CIO views use `getLeadershipDashboard`, `getComparison`, `getEnterpriseDashboard` (1303-1317, 1524-1525).
- Per-widget error state needs: (1) `withFallback` must not fall back to mock for live-backend failures: surface the error (the pages already have `ErrorNote`/`Loading` in `FE/components/ui.tsx`) and use mock only when `mocksForced()` or offline on the first probe; (2) remove `fallbackOnHttpError=true` for dashboard endpoints; (3) don't flip the global flag to `mock` on a timeout of a single request; use a per-widget "unavailable, retry" state; (4) raise the timeout for the completeness call (e.g. `timeoutMs: 20000`) once the backend fix is in.

### c) `/dashboard/kpis` 403 for ADMIN
- Code says ADMIN is allowed. `SchedulerAccessPolicy.preHandle`: role must be in `KNOWN_ROLES` (line 64: exact, case-sensitive match after `trim()`: `ADMIN` is in the set, line 40). `/api/v1/dashboard/**` is in the interceptor (line 172); a GET on it falls to the `else` branch (97-107) where `read` is true, so every known role passes. `DashboardKpiService.kpis` uses the role only for the unread-notification count (`DashboardKpiService.java:202`); no 403 there.
- So Admin is not intentionally excluded and the code cannot return 403 for `ADMIN`. The 403 without headers matches `UNKNOWN_ROLE` (line 64-68), as in the test (`DashboardKpiTest.java:166`).
- Evidence from the DB: `security_event` has only two events on `/api/v1/dashboard/kpis` (10/14 08:43:22 and 08:43:37), both `UNKNOWN_ROLE` with `role received: (none)`. There is no `ACCESS_DENIED` or `UNKNOWN_ROLE` with an ADMIN-like value, so the ADMIN 403 isn't recorded by this backend.
- Likely causes (suspected, need the exact request): a mis-cased role (`Admin` instead of `ADMIN`; the code would log it as `role received: Admin`, which is not in the table) or a request that did not reach this backend/DB. I cannot reproduce from code.
- To confirm: re-run with `curl -i -H "X-Pramaan-Role: ADMIN" -H "X-Pramaan-User: Admin" .../api/v1/dashboard/kpis` and read the response body (`UNKNOWN_ROLE` vs `ACCESS_DENIED` message) and the new `security_event` row.

---

## ISSUE 5 — known gaps

### a) Orphan objects after a failed file
- Write order, `EvidenceIngestionService.ingest` (`@Transactional`, line 110): decode+hash (127-128) → `getOrAutoCreate` app (132) → `objectStore.put(objectKey, ...)` (180) → add version, name (182-189) → `records.save` (192) → `lifecycle.recordIngestion` (193) → `autoEvaluate` (194; rule evaluation, deliberately failing the ingest on error, comment 199-207) → commit at method exit. A failure at 182-194 or at commit rolls back the DB but leaves the object. `BulkUploadService.handle` catches it (301-307) and reports FAILED/retryable.
- Object key: `evidence/<recordId>/v<n>/<sha>`. For a NEW record the id is `UUID.randomUUID()` (line 139) generated per attempt, so the orphan key is never reused by a retry. For a new version of an existing record the key is deterministic, so a retry reuses it (`put` is no-overwrite, `FilesystemObjectStore.java:31-33`, `MinioObjectStore.java:41-44`) — no orphan growth.
- The simulated outage (`BulkUploadService.simulateOutage`, 505-510, called at 289) throws before ingest, so it does not produce an orphan; the orphan only arises on a real failure after line 180. Test coverage: `BulkUploadFailureRecoveryTest` (untracked, new) covers "no partial record + idempotent resubmit", not objects.
- `ObjectStore` (`storage/ObjectStore.java`) has `put/get/exists/driver` only — no `delete` or `list`. "Immutable" by contract.
- Cheapest safe fix within the filesystem default: (1) preferred — make the put happen last: do all DB work and `autoEvaluate` first, then put the object via `TransactionSynchronization.beforeCommit` (a put failure rolls the transaction back, so there is no DB row without an object, and a post-put commit failure is the only residual gap, tiny); or (2) an orphan-sweep job: add `list(prefix)` to `ObjectStore` (Filesystem/Minio/InMemory) and delete or report keys with no `evidence_version.object_key`. Option 1 needs no interface change and is within the decision log (no new store wiring). Risk: `ruleEvaluation.evaluate` (autoEvaluate) may read the object just written, so it must see the bytes. Verify before moving the put (rules read via `objectStore.get`); if so, keep the put before `autoEvaluate` and do option 2, or compensate by registering `afterCompletion(ROLLED_BACK)` with an added `delete` method (an interface change; conflicts with the "immutable" contract; needs your call).
- Tests: extend `BulkUploadFailureRecoveryTest` with an `ObjectStore` spy that throws in `autoEvaluate`/`recordIngestion` and asserts no object remains (or is reported by the sweep).

### b) Upload form for roles that cannot upload
- Gating exists: `FE/pages/BulkUpload.tsx:56-58`: `canUpload = !persona || BULK_UPLOAD_ROLES.includes(persona.code)`; `BULK_UPLOAD_ROLES = ['APP','ADMIN']` (`FE/pages/PersonaLogin.tsx:67`, mirrors `SchedulerAccessPolicy.UPLOAD_ROLES`, line 43). It shows an error note (222-223) and disables only the submit button (line 371: `disabled={busy || !canUpload}`).
- Gap: the metadata inputs, the dropzone/file input (click handlers ~lines 309-345) remain visible and usable for AUDITOR/FH/VH/CIO. Smallest fix: wrap the form controls in `<fieldset disabled={!canUpload}>` (the pattern already used in `Scheduler.tsx:529`) or return early with only the note and batch history. The backend already returns 403 (`SchedulerAccessPolicy.java:84-89`). Also check the sidebar link in `FE/components/Layout.tsx`/`App.tsx` if you want to hide the nav entry for those roles. Test: `BulkUpload.test.tsx` with an AUDITOR persona asserts inputs are disabled.

### c) Parallel uploads slow
- Lock scope: one row lock per application: `ApplicationRepository.lockBySlug` (`PESSIMISTIC_WRITE` on the `applications` row, lines 14-16), taken in `EvidenceIngestionService.evidenceName` (line 298) only when the record needs a new NNN (a new record, or a title whose prefix changed). It is a DB row lock (not a JVM lock), per application, not per (app, control, env, date), and held until the ingest transaction commits (comment 296-297).
- What runs under the lock: the full rest of the transaction: `records.findByApplicationSlug(slug)` loading every record of the app into memory (line 299; with several hundred records in one app this is the heaviest part), `records.save`, `lifecycle.recordIngestion` (193), `autoEvaluate` → `ruleEvaluation.evaluate` for (app, control) (194), flush and commit.
- Not held across: the object write (line 180 is before line 298) or the SHA (128, and the controller reads bytes before). Also `BulkUploadService.upload/process` are not `@Transactional` and loop sequentially, so within a batch each file is its own transaction; the contention is between simultaneous requests for the same application. Retries add delay: up to 12 attempts with jittered sleeps (`BulkUploadService.java:62, 475-490`).
- Narrowing without losing unique NNN:
  1. Replace `findByApplicationSlug` (loads all entities) with a projection: `select title from evidence_record where application_slug=:s and title like :prefix||'\_%'` (add to `EvidenceRecordRepository`). Biggest saving, no semantic change.
  2. Lock on (app, prefix) via `pg_advisory_xact_lock(hashtext(:app||'|'||:prefix))` instead of the app row, so different controls/dates/environments in the same app don't serialize. Still held to commit; uniqueness holds because two records only collide when they share a prefix.
  3. Move `autoEvaluate` after the lock's critical section — it needs `afterCommit`/a separate step; it is deliberately inside the transaction ("a failure here is a genuine defect and rolls the ingest back"), so changing this is a behaviour change: not recommended.
- Not confirmed by measurement: I can't time it without running. To confirm, log elapsed time in `ingest()` around lines 298-299 vs 193-194 under a parallel load test (`BulkUploadConcurrencyTest` is the existing harness).
- Tests: `BulkUploadConcurrencyTest` (uniqueness of NNN under N parallel uploads, already modified in your tree) must stay green; add an assertion that concurrent uploads to different controls in the same app both succeed with distinct names.

### d) ConcurrentUsersTest failure
- Existing surefire output (`backend-java/target/surefire-reports/com.pramaan.backend.scheduler.ConcurrentUsersTest.txt`, 10/14 11:07): 4 tests, 1 failure: `concurrentTogglesAndSettingsSavesNeverFailAndAStaleVersionIsAConflict`, `expected: 409 but was: 200` at `ConcurrentUsersTest.java:177` (the stale-version settings save). I did not re-run it.
- Code under test: `NotificationService.saveSettings` (lines 160-186): `if (req.version() != null && s.getVersion() != null && !req.version().equals(s.getVersion())) throw ObjectOptimisticLockingFailureException` → 409. `NotificationSettings.version` is `@Version` (line 35-36). The 409 path is intact.
- The test (lines 158-177): two simultaneous PUTs (a: APP only, b: APP+ADMIN); reads `v = finalSettings.version()`; then PUTs `a` again with no version (line 172) and expects that to bump the version; then PUTs `b` with version `v` and expects 409.
- Root cause (confirmed by reading code, not by execution): the intermediate `PUT a` bumps the version only if it changes something. `saveSettings` calls `s.apply(...)` and `settingsRepo.save(s)` (lines 178-181); if the stored state already equals `a` (when `a` was the last of the two concurrent saves, ~50% of runs), Hibernate sees no dirty fields and issues no UPDATE, so `@Version` doesn't change. The stale save with `v` then equals the current version and succeeds with 200. If `b` was last, `a` changes the row, the version moves, and the test passes.
- Verdict: test bug (order-dependent flake, no regression, no shared-state issue between tests). The code path being tested works. Fix in the test only: make the intermediate save always differ from the final state (e.g., choose `a` or `b` by `finalSettings.recipientRoles().size()` so it flips the state, or flip `emailSimulated`), and optionally assert the version increased. To confirm, run `./mvnw -Dtest=ConcurrentUsersTest test` 5 times: failures should be intermittent. I did not run it (your rule: focused tests only, and the failure is already recorded).
- Optional product decision: make `saveSettings` bump the version even when nothing changed (not needed).

---

## Ranking by severity

| # | Issue | Severity | Why |
|---|---|---|---|
| 1 | FT006 metadata edit missing | High | Required feature absent end to end (endpoint, UI, contract, audit). Also needs an ownership decision. |
| 2 | FT017 silent scope on `/retention/archive` | High | Unknown field is ignored and EXECUTE then runs against every eligible record; ARCHIVED is irreversible through the API. Mitigated by ADMIN-only and no eligible data today. |
| 3 | FT016 dashboard cold 4.9 s + global mock fallback | Medium-High | The slow call plus the 8 s timeout can turn the whole app to a "mock data" banner and show mock numbers as if live. Misleading in an audit tool. |
| 4 | Orphan objects after failures | Medium | Storage leak and untracked bytes in an immutable store; no data corruption. |
| 5 | Parallel upload slowness | Medium | Lock held across rule evaluation plus loading all app records; affects throughput, not correctness. |
| 6 | FT002 technology optional | Low-Medium (decision) | Consistent with UI/OpenAPI/UC3 text; depends on the FT002 acceptance criteria. |
| 7 | Upload form visible to non-upload roles | Low | Submit is disabled and backend returns 403; cosmetic/UX. |
| 8 | ConcurrentUsersTest 409 | Low | Test flake (no-op PUT does not bump `@Version`), not a regression. |
| 9 | `/dashboard/kpis` 403 for ADMIN | Low (unconfirmed) | Code allows ADMIN; not reproducible from code or the security log. |

## Proposed order of fixes
1. Issue 3a (retention contract): small, removes the only data-loss hazard.
2. Issue 1 (metadata edit): backend + OpenAPI first, then UI, after the ownership decision.
3. Issue 4 (cache per immutable version + startup warm-up; FE per-widget error state, no mock-on-timeout).
4. Issue 5c quick win (projection query + advisory lock per prefix) and 5a (orphan strategy) together; both touch `EvidenceIngestionService.ingest`.
5. Issue 5b (fieldset), 5d (test fix), Issue 2 once decided.
6. Issue 4c: re-test with exact curl before touching code.

## Questions that need your decision
1. FT002: does the acceptance criterion say technology is mandatory? If so, only on `/bulk/upload`, or also `/ingest` and `/bulk`? (I did not open `docs/MD_Usecases (6).xlsx`.)
2. FT006 ownership: how should "APP owns the application" work? Options: (a) compare `X-Pramaan-User` to `applications.owner` (needs seed/persona alignment), (b) a new user→application table (migration, V23), (c) skip enforcement for the demo and allow APP + ADMIN. Which?
3. FT006: which roles may edit (APP + ADMIN?), which fields (technology, environment only, or also framework/control)? Should editing an APPROVED record always go back to DRAFT, and what about SUBMITTED?
4. FT017: for EXECUTE, require explicit scope (`application` or `allApplications:true`)? Does the current `RetentionPanel` ever send an unscoped EXECUTE?
5. FT016: is per-request SHA re-verification inside completeness required, or may we cache integrity per immutable version?
6. Orphans: OK to move the object write to `beforeCommit` (if rule evaluation does not read the object), or do you prefer an orphan-sweep job (needs `list` on `ObjectStore`)? Adding `delete` conflicts with the immutable contract.
7. ADMIN 403: please send the exact request/response (body message) and which backend instance answered.
