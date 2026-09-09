# ECS Migration Inventory

Read-only survey of the existing ECS codebase (`../ecs-enterprise-backup`), captured
as reference for the `pramaan-next` rebuild. **Nothing in ECS was modified.** This
document records behavior and interfaces only — no ECS runtime code is imported.

## Application shape

- **Framework:** FastAPI (`app/main.py`, `FastAPI(title="ECS Consolidated Demo V13")`), server-rendered Jinja2 templates + a thin React/JSON API layer. Python 3 — *not carried forward*.
- **Run model:** bare `uvicorn app.main:app` locally; Postgres / pgvector / Redis / MinIO via Docker Compose (app itself runs outside Docker).
- **Startup (`ecs_lifespan`):** loads `.env` (`app/env_bootstrap.py`), refreshes framework→repository catalog (~700 rows), validates predefined-query registry + environment config, best-effort schema init, non-fatal LLM-RAG readiness check. No cron / APScheduler.
- **Layout:** legacy flat `app/*.py` (many now compatibility shims) → primary impl under `modules/<domain>/{engines,services,routes,templates}` + platform/RAG core under `ecs_platform/`.
- **Config:** `config/*.yaml` with `${ENV:-default}` interpolation; per-env overlays in `config/environments/`; framework/control master in `config/framework_control_master/`.

### Domain modules (`modules/`)

| Module | Responsibility |
|---|---|
| `executive_overview` | CIO/leadership dashboards, ROI center, enterprise & pan-India views, trends, reports |
| `frameworks` | Framework catalog & loader, control master, control validation, application governance, ITPP |
| `governance` | Evidence completeness, health, approval, lifecycle, comparison, search, audit-prep, gap export, workflow queues |
| `operations` | Scheduler, connectors/integrations, predefined queries, onboarding, bulk upload, AI-ops assistant/summary, evidence reuse story, common controls |
| `audit_intelligence` | Evidence orchestrator, deterministic evidence validation, asset/technology discovery & mapping, observation generation, dashboards, LLM workbench/benchmark |
| `enterprise_grc` | Risk register, heatmaps, exceptions/RAF, regulatory, correlation, CMDB, GRC demo service |
| `ai_sdlc` | AI-SDLC control tower, stage gates, AI registry, AI governance posture, onboarding, reports |
| `patch_management` | Patch compliance / pending patch submission |
| `shared` | Nav framework, RBAC/role scope, chatbot engines, drilldown engines, evidence workflow engine, `ecs_state`, route registration, templates/partials, static JS |

## Use cases

Catalogued in `docs/01-product/product/ECS_MASTER_USE_CASE_REGISTRY.md` (150+ entries):

- **A — Audit:** readiness scoring, evidence pack assembly, pre-audit gap ID, findings→closure, freshness/trail/calendar/trend.
- **B–E — Evidence & control library:** management, reuse, versioning, classification, search, control↔framework crosswalk.
- **F/K — Frameworks & compliance:** assessment, readiness, mapping, compliance %, continuous compliance, exports.
- **G/X — Governance & executive:** governance analytics/QA/lifecycle/CAB/CMDB/AI-gov, CIO posture, board summary, ROI.
- **R/RAF — Risk:** risk register, heatmaps, SLA, AI risk, risk acceptance via exception (first-class RAF + ISG lifecycle = target).
- **O/I — Operations & connectors:** scheduler, connector health, onboarding, integrations (Jira, Confluence, ServiceNow, Prisma, SharePoint, Teams, Azure DevOps, GitHub, Jenkins, Gitea, Figma) — "enable + validate".
- **BL — Technology baselining (predefined queries):** Linux, PostgreSQL, Yugabyte, Trivy, GitLeaks, SonarQube, Kubernetes/OpenShift, Aerospike live (Phase 1); Windows, Oracle, SQL Server, MySQL deferred.
- **AI/CP — AI assistant & copilots:** RAG Q&A + refusal, retrieval, drafting, classification, mapping; Audit / Governance / Executive / Ops copilots (local-LLM-first).
- **OB / W — Observations & workflow:** observation raise/track/persist/auto-close (durable behind flag); workflow engine for onboarding, approval, mock audit, exception, report.

Phase-2 AI dependency reality: only NL audit queries + AI evidence summaries touch the LLM; completeness detection, quality scoring, evidence similarity/reuse, and leadership dashboards are deterministic.

## APIs

Registered in `app/main.py` via `register_*` functions. ~250 distinct paths.

- **MVP HTML/form routes** — `modules/shared/routes/routes_mvp.py` (~124 handlers), one `/mvp/*` per screen plus actions: `/mvp/scheduler/{run,run-status,pause,resume,retry}`, `/mvp/workflow/*`, `/mvp/platform/*`, `/mvp/predefined-queries/{prepare,run,detail}`, `/mvp/integrations*/sync`, `/mvp/audit/*`, `/mvp/ai-sdlc/*`, `/mvp/api/chat-*`.
- **Evidence routes** — `modules/shared/routes/evidence_routes.py`: `/evidence/upload`, `/evidence/submit`, `/evidence/revalidate`, `/evidence/repository`, `/evidence/{id}`, `/audit/package/{generate,export}`.
- **Audit-LLM API** — `modules/audit_intelligence/routes/routes_audit_llm.py`: `/api/audit-llm/{query,classify,token-estimate,prompts,profiles,benchmark,replay,compare,validate-grounding}`.
- **React JSON API** — `modules/shared/routes/routes_react_api.py`: `/api/react/{scorecard,governance-analytics,search,roi,demo-overview,trends,patch-management/pending}`.
- **Other registrars** — platform, governance, ai-sdlc governance, grc-demo, ecs-benchmark, nav aggregators, audit-intelligence, audit-ui.
- **Static mount** — `/static/ecs` → `modules/shared/static`.

## Screens

~150 Jinja2 templates under `modules/<domain>/templates/` (+ partials):

- **Executive:** `dashboard*.html` per persona, `cio_dashboard`, `mvp_enterprise`, `mvp_pan_india`, `mvp_roi_center`, `mvp_trends`, `mvp_reports`, `login`.
- **Governance:** `mvp_completeness`, `mvp_evidence_{dashboard,health,approval}`, `mvp_lifecycle`, `mvp_comparison`, `mvp_reuse`, `mvp_search`, `mvp_audit_prep`, `mvp_workflow_*`, `evidence_review`.
- **Operations:** `mvp_scheduler`/`gov_scheduler`, `mvp_predefined_queries(+_detail)`, `mvp_integrations(+_hub)`, `mvp_onboarding`, `mvp_bulk_upload`, `mvp_ai_ops_{assistant,summary}`, `mvp_evidence_reuse_story`, `platform_evidence_explorer`.
- **Audit intelligence:** `audit/*` — `asset_inventory`, `technology_{inventory,mapping}`, `evidence_{repository,packs,runs}`, `validation_results`, `observations`, `executive_readiness`, `llm_workbench`, `connector_test_workbench`, `admin_users_roles`.
- **Frameworks:** `framework`, `framework_loader`, `mvp_framework_{admin,control_master}` + drill/insight partials.
- **Enterprise GRC:** `mvp_{risk_register,heatmaps,exceptions,exception_governance,regulatory,correlation,cmdb,governance_analytics}`.
- **AI-SDLC:** `mvp_ai_sdlc_*`, `mvp_sdlc_gates(+_stage)`, `mvp_ai_registry`, `mvp_ai_governance_posture`.
- **Shared chrome:** `partials/ecs_sidebar`, `ecs_nav_*`, `chatbot_global`, `evidence_workflow_system`, drilldown modal, chart standards.

## Evidence lifecycle

- **State machine:** `modules/shared/services/evidence_workflow_engine.py` — owner states `draft → uploaded → pending_app_owner → pending_auditor → submitted / reupload`, plus counters & timelines; backed by in-memory `app/ecs_state.py`.
- **Ingest:** `/evidence/upload` → validate → `/evidence/submit`; bulk via `/mvp/bulk-upload`. Approval: `modules/governance/engines/evidence_approval_engine.py`; review: `evidence_review.py`.
- **Durable path:** `ecs_platform/ingestion.py` + `ecs_platform/repository/repository.py` (Postgres) + evidence bytes to MinIO; `ecs_platform/evidence_indexing.py` chunks & embeds into pgvector.
- **Evidence intelligence:** `app/evidence_intel/*` (lineage, reuse, versioning, sufficiency_v2, readiness, change); `app/evidence_analytics/*` (quality, timeline, portfolio, closure, difference); `modules/audit_intelligence/services/evidence_reuse_service.py` (read-only, no-LLM).
- **Orchestrated collection:** `modules/audit_intelligence/engines/evidence_orchestrator.py` → `evidence_validation.py` → verdict (PASS/FAIL/WARNING/NA) + quality score.
- **Observations:** `app/observations/store.py`, `modules/audit_intelligence/engines/observation_generation.py` (durable behind flag).

## Scheduler

- `modules/operations/engines/scheduler_module.py` (+ `scheduler_intelligence.py`, `scheduler_progress.py`). **No time-based scheduling** — triggered on demand by `POST /mvp/scheduler/run` (role/user form fields).
- `start_scheduler_collection_async()` runs a background **thread** worker; progress tracked by `run_id` (`/mvp/scheduler/run-status`); pause/resume/retry endpoints.
- Per-run sources, each flag-gated: connector pulls, mock evidence (`ECS_MOCK_EVIDENCE_COLLECTION_ENABLED`), common controls (`ECS_COMMON_CONTROLS_COLLECTION_ENABLED`), predefined queries, SharePoint.
- Asset-discovery runs: `modules/audit_intelligence/services/{asset_scheduler,scheduler_execution}.py`.

## Connectors

Three overlapping connector stacks:

1. **`ecs_platform/connectors/`** — `ConnectorFactory` driven by `config/integrations.yaml`; `_REGISTRY` maps type→`module:Class`: gitea, github, sonarqube, jenkins, jira, confluence, figma, servicenow, teams, sharepoint, prisma, azure_devops. Shared `base.py`, `http_client.py`, `_msgraph.py`.
2. **`modules/operations/integrations/`** — cloud/scanner: aws, azure, gcp, azure_devops, github, jenkins, jira, confluence, servicenow_cmdb, sharepoint_graph, teams_graph, outlook_graph, prisma_cloud, qualys, nessus, checkmarx, tripwire, archer (bases `_base.py`, `_platform_bridge.py`, `ms_graph_base.py`).
3. **Technology / predefined-query connectors** — `modules/operations/engines/*_connector.py`: postgresql, mysql, sqlserver, oracle, mongodb, redis, yugabyte, aerospike, linux, kubernetes, sonarqube, trivy, gitleaks. Registry + execution mode: `config/predefined_query_phase1_registry.yaml`; targets: `config/predefined_query_targets.*.yaml`.

- **Connectivity assessment:** `app/connectivity/` — deterministic, offline-by-default DNS/network/TLS/auth/discovery readiness scoring; flag-gated (`CONNECTIVITY_ASSESSMENT_ENABLED`), no live probe wired.

→ `pramaan-next` mapping: these become **Go agents/collectors** (`agents-go/`).

## Rule engine

Deterministic (no-LLM) scoring/validation engines:

- **Evidence sufficiency:** `app/sufficiency/{engine,rules}.py` — 0–100 composite from 5 sub-scores (completeness, freshness, traceability, coverage, review); read-only, flag-gated (`SUFFICIENCY_ENGINE_ENABLED`). Newer: `app/evidence_intel/sufficiency_v2.py`.
- **Evidence validation:** `modules/audit_intelligence/engines/evidence_validation.py` — data-driven keyword/expectation rules → verdict + control_status + quality. 100% deterministic.
- **Control validation:** `modules/frameworks/engines/control_validation_engine.py` — configuration / file_based / policy_mapping / evidence_reuse / sla_td / implementation checks.
- **Completeness / missing evidence:** `modules/governance/engines/{governance_completeness_engine,missing_evidence_engine}.py` — pure catalog logic.
- **Deterministic query router:** `modules/audit_intelligence/llm/deterministic_router.py` — answers audit questions from ECS data with no LLM (LLM only summarizes).
- **RBAC policy:** `ecs_platform/rbac/policy.py`, `modules/shared/services/{role_permissions,role_filter_scope}.py`, `config/rbac.yaml` / `config/auth.yaml`.

## Storage

- **Relational:** PostgreSQL evidence repository — `ecs_platform/repository/repository.py`, schema `ecs_platform/repository/{schema.sql,governance_schema.sql}`, config `config/repository.yaml`. Host port 5433, db `ecs_repository`. psycopg2 imported lazily.
- **Vector:** pgvector — `ecs_platform/vectorstore/{factory,base,pgvector_store}.py`, config `config/vectorstore.yaml` (768-dim, table `evidence_embeddings`, chunk 1000 / overlap 150). Host port 5434, db `ecs_vectors`. Provider-switchable (chroma/milvus stubs).
- **Object store:** MinIO / S3-compatible with local-filesystem fallback — `ecs_platform/storage/object_store.py`; immutable evidence bytes only, keyed by connector/key/version/hash. Host API port 9002.
- **Cache:** Redis (`localhost:6379`); in-process `modules/shared/utils/simple_cache.py`.
- **In-memory demo state:** `app/ecs_state.py` — backs most MVP screens (not durable).
- **On-disk evidence objects:** `data/evidence-objects/evidence/<SOURCE>/<App--Control>/v1/*.json`.

## Search / RAG / chatbot

- **RAG core:** `ecs_platform/rag.py` — pipeline: RBAC filter → pgvector retrieval (repository fallback) → reuse mapping → framework crosswalk → context assembly → LLM generation (grounded, citations required) → cited response. Anti-hallucination contract: returns `"No evidence found in ECS repository."` on empty retrieval.
- **LLM engine:** `ecs_platform/llm_engine/` — `provider.py` (`get_provider()`), `retriever.py`, `prompt_builder.py`, `generator.py`, `metrics_logger.py`, `retrieval_metrics.py`.
- **Providers:** `config/llm.yaml` — default `ollama` / `qwen3:8b`, embeddings `nomic-embed-text`; pluggable gemini / openai / azure_openai / claude via `ECS_LLM_PROVIDER`.
- **Deterministic fallback chatbot:** `modules/shared/services/chatbot_engine.py` (+ `chatbot_enhanced.py`, `chatbot_context_engine.py`, `chatbot_nav.py`) — intent routing over live analytics/work-queue data; global widget `partials/chatbot_global.html`; endpoints `/mvp/chat`, `/mvp/api/chat-*`.
- **AI-ops:** `modules/operations/engines/{ai_ops_assistant_engine,ai_ops_summary_engine,ai_ops_response_modes}.py`; endpoint `/mvp/ai-ops-assistant/summary/{mode}`.
- **Audit LLM workbench:** `modules/audit_intelligence/llm/` — `query_classifier.py`, `context_builder.py`, `execution_service.py`, `prompt_library.py`, `token_estimator.py`, `benchmark_runner.py`, `llm_evaluation.py`; config `config/audit_llm_{prompt_library,benchmark_profiles}.yaml`.
- **Faceted/semantic search:** `modules/governance/engines/search_module.py`; `/mvp/search`, `/api/react/search`.
- **Reindex:** `ecs_platform.rag.reindex_evidence()` — bare function, no route/CLI.
- **Benchmarks:** `benchmarks/` — RAG golden set / metrics (`benchmarks/config/rag_*.json`, `benchmarks/output/rag_metrics.*`) + AI-workload capacity models.

## Mock / test data

- **`data/mock-evidence/<App>/<Framework>/`** — DEMO_MODE mock evidence (`modules/operations/engines/mock_evidence_collector.py`); apps NetBanking / MobileBanking / Payments; frameworks PCI-DSS, DPSC, ITPP, C-SITE, etc. Populated only on scheduler run, gated by `ECS_MOCK_EVIDENCE_COLLECTION_ENABLED`.
- **`data/mock-sharepoint/`** — SharePoint evidence-path simulation.
- **`data/llm_usecase_demo/`** — LLM use-case demo seed (`llm_usecase_demo_seed.py`).
- **`data/phase2-reusability/`** — Phase-2 reusable-control simulation (`phase2_reusability.py`, `phase2_intelligence.py`, `phase2_tech_adapters.py`).
- **`data/patch-management/`** — patch demo data.
- **`CommonControls/<control>/{manifest,evidence}.json`** — 12 reusable common-control packs (encryption at rest/in transit, audit logging, backup/restore, certs, identity/PAM, network security, secure config, time sync, vuln/patch).
- **`data/evidence-objects/`** — committed predefined-query, common-controls, and connector (confluence/github/azure_devops) evidence JSON.
- **Demo engines:** `modules/executive_overview/engines/{demo_seed,demo_metrics,demo_kpi_drill_engine,enterprise_mock_service}.py`, `modules/governance/engines/{governance_mock_data,operational_mock_data}.py`, `modules/operations/engines/operations_mock_data.py`, `ecs_platform/{demo_evidence,demo_governance}.py`, `modules/shared/services/ecs_mock_engine.py`.
- **Data-source honesty:** `modules/shared/utils/{demo_data_standards,data_source_marker}.py` + `partials/ecs_data_source_indicator.html` mark mock vs real in the UI.
- **Tests:** `tests/` (pytest) + `conftest.py` — Python; not carried forward. Behavioral intent to be re-expressed as JUnit / Go tests.

## Notes for the rebuild

- ECS leans heavily on in-memory `ecs_state`; only the repository/RAG flow is durable. `pramaan-next` should make Postgres the source of truth from the start.
- Scheduler is thread-based and manual — replace with a real scheduler/queue.
- Three overlapping connector stacks → consolidate into Go collectors behind one contract in `contracts/`.
- Many `app/*.py` are re-export shims of `modules/*` — no equivalent needed.
- Auth: Azure AD provider enforced unless `DEMO_MODE=true`.
- **A stray `docs/MIGRATION_INVENTORY.md` was written into the ECS working tree during an earlier step. It should be reverted there** (`git -C ../ecs-enterprise-backup checkout -- docs/MIGRATION_INVENTORY.md`) — the authoritative copy is this file.
