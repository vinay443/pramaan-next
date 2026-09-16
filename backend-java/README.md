# backend-java

Java 21 · Spring Boot 3.3 · Maven Wrapper. Phase 1 scope — no LLM, no external
network calls (mock integrations only).

## Capabilities

| Area | Endpoints | Notes |
|---|---|---|
| Application metadata | `/api/v1/applications` | CRUD-ish (create/replace, list, get, update) |
| Evidence ingestion | `POST /api/v1/evidence/ingest` | SHA-256 dedup, immutable versioning, object-store write, metadata + tags |
| Bulk evidence | `POST /api/v1/evidence/bulk`, `POST /api/v1/evidence/bulk/upload` | JSON array or multipart files; partial success |
| Evidence repository | `GET /api/v1/evidence`, `/{id}`, `/{id}/versions`, `/{id}/versions/{n}` | deterministic filtered queries, stable ordering |
| Integrity | `GET /api/v1/evidence/{id}/verify` | recompute SHA-256 from object store |
| Deterministic queries | `GET /api/v1/evidence/query/{name}` | `source-breakdown`, `stale-evidence`, `latest-per-control`, `duplicates`, `freshness` |
| Scheduler | `POST /api/v1/scheduler/runs`, `/{runId}`, `/{runId}/retry`, `GET /runs`, `/sources` | async run, progress + per-source tally; optional `@Scheduled` trigger (off by default) |
| Rule evaluation | `GET /api/v1/checks`, `POST /api/v1/checks/evaluate`, `GET /api/v1/check-results` | deterministic keyword/expectation rules (`rules/phase1-rules.json`) → `PASS`/`WARNING`/`FAIL`/`NOT_APPLICABLE`, one row per (evidence, check) |
| Completeness (P2) | `GET /api/v1/insight/completeness` | expected-control catalog (`phase2/control-catalog.json`) vs current evidence → `COVERED`/`STALE`/`MISSING` |
| Compliance (P2) | `GET /api/v1/insight/compliance` | check verdicts rolled up per control / framework / application |
| Evidence reuse (P2) | `GET /api/v1/insight/reuse/{id}`, `POST /api/v1/insight/reuse/search`, `POST /api/v1/insight/embeddings/reindex` | embedding nearest-neighbour + deterministic reuse hints |
| AI summary (P2) | `GET /api/v1/insight/evidence/{id}/summary` | grounded evidence summary; deterministic in mock mode |
| NL query (P2) | `POST /api/v1/insight/nl-query` | deterministic keyword routing + AI narration |

Contract: [`../contracts/openapi/pramaan-backend.yaml`](../contracts/openapi/pramaan-backend.yaml).

## Design

- **Persistence:** PostgreSQL via Spring Data JPA + Flyway (`src/main/resources/db/migration`).
- **Object storage:** `ObjectStore` abstraction — `FilesystemObjectStore` (default) / `InMemoryObjectStore` (tests). S3/MinIO can slot in behind the same interface.
- **Integrations:** `EnterpriseIntegration` interface; Phase 1 ships deterministic mocks (`MOCK_JIRA`, `MOCK_SERVICENOW`, `MOCK_GITHUB`, `MOCK_CONFLUENCE`) — same (app, control, day) → identical bytes, so re-runs dedup and a new day yields a new version.
- **Rule engine:** `RuleCatalog` + `RuleEvaluationService` — data-driven, no LLM. Rules in `src/main/resources/rules/phase1-rules.json` (framework/control matcher + ordered substring clauses + default verdict); results persisted to `check_result`, overwritten per (evidence, check) on re-evaluation. Reference: ECS `evidence_validation.py`.
- **Phase 2 AI (`com.pramaan.backend.ai` + `.insight`):** `pramaan.ai.mode=mock` (default) uses deterministic in-process models — no external calls, tests hermetic. `mode=live` calls configurable HTTP APIs (`ChatModel`: OpenAI `/chat/completions` or Anthropic `/v1/messages`; `EmbeddingModel`: OpenAI `/embeddings`). `EmbeddingStore` is `memory` (default) or `pgvector` (`pramaan.ai.vector-store=pgvector`, needs the `pgvector/pgvector` image; DDL is self-applied, not a Flyway migration). Completeness & compliance are fully deterministic; reuse uses embeddings; summaries & NL-query narration use the chat model (routing stays deterministic).
- **Config:** `pramaan.*` in `application.yml` (`PramaanProperties`).

Behavioural reference: the ECS system (`../../ecs-enterprise-backup`, read-only). Not imported.

## Run

```bash
# Manual backend-only start (waits for Postgres, sets required JVM flags):
./run-local.sh

# With an env var override, e.g. LIVE predefined-query execution:
PRAMAAN_PREDEFINED_QUERIES_MODE=LIVE ./run-local.sh
```

Do not chain `docker compose up -d && ./mvnw spring-boot:run` manually — `docker
compose up -d` returns as soon as the container process starts, not once
Postgres actually accepts connections, so Flyway can race it on a cold start
(`FlywaySqlException: Connection to localhost:5433 refused`, intermittent —
timing-dependent). `run-local.sh` polls `pg_isready` before handing off to
Maven, so it never races.

## Test

Focused run (Phase 1 suite):

```bash
./mvnw "-Dtest=HashingTest,EvidenceIngestionServiceTest,SchedulerRunExecutorTest,EvidenceControllerWebTest,RuleEvaluationServiceTest,InsightServicesTest,InsightControllerWebTest,MockEmbeddingModelTest,BackendApplicationTests" test
```

Tests use H2 (`application-test.yml`), the in-memory object store, and mock integrations — no Docker needed.
