# Mock-data schema

All files are plain JSON, small, and cross-referenced by string id. Field names
match `../openapi/pramaan-backend.yaml` where an equivalent type exists; extra
fields (marked ✚) are prototype-only context not yet in the backend contract.

## applications.json

- `environments[]` — `{ id, name, tier: "production" | "non-production" }` ✚
- `applications[]` — contract `ApplicationUpsert` plus `environments: string[]` ✚
  (server-managed `autoCreated`/`createdAt`/`updatedAt` are omitted from the seed).

## agents.json

- `agents[]` — contract `AgentDescriptorView` (identity fields); also the shape of
  `agent describe` output from `agents-go` and proto `AgentDescriptor`.
- `assets[]` ✚ — `{ id, application, environment, kind, technology, address, agentId }`.
  `kind` ∈ `os | database | middleware | tls`. Mirrors proto `Asset`.
- `collectorCatalog[]` — the predefined technical checks each collector runs.
  Each `checks[]` entry is contract `CheckDefView` (`GET /api/v1/checks`) /
  proto `CheckDef`; mirrors `agents-go` `AllChecks()`. Build an
  `AgentDescriptorView` by joining an agent's `collectors[]` to this catalog.

## frameworks.json (reference data)

- `frameworks[]` — contract `FrameworkView` (`GET /api/v1/frameworks`).
  `id` is what evidence/results carry as `framework`.
- `controls[]` — contract `ControlView` (`GET /api/v1/controls`).
  `id` is what evidence/results carry as `controlId`.

## evidence.json

- `evidence[]` — contract `EvidenceView`, plus ✚ `environment`, `assetId`, `agentId`.
  `latest` is `EvidenceVersionView`. `evidenceId` here is a short slug (`ev-001`),
  not a UUID — consumers must not assume UUID format for fixtures.

## scheduler-runs.json

- `sources[]` — contract `GET /api/v1/scheduler/sources`.
- `runs[]` — contract `RunView`.

## results.json

Per-check outcomes (drives PASS/WARNING/FAIL summaries and drill-downs).
Contract `CheckResultView` (`GET /api/v1/check-results`) / proto `CheckResult`.

- `statusValues[]` — allowed `status`: `PASS | WARNING | FAIL | NOT_APPLICABLE`
  (contract `CheckStatus`; mirrors ECS `evidence_validation` verdicts).
- `results[]` — `{ id, evidenceId, application, assetId, framework, controlId,
  checkId, status, observed, expected, detail, collectedAt }`
  (`application` here is the `applicationSlug`; `runId`/`agentId` omitted in the seed).

## Referential integrity

```
results.evidenceId      -> evidence.evidenceId
results.controlId       -> frameworks.controls.id
evidence.applicationSlug-> applications.slug
evidence.assetId        -> agents.assets.id            (nullable)
evidence.agentId        -> agents.agents.agentId       (nullable)
agents.assets.agentId   -> agents.agents.agentId
agents.assets.application-> applications.slug
scheduler-runs.runs.applications[] -> applications.slug
```

`manifest.json.referenceNow` is the fixed "now" for freshness math
(`staleAfterDays = 90`).
