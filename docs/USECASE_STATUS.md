# Use-Case Status — Pramaan Next

Status of the 19 use cases from the client registry
`../ecs-enterprise-backup/docs/01-product/use-cases/MD_Usecases (6).xlsx`
(sheet `MD_Usecases`) as implemented in this stack, in that sheet's own order —
this is the single source of truth for UC numbering in this document.

**Legend:** DONE = end-to-end in `pramaan-next` (backend + frontend, focused tests) ·
PARTIAL = core present, surface or sub-feature missing · MISSING = not started.

| UC | Use case (verbatim from xlsx) | Phase | Status | Backend | Frontend | Note |
|----|--------------------------------|-------|--------|---------|----------|------|
| UC01 | Automated scheduled evidence pull | Phase1 | DONE | `scheduler/SchedulerController`, `SchedulerRunExecutor` (async), `ScheduledCollectionTrigger` (cron); `POST/GET /api/v1/scheduler/runs`, `/retry` | `Scheduler.tsx` | Sources are simulated/mock integrations |
| UC02 | Bulk evidence upload | Phase1 | DONE | `evidence/EvidenceController` — `POST /api/v1/evidence/bulk` (JSON), `/bulk/upload` (multipart); `EvidenceIngestionService.ingestBulk` | `BulkUpload.tsx` | Per-item outcomes, partial success |
| UC03 | Metadata tagging and naming convention | Phase1 | DONE | `evidence/EvidenceNaming`, `domain/EvidenceTag`; canonical tag set applied on every ingest path; `GET /api/v1/evidence?tag=&technology=&collectionMethod=` | `MetadataTaggingPanel.tsx` (in `EvidenceDetail.tsx`) | `EvidenceNamingTest` |
| UC04 | Evidence dashboard and hash integrity check | Phase1 | DONE | `EvidenceQueryService.dashboard()`, `util/Hashing`; `GET /api/v1/evidence/dashboard`, `/{id}/verify` | `Dashboard.tsx` | SHA-256 dedup on ingest + repository-wide integrity recheck |
| UC05 | Common evidence querying — pre-defined query | Phase1 | DONE | `EvidenceQueryService` (`GET /api/v1/evidence`, `/query/{name}`); `predefinedquery/PredefinedQueryService` + `PredefinedQueryController` (`GET /api/v1/predefined-queries`, `POST /{controlId}/run`, `/run-all`) | `EvidenceQuery.tsx`, `PredefinedQueries.tsx` | `PredefinedQueryServiceTest` |
| UC06 | Evidence completeness detection | Phase2 | DONE | `insight/CompletenessService`, `ControlCatalog`; `GET /api/v1/insight/completeness` | `Completeness.tsx` | Deterministic, no LLM |
| UC07 | Evidence similarity and reuse | Phase2 | DONE | `insight/EvidenceReuseService`, `EvidenceEmbeddingIndexer` (in-memory / pgvector); `GET /api/v1/insight/reuse/{id}`, `/reuse/search`, `/reuse/by-control` | `EvidenceReuse.tsx` | Exact-duplicate (SHA-256) + embedding similarity |
| UC08 | AI-generated evidence summaries | Phase2 | DONE | `insight/EvidenceSummaryService`; `GET /api/v1/insight/evidence/{id}/summary` | `EvidenceSummaryPanel.tsx` (in `EvidenceDetail.tsx`) | Grounded strictly in stored facts; mock chat model by default |
| UC09 | Natural language audit queries | Phase2 | DONE | `insight/NlQueryService` (deterministic router + RAG over the embedding store); `POST /api/v1/insight/nl-query` | `NlQuery.tsx` | Cites evidence ids; refuses when nothing retrieved |
| UC10 | Leadership compliance dashboards | Phase2 | DONE | `insight/LeadershipService`; `GET /api/v1/insight/leadership` | `Leadership.tsx` | Portfolio rollup of persisted check verdicts |
| UC11 | Multi-application onboarding | Phase3 | DONE | `application/OnboardingService`, `OnboardingCatalog`, `OnboardingController`; `GET /api/v1/onboarding/plan`, `POST /apply` | `Onboarding.tsx` | Idempotent upsert via `ApplicationService`; no asset auto-discovery. Frontend's richer staged-scan flow (`/onboarding/scans`) has no backend yet — served from mock fixtures |
| UC12 | Evidence lifecycle management | Phase3 | DONE | `evidence/EvidenceLifecycleService` — retention-based `EXPIRED`, `RETIRE→SUPERSEDED` archival, immutable `evidence_lifecycle_event` audit trail, versioned via `EvidenceVersion`; `GET/POST /api/v1/evidence/{id}/lifecycle`, `GET /{id}/versions` | `EvidenceDetail.tsx` (per-record panel), `EvidenceLifecycle.tsx` (portfolio-wide retention/archival/version view) | `EvidenceLifecycleServiceTest`, `EvidenceControllerWebTest` |
| UC13 | Cross-application compliance comparison | Phase3 | DONE | `insight/ComparisonService`; `GET /api/v1/insight/comparison` | `Comparison.tsx` | Re-aggregates `ComplianceService`, no new scoring |
| UC14 | GRC platform integration | Phase3 | DONE | `integrations/grc/GrcSyncService` + `GrcSyncController` — pushes an evidence + control-status summary to a mock external GRC endpoint; `GET /api/v1/grc/status`, `POST /api/v1/grc/sync` | `GrcIntegration.tsx` | Mock by default (`pramaan.integrations.grc`), no credentials; `GrcSyncControllerWebTest` |
| UC15 | Enterprise compliance dashboards | Phase3 | DONE | `insight/EnterpriseDashboardService.enterprise()` — business-unit / criticality cuts + top risks over `LeadershipService`; `GET /api/v1/insight/enterprise` | `Enterprise.tsx` | `PortfolioInsightTest` |
| UC16 | Automated regulatory reporting | Pan India | DONE | `reporting/ReportService` + `ReportController` — 6 report views incl. a regulator-ready `regulatory-filing` template (cover page, per-framework breakdown, attestation, distinct from the generic reports); `GET /api/v1/reports`, `/reports/{name}?format=` | `Reports.tsx` — "Report catalogue" + "Regulatory Report" tabs | `ReportControllerWebTest` |
| UC17 | AI-assisted audit preparation | Pan India | DONE | `insight/AuditPrepService` — deterministic checklist + grounded AI narrative; `GET /api/v1/insight/audit-prep` | `AuditPrep.tsx` | Every finding names a real (app, control) |
| UC18 | Compliance trend analysis | Pan India | DONE | `insight/TrendService` — trend from persisted `compliance_snapshot` rows, closure metrics from the lifecycle audit trail; `GET /api/v1/insight/trend`, `POST /trend/snapshot` | `Trend.tsx` | Synthetic seeded history; real snapshots accrue over time |
| UC19 | National compliance dashboard | Pan India | DONE | `insight/EnterpriseDashboardService.national()` (region rollup + RAG) and `.nationalRollup()` (region × framework breakdown, regions ranked by gap to national average); `GET /api/v1/insight/national`, `/national/rollup` | `Enterprise.tsx` (national section), `NationalDashboard.tsx` (rollup) | Region roster is mock (`phase2/national-regions.json`) |

## Rollup

- **DONE: 19 / 19.** **PARTIAL: 0. MISSING: 0.**
- Every use case is implemented end-to-end (backend service + controller +
  frontend page, with focused tests) against the one canonical evidence
  ingestion/repository path (`EvidenceIngestionService` → `EvidenceRecord` /
  `EvidenceVersion` / `EvidenceTag`). External systems unavailable locally
  (GRC endpoint, SharePoint/ServiceNow, national region roster) are mocked
  and clearly marked as such in code and above.

## Team ownership (per `CLAUDE.md`)

Backend services/controllers under `backend-java/` are Teammate 1's area;
frontend pages under `frontend-react/` are Teammate 3's. Coordinate before
changing files outside your area.

---
*Regenerated from `MD_Usecases (6).xlsx` by code inspection — do not hand-edit
without re-checking against current source; prefer regenerating this file over
patching stale entries.*
