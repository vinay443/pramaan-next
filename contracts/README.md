# contracts/

Single source of truth for cross-service interfaces shared by `backend-java`,
`agents-go`, and `frontend-react`.

- `openapi/` — REST API definitions (OpenAPI 3.1 YAML).
  - `pramaan-backend.yaml` — backend API.
    - Phase 1: applications, frameworks, controls, agents, technical checks +
      check-results, evidence (ingest / bulk / upload / versions / verify /
      deterministic queries), scheduler.
    - Phase 2 (`insight` tag): evidence completeness, reuse/similarity, AI evidence
      summaries, natural-language queries, compliance aggregation. AI features run
      deterministically unless `pramaan.ai.mode=live`.
- `proto/` — gRPC / protobuf for backend ⇄ agents.
  - `agent.proto` — `AgentCollector` service: `Describe` + `RunChecks` streaming
    deterministic `CheckResult`s.

## Rules

- Define or update the contract here **before** implementing either side of it.
- Generate client/server types from these files; do not hand-write DTOs that
  duplicate a contract.
- The backend is the reference implementation of `pramaan-backend.yaml` — if code
  and contract disagree, fix the mismatch, don't fork the shape.
