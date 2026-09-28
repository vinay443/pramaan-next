# P01 — Automated Scheduled Evidence Pull (Go layer)

Go-only implementation of P01. Package: [`internal/job`](../../internal/job).
CLI entrypoint: `agent job` (see [`cmd/agent/main.go`](../../cmd/agent/main.go)).

This is a deterministic job-execution envelope around the existing Phase 1
collectors in `internal/collectors` — it does not add new collection logic.
It adds the safety envelope P01 requires: validate → look up an approved,
versioned collector → validate parameters → validate the source → run with a
timeout and bounded output → return a structured result → replay safely on
retry.

No LLM. No agent loop. No arbitrary SQL. No shell execution. See
[ARCHITECTURE.md](ARCHITECTURE.md) for how that's enforced.

## Quick start (fully offline)

```bash
cd agents-go
SIMULATION_MODE=true AGENT_JOB_SOURCES_PATH=examples/job/sources.json \
  go run ./cmd/agent job -request examples/job/request-os-baseline.json
```

This runs one approved collector (`os-baseline` v1) against a synthetic
source, with no backend, no real server, and no network access. See
[RUNBOOK.md](RUNBOOK.md) for more scenarios (real-mode file-based collection,
an unavailable source, a rejected request, etc.).

## Documents in this set

| Doc | Contents |
|---|---|
| [ARCHITECTURE.md](ARCHITECTURE.md) | Design, execution flow, how it reuses `internal/agent`/`internal/evidence`, safety boundary |
| [CONTRACT.md](CONTRACT.md) | Input contract, output contract, error contract, catalog of approved collectors |
| [RUNBOOK.md](RUNBOOK.md) | How to run it locally/offline, flags, env vars, example invocations and their output |
| [TEST_REPORT.md](TEST_REPORT.md) | Every test scenario required by the P01 spec, and the actual `go test` output that exercised it |
| [ACCEPTANCE.md](ACCEPTANCE.md) | Requirement-by-requirement checklist against the P01 spec |
| [ROLLBACK.md](ROLLBACK.md) | How to disable or remove this feature without touching Java/React |

## What this does NOT do

- It does not modify `backend-java` or `frontend-react`. Java invoking this
  Go code (via process exec or a future RPC) is a `backend-java`-side change,
  out of scope here — see "Integration dependency on Java" in the final
  report and [ACCEPTANCE.md](ACCEPTANCE.md).
- It does not add authentication to the evidence-ingest path — that gap
  pre-exists this change (see `agents-go/GO_PHASE1_INTEGRATION_CONTRACT.md`)
  and is unchanged by P01.
- It does not introduce a fifth "kind" of technical collector — it only adds
  an approved, versioned way to invoke the five that already exist.
