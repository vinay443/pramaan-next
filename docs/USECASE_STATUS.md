# Use-Case Status — Pramaan Next

Status of the 19 canonical ECS enterprise use cases (source of truth:
`../ecs-enterprise-backup/docs/01-product/use-cases/phase1/reference/use_case_implementation_matrix.md`)
as re-implemented in this stack. Captured 2026-09-08 by code inspection.

**Legend:** DONE = end-to-end in `pramaan-next` (backend + contract, frontend where applicable) ·
PARTIAL = core present, surface or sub-feature missing · MISSING = not started.

There is no canonical UC20 in the ECS registry (it stops at 19); UC20 is listed as N/A.

> **Two registries.** The table below follows the ECS *implementation matrix* (19
> enterprise use cases). The client *MD_Usecases* registry numbers a few items
> differently — notably **UC05 = "ECS Admin (users, roles, applications)"** there,
> vs "Common evidence querying" here. The client-registry mapping for the
> Phase-1 admin/ingestion slice is tracked in its own section below.

| UC | Use case | Status | Where (pramaan-next) | Note |
|----|----------|--------|----------------------|------|
| UC01 | Automated scheduled evidence pull | DONE | `scheduler/*` (`SchedulerController`, async `SchedulerRunExecutor`, `ScheduledCollectionTrigger` cron, retry), `POST/GET /api/v1/scheduler/runs`, frontend `Scheduler.tsx` | Sources are simulated/mock integrations |
| UC02 | Bulk evidence upload | DONE | `evidence/` `POST /api/v1/evidence/bulk`, `/bulk/upload`; frontend `BulkUpload.tsx` (+test) | Per-item outcomes, partial success |
| UC03 | Metadata tagging & naming convention | DONE | `EvidenceNaming`, `EvidenceTag` domain, filtered `GET /api/v1/evidence?tag=&framework=…` | `EvidenceNamingTest` |
| UC04 | Evidence dashboard & hash integrity check | DONE | SHA-256 dedup in `EvidenceIngestionService`; per-record `GET /api/v1/evidence/{id}/verify`; consolidated `GET /api/v1/evidence/dashboard` (totals + freshness + repository-wide SHA-256 integrity + duplicate-hash count); `Hashing`; frontend `Dashboard.tsx` | — |
| UC05 | Common evidence querying | DONE | `EvidenceQueryService`, `GET /api/v1/evidence`, named `GET /api/v1/evidence/query/{name}`; frontend `EvidenceQuery.tsx` | Stable ordering |
| UC06 | Evidence completeness detection | DONE | `CompletenessService`, `GET /api/v1/insight/completeness`, `ControlCatalog`; frontend `Completeness.tsx` (+test) | Deterministic |
| UC07 | Evidence similarity & reuse | DONE | `EvidenceReuseService`, `EvidenceEmbeddingIndexer`, `PgVectorEmbeddingStore`/`InMemoryEmbeddingStore`, `GET /api/v1/insight/reuse/{id}`, `/reuse/search`, `/embeddings/reindex`; frontend `EvidenceReuse.tsx` | Embedding NN + reuse hints |
| UC08 | AI-generated evidence summaries | DONE | `EvidenceSummaryService`, `GET /api/v1/insight/evidence/{id}/summary`, `HttpChatModel`/`MockChatModel`; frontend `EvidenceSummaryPanel.tsx` | Prompt built strictly from stored facts + verdicts + content excerpt; `groundedOn` returned; mock chat model default |
| UC09 | Natural language audit queries | DONE | `NlQueryService` (deterministic router + RAG evidence-lookup via `EvidenceReuseService` embeddings/pgvector, cited + grounded), `POST /api/v1/insight/nl-query`; frontend `NlQuery.tsx` | Structured intents deterministic; "what evidence for X" answered by retrieval, cites evidence ids, refuses when empty |
| UC10 | Leadership compliance dashboards | DONE | `ComplianceService` (per-app) + `LeadershipService` portfolio rollup, `GET /api/v1/insight/{compliance,leadership}`; frontend `Compliance.tsx`, `Leadership.tsx`, `Dashboard.tsx` | Aggregates actual check verdicts + completeness across all apps |
| UC11 | Multi-application onboarding | DONE | Config-driven `OnboardingCatalog` (`phase2/onboarding.json`) + `OnboardingService` (idempotent upsert via `ApplicationService`, optional initial `SchedulerService` run), `GET /api/v1/onboarding/plan`, `POST /api/v1/onboarding/apply`; `SeedRunner` now seeds from the catalogue; frontend `Onboarding.tsx` | No asset auto-discovery (out of Phase 1-2 scope) |
| UC12 | Evidence lifecycle management | DONE | `EvidenceLifecycleService` — `DRAFT→SUBMITTED→APPROVED / REJECTED`, retention-based `EXPIRED`, `RETIRE→SUPERSEDED`, immutable `evidence_lifecycle_event` audit trail; new version of approved evidence forces re-review; `GET/POST /api/v1/evidence/{id}/lifecycle`; hooked into the one ingestion path; frontend `EvidenceDetail.tsx` | — |
| UC13 | Cross-application compliance comparison | DONE | `ComparisonService` — re-aggregates per-app `ComplianceService` into a framework/control matrix + gap list; `GET /api/v1/insight/comparison`; frontend `Comparison.tsx` | Pure re-aggregation, no new scoring |
| UC14 | SharePoint & ServiceNow integration | DONE | Dedicated `SHAREPOINT` / `SERVICENOW` `EnterpriseIntegration` beans (`ExternalSystemIntegrationsConfig`) — mock by default, config-driven base URLs, **no credentials**; feed the existing `SchedulerRunExecutor → EvidenceIngestionService` path; added to `default-sources` | Live adapters not implemented (mock only) |
| UC15 | Enterprise compliance dashboards | DONE | `EnterpriseDashboardService.enterprise()` — decorates `LeadershipService` with business-unit / criticality cuts + top-risk list; `GET /api/v1/insight/enterprise`; frontend `Enterprise.tsx` | Reuses the one portfolio rollup |
| UC16 | Automated regulatory reporting | DONE | `reporting/ReportService` + `ReportController` — 5 report views (`compliance-summary`, `evidence-register`, `gap-report`, `audit-readiness`, `pan-india`) as JSON or CSV over existing insight/evidence data; `GET /api/v1/reports`, `/reports/{name}?format=`; frontend `Reports.tsx` | No PDF; no dataset of its own |
| UC17 | AI-assisted audit preparation | DONE | `AuditPrepService` — deterministic checklist from completeness + compliance + evidence lifecycle, grounded AI narrative via the shared `ChatModel` (mock-deterministic); `GET /api/v1/insight/audit-prep`; frontend `AuditPrep.tsx` | Every finding names a real (app, control) — no fabrication |
| UC18 | Compliance trend & closure | DONE | `TrendService` — trend from persisted `compliance_snapshot` rows (each re-runs `LeadershipService`, Flyway `V5`), closure metrics from the `evidence_lifecycle_event` trail, collection throughput from `scheduler_run`; `GET /api/v1/insight/trend`, `POST /trend/snapshot`; synthetic weekly history seeded (`TrendSeedRunner`); frontend `Trend.tsx` | Historical posture is mock (seeded); real snapshots accrue over time |
| UC19 | National compliance dashboard | DONE | `EnterpriseDashboardService.national()` — maps applications to (mock) regions (`phase2/national-regions.json`), regional + national compliance/completeness + RAG; `GET /api/v1/insight/national`; frontend `Enterprise.tsx` | Region roster is mock (no real national data locally) |
| UC20 | (not in ECS registry) | N/A | — | Registry ends at 19 |

## Client registry (MD_Usecases) — UC01–UC20, all delivered

Updated 2026-09-08. **One canonical ingestion path** —
`EvidenceIngestionService.ingest(request, ingestionRunId)` (SHA-256 dedup,
immutable versioning, object-store write, UC03 naming/tag set) — is the only
writer; single-item ingest, bulk JSON, bulk file upload and the scheduler
executor all call it. **One authoritative repository** — the JPA entities
`EvidenceRecord` / `EvidenceVersion` (immutable, carries `sha256`,
`version_number`, `size_bytes`, `object_key`, `collected_at`, metadata) /
`EvidenceTag`, Flyway `V1` → PostgreSQL (H2 in tests). Every dashboard, query and
integrity check reads from it — no second store.

| UC (client) | Use case | Status | Where (pramaan-next) |
|-------------|----------|--------|----------------------|
| UC01 | Automated scheduled evidence pull | DONE | `scheduler/` — async `SchedulerRunExecutor` pulls each `EnterpriseIntegration` (mock/simulated) → canonical `ingestion.ingest(req, runId)`; `ScheduledCollectionTrigger` cron (off by default); `POST/GET /api/v1/scheduler/runs`, `/retry`, `/sources`; frontend `Scheduler.tsx` |
| UC02 | Bulk evidence upload | DONE | `POST /api/v1/evidence/bulk` (JSON array) and `POST /api/v1/evidence/bulk/upload` (multipart) → `ingestion.ingestBulk` → per-item `ingest()`; partial success with per-item outcomes/errors; frontend `BulkUpload.tsx` (+test) |
| UC03 | Metadata tagging & naming convention | DONE | `EvidenceNaming` — canonical name `{app}_{tech}_{control}_{yyyyMMdd-HHmmss}` + canonical tag set (`application, technology, control, framework, collectionMethod, version`); applied inside the canonical path so every channel is tagged identically; queryable via `GET /api/v1/evidence?tag=&technology=&collectionMethod=`; `EvidenceNamingTest` |
| UC04 | Evidence dashboard + hash integrity | DONE (this iteration) | **new** `GET /api/v1/evidence/dashboard` on `EvidenceQueryService.dashboard()` — one authoritative snapshot over the persisted repository: record/version/app/framework/source totals, freshness buckets, duplicate-hash count, and a repository-wide hash-integrity check (SHA-256 recomputed from the object store for the current version of every `EvidenceRecord` vs the persisted `evidence_version.sha256`). Reuses existing repos + `ObjectStore` + `Hashing` — no new store/API. Frontend `Dashboard.tsx` rebuilt on it (one call) with an integrity panel; contract + `EvidenceControllerWebTest` |
| UC05 | ECS Admin (users, roles, applications) | DONE | `admin/` — `AdminController` `/api/v1/admin/{roles,users,users/{u},users/{u}/active}`; `AdminService` in-memory persona registry (**no credentials stored**), seeded demo users, canonical role catalogue from `pramaan.admin.roles`; applications reuse the existing `/api/v1/applications` API (not duplicated); contract in `contracts/openapi`; frontend `Admin.tsx` (+test), nav "Admin" |
| UC06 | Common evidence querying | DONE (verified) | `EvidenceQueryService` — filtered `GET /api/v1/evidence` (app/framework/control/source/date + `tag` / `technology` / `collectionMethod` facets, stable ordering, paged) and named `GET /api/v1/evidence/query/{name}` (`source-breakdown`, `stale-evidence`, `latest-per-control`, `duplicates`, `freshness`). Every query runs against persisted evidence via `EvidenceRecordRepository` / `EvidenceVersionRepository` (JPA `Specification`) — no in-memory store. Frontend `EvidenceQuery.tsx`, `EvidenceRepository.tsx`; `EvidenceControllerWebTest` |

| UC07 | Evidence completeness detection | DONE (verified) | `CompletenessService.forApplication` — for every expected `(application, control)` in `ControlCatalog`, checks the latest persisted `EvidenceRecord` (via `EvidenceQueryService.recordsMatching` → `EvidenceRecordRepository.findAll(spec)`) and classifies COVERED / STALE / MISSING with age vs `staleAfterDays`. Deterministic, persisted-evidence only, no LLM. `GET /api/v1/insight/completeness`; frontend `Completeness.tsx` (+test); `InsightServicesTest` |
| UC08 | Evidence similarity & reuse | DONE (this iteration) | `EvidenceReuseService` — **(a) exact duplicates by SHA-256**: `EvidenceVersionRepository.findRecordsWithCurrentSha256` returns other persisted records whose *current version* is byte-identical → `ReuseResult.exactDuplicates` + `querySha256`; **(b) similarity by embeddings + vector store**: `EvidenceEmbeddingIndexer` builds vectors in `EmbeddingStore` — `InMemoryEmbeddingStore` (default) or `PgVectorEmbeddingStore` (`pramaan.ai.vector-store=pgvector`, cosine `<=>`), deterministic ranking + reuse hints. Reuses authoritative records — surfaces reuse candidates so evidence is not re-collected. `GET /api/v1/insight/reuse/{id}`, `POST /api/v1/insight/reuse/search`; frontend `EvidenceReuse.tsx` (exact-duplicate panel); contract + `InsightServicesTest` |
| UC09 | AI-generated evidence summaries | DONE (verified) | `EvidenceSummaryService.summarize` — prompt assembled **only** from the authoritative record: application/framework/control/source, tags, persisted `CheckResult` verdicts, and a bounded content excerpt from the object store. System prompt forbids inference; `EvidenceSummary.groundedOn` returns the exact fact list; `simulated` flag set from `ChatModel.deterministic()`. Java/Spring orchestration; `ChatModel` = `MockChatModel` (deterministic, extracts from the grounded prompt — invents nothing) under `pramaan.ai.mode=mock` (default), `HttpChatModel` (openai/anthropic, configurable base-url/key/model) under `mode=live`. `GET /api/v1/insight/evidence/{id}/summary`; `InsightServicesTest`, `InsightControllerWebTest` |
| UC10 | Natural-language audit queries | DONE (this iteration) | `NlQueryService` — deterministic keyword router. Structured questions (missing/compliance/stale/freshness/duplicates/latest/sources) answered from the deterministic services over persisted data. **Open "what evidence do we have for X" questions → RAG**: `EvidenceReuseService.similarToText` embeds the question and retrieves over the `EmbeddingStore` (`InMemoryEmbeddingStore` default, `PgVectorEmbeddingStore` when `pramaan.ai.vector-store=pgvector`); the answer carries `retrieved` + `evidenceCitations` + `grounded`, and is `"No evidence found in the ECS repository."` when retrieval is empty. Narration system prompt pins the model to the retrieved records only. `POST /api/v1/insight/nl-query`; frontend `NlQuery.tsx` (citations shown); contract; `InsightServicesTest`, `InsightControllerWebTest` |
| UC11 | Leadership compliance dashboard | DONE | **new** `LeadershipService.dashboard()` → `GET /api/v1/insight/leadership` — portfolio rollup that aggregates the **actual** results from `ComplianceService` / `CompletenessService` (persisted evidence + persisted `CheckResult` verdicts) across every application: portfolio compliance %/completeness %, per-application posture, per-framework rollup, and the real estate-wide check-verdict tally (`CheckResultRepository.findAll()` grouped by status). No new scoring, no LLM. Frontend `Leadership.tsx` (+test), nav "Leadership"; contract + `InsightControllerWebTest` |

| UC12 | Multi-application onboarding | DONE (this iteration) | **Configuration-driven**: `OnboardingCatalog` loads `classpath:phase2/onboarding.json` (override `pramaan.onboarding.catalog`) — each entry has slug/metadata, in-scope `frameworks` and evidence `sources`. `OnboardingService.plan()` (dry, flags unknown sources) / `apply(collect)` — idempotent upsert through the existing `ApplicationService` (no new application store), and `collect=true` starts **one** `SchedulerService` run over the catalogued sources (no new ingestion path). `SeedRunner` now delegates to it. `GET /api/v1/onboarding/plan`, `POST /api/v1/onboarding/apply`; frontend `Onboarding.tsx` (+test), nav "Onboarding"; contract; `OnboardingServiceTest` |
| UC13 | Evidence lifecycle management | DONE (this iteration) | `EvidenceLifecycleService` on the single authoritative `EvidenceRecord` (new columns) + immutable `evidence_lifecycle_event` audit trail (Flyway `V4`). States `DRAFT → SUBMITTED → APPROVED` (records reviewer), `REJECTED` (resubmittable), `SUPERSEDED` via `RETIRE`; an `APPROVED` record past `pramaan.evidence.retention-days` (default 365) reads as `EXPIRED`. The ingestion path (only writer) records every create/new-version; a new version of approved evidence auto-transitions to `SUBMITTED` for re-review. `GET/POST /api/v1/evidence/{id}/lifecycle`; `lifecycleState` on `EvidenceView`; frontend `EvidenceDetail.tsx` (+test); contract; `EvidenceLifecycleServiceTest`, `EvidenceControllerWebTest` |
| UC15 | SharePoint / ServiceNow integration | DONE (this iteration) | `ExternalSystemIntegrationsConfig` registers dedicated `SHAREPOINT` and `SERVICENOW` `EnterpriseIntegration` beans. **Mock mode** on by default (`pramaan.integrations.{sharepoint,servicenow}.mock`), **no secrets** — only a config-supplied base URL, stamped into evidence provenance. They register as scheduler sources and flow through `SchedulerRunExecutor → EvidenceIngestionService.ingest` exactly like every other source — **no new ingestion path**; a live adapter can replace either bean behind the interface. Added to `default-sources` and the onboarding catalogue; contract note; `SchedulerRunExecutorTest` |

| UC14 | Cross-application compliance comparison | DONE (this iteration) | `ComparisonService.compare(apps, framework)` — re-aggregates the per-application `ComplianceService` reports into a framework compliance-% matrix (min/max/spread), a per-control status matrix (with a `consistent` flag) and a flat gap list. Pure view over existing results — no new scoring. `GET /api/v1/insight/comparison`; frontend `Comparison.tsx` (+test); contract; `PortfolioInsightTest` |
| UC16 | Enterprise compliance dashboards | DONE (this iteration) | `EnterpriseDashboardService.enterprise()` — decorates the existing `LeadershipService` portfolio rollup with **business-unit** and **criticality** cuts (joined from `ApplicationService`) plus a criticality-weighted **top-risks** list. No second dashboard pipeline. `GET /api/v1/insight/enterprise`; frontend `Enterprise.tsx` (+test); contract; `PortfolioInsightTest` |
| UC17 | Automated regulatory reporting | DONE (this iteration) | `reporting/ReportService` + `ReportController` — a report is a **view** over data the insight/evidence services already produce, rendered as JSON or CSV (`text/csv` + `Content-Disposition`). Five reports: `compliance-summary`, `evidence-register`, `gap-report`, `audit-readiness`, `pan-india`. No report builds its own dataset. `GET /api/v1/reports`, `GET /api/v1/reports/{name}?format=&applicationSlug=&framework=`; frontend `Reports.tsx` (+test); contract; `ReportControllerWebTest` |
| UC18 | AI-assisted audit preparation | DONE (this iteration) | `AuditPrepService.prepare(app, framework)` — **deterministic** checklist assembled from existing `CompletenessService` (MISSING / STALE), `ComplianceService` (NON_COMPLIANT / PARTIAL / NOT_ASSESSED) and the evidence lifecycle state (unapproved evidence), plus a readiness score. The shared `ChatModel` only **narrates** the checklist (deterministic under MOCK_AI); every finding names a real `(application, control)` — no evidence fabricated. `GET /api/v1/insight/audit-prep`; frontend `AuditPrep.tsx` (+test); contract; `PortfolioInsightTest` |
| UC19 | Compliance trend & closure | DONE (this iteration) | `TrendService` — trend points from persisted `compliance_snapshot` rows, each written by re-running the existing `LeadershipService` rollup (`POST /api/v1/insight/trend/snapshot`, Flyway `V5`); **closure** metrics (approvals, rejections, resubmissions, avg days SUBMITTED→APPROVED, approvals-by-week) derived from the `evidence_lifecycle_event` audit trail; **collection throughput** from `scheduler_run` history. `TrendSeedRunner` seeds a synthetic 8-week history (real posture history unavailable locally). `GET /api/v1/insight/trend`; frontend `Trend.tsx` (+test); contract; `PortfolioInsightTest` |
| UC20 | National compliance dashboard | DONE (this iteration) | `EnterpriseDashboardService.national()` — maps applications to regions via a **mock** roster (`classpath:phase2/national-regions.json`, override `pramaan.national.regions`), rolls the `LeadershipService` per-application postures up to regional and national compliance/completeness with a GREEN/AMBER/RED band. `GET /api/v1/insight/national`; frontend `Enterprise.tsx` (national section, +test); contract; `PortfolioInsightTest` |

Not touched (correctly out of scope): Go collectors — the scheduler drives the
new SharePoint/ServiceNow mock integrations with no executor or collector change;
every new dashboard/report/AI feature composes existing services.

## Rollup

- **DONE: 19 / 19** (ECS matrix) — UC01–UC19. Client MD_Usecases registry UC01–UC20 all delivered.
- **PARTIAL: 0** · **MISSING: 0**.
- Every Phase-1 + Phase-2 + Phase-3 + Pan-India use case is implemented end-to-end
  (backend + contract + frontend + focused tests). External/enterprise/national
  data that is unavailable locally is mocked and clearly marked.

## Do-not-regenerate

### Live-UI defect fixes (2026-09-08)

1. **RAG retrieval stale index** — `EvidenceEmbeddingIndexer.ensureIndexed()` only
   guarded a cold store, so evidence ingested *after* the first query stayed
   unsearchable (`retrieved: []`). Now reindexes whenever `EvidenceQueryService.count()`
   grew since the last reindex; NL evidence-lookup floor lowered 0.2→0.1 to match the
   Reuse page. Tests: `RagRetrievalIntegrationTest` (ingest-after-index → matches).
2. **Dashboards showed 0% until manual "Re-evaluate"** — `EvidenceIngestionService`
   now auto-runs scoped `RuleEvaluationService.evaluate(app, control)` on every
   CREATED / NEW_VERSION (covers single ingest, bulk, upload, scheduler — all one
   path). `EndToEndFlowTest` no longer calls `/checks/evaluate`; `PortfolioInsightTest.defect2_*`.
3. **Trend headline used stale synthetic snapshot** — `TrendReport.current` added,
   computed from the live `LeadershipService` rollup; `Trend.tsx` headline uses it.
   `PortfolioInsightTest.defect3_*`.

All use cases (UC01–UC20) are complete and tested (`backend-java` 70 tests
incl. `EndToEndFlowTest` — the full onboard→collect/upload→tag→persist/hash→query
→completeness→reuse→dashboard→AI→lifecycle→enterprise/reporting chain at REST
level; `frontend-react` 42 tests, `agents-go` green — 2026-09-08). Extend in place; do
not rebuild:
- `evidence/` — ingestion (the one canonical write path), query, dashboard + SHA-256 integrity, immutable-versioned repository, lifecycle
- `application/` — CRUD + config-driven onboarding
- `scheduler/` — async runs, cron trigger, retry
- `admin/` — users / roles console
- `insight/` — completeness, compliance, leadership, **comparison, enterprise, national, audit-prep, trend**, reuse + SHA-256 dedup, AI summaries, NL-query + RAG
- `reporting/` — JSON/CSV report views (no dataset of its own)
- `rules/` — deterministic check evaluation
- `ai/` — configurable chat + embedding models, MOCK_AI mode, in-memory + pgvector stores
- `integrations/mock/` — generic mocks + SIM_* tech + SHAREPOINT/SERVICENOW

New capabilities are **compositions** of the above — no duplicate evidence,
dashboard, reporting or AI pipeline was introduced.
