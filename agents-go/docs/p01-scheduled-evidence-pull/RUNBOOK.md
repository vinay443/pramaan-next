# P01 Runbook

All commands below were actually run against this branch on 2026-09-28
(Windows, git-bash) to produce the output shown — nothing here is
speculative.

## 1. Build and unit-test (no external dependencies)

```bash
cd agents-go
go build ./...
go vet ./...
go test ./...
```

Expected: all packages `ok`, including the new `internal/job` package (18
test functions). See TEST_REPORT.md for the full run.

## 2. Offline demo — successful collection (simulation mode)

```bash
cd agents-go
SIMULATION_MODE=true AGENT_JOB_SOURCES_PATH=examples/job/sources.json \
  go run ./cmd/agent job -request examples/job/request-os-baseline.json
```

No network, no backend, no real server. Result (`status: SUCCEEDED`, exit
code 0):

```json
{
  "jobId": "demo-job-os-001",
  "correlationId": "demo-correlation-os-001",
  "status": "SUCCEEDED",
  "collectorId": "os-baseline",
  "collectorVersion": "v1",
  "findings": [ /* 4 PASS findings */ ],
  "evidenceCount": 4,
  "outputBytes": 4336,
  "submitted": false,
  "idempotentReplay": false
}
```

`submitted: false` because no `-submit` flag was given — this is
"collect-only" mode, gathering and validating evidence without sending it
anywhere, exactly what a local demonstration needs.

## 3. Offline demo — real (non-simulation) mode, still fully local

`SIMULATION_MODE` is **not** set here — the database collector reads an
actual local JSON snapshot file (`examples/job/fixtures/pg-snapshot-hardened.json`),
demonstrating the "real mode" file-based path with zero network access and
no live database:

```bash
cd agents-go
AGENT_JOB_SOURCES_PATH=examples/job/sources.json \
  go run ./cmd/agent job -request examples/job/request-database-real-mode.json
```

Result: `status: SUCCEEDED`, 4 evidence bundles, all 4 Postgres checks PASS
(and the 3 MySQL checks correctly report NOT_APPLICABLE for a postgres
target — this is `agent.Result.Findings` produced by the existing,
unmodified `DatabaseCollector`).

## 4. Offline demo — source marked unavailable

```bash
cd agents-go
AGENT_JOB_SOURCES_PATH=examples/job/sources.json \
  go run ./cmd/agent job -request examples/job/request-source-unavailable.json
echo "exit code: $?"
```

Result: `status: FAILED`, `error.code: SOURCE_UNAVAILABLE`, `exit code: 1`.
No evidence collected (`evidenceCount: 0`), because source validation
happens before the collector ever runs.

## 5. Writing the result to a file (for a caller/scheduler to pick up)

```bash
go run ./cmd/agent job -request examples/job/request-os-baseline.json \
  -sources examples/job/sources.json -out /tmp/job-result.json
```

`AGENT_JOB_SOURCES_PATH` and `-sources` are interchangeable; `-sources`
overrides the env var when both are given.

## 6. Submitting to a real (or dry-run) backend

```bash
# Against a running backend-java instance:
AGENT_JOB_SOURCES_PATH=examples/job/sources.json PRAMAAN_BACKEND_URL=http://localhost:8080 \
  go run ./cmd/agent job -request examples/job/request-os-baseline.json -submit

# Dry-run (logs the would-be request, makes no HTTP call):
AGENT_JOB_SOURCES_PATH=examples/job/sources.json AGENT_SUBMIT_DRY_RUN=true \
  go run ./cmd/agent job -request examples/job/request-os-baseline.json -submit
```

`-submit` reuses `evidence.NewClient` exactly as `agent run -submit` does —
same backend endpoints (`POST /api/v1/evidence/ingest`/`/bulk`), same
contract types, same dry-run behavior.

## 7. Idempotent retry

Set `AGENT_JOB_IDEMPOTENCY_DIR` explicitly for a demo so it's easy to reset:

```bash
export AGENT_JOB_IDEMPOTENCY_DIR=./tmp-job-store
AGENT_JOB_SOURCES_PATH=examples/job/sources.json \
  go run ./cmd/agent job -request examples/job/request-os-baseline.json   # fresh run
AGENT_JOB_SOURCES_PATH=examples/job/sources.json \
  go run ./cmd/agent job -request examples/job/request-os-baseline.json   # idempotentReplay: true
rm -rf ./tmp-job-store   # clear the cache to force a fresh run again
```

## 8. Feature flag off

```bash
AGENT_JOB_PULL_ENABLED=false AGENT_JOB_SOURCES_PATH=examples/job/sources.json \
  go run ./cmd/agent job -request examples/job/request-os-baseline.json
```

Result: `status: REJECTED`, `error.code: FEATURE_DISABLED`, exit code 1.
Nothing is validated beyond the request's own shape — no catalog lookup, no
source lookup, no collector execution.

## Flags and environment variables

CLI flags (`agent job ...`):

| Flag | Meaning |
|---|---|
| `-request <file>` | required; path to the JSON `JobRequest` |
| `-out <file>` | optional; also write the `JobResult` JSON here |
| `-sources <file>` | optional; overrides `AGENT_JOB_SOURCES_PATH` |
| `-submit` | actually submit collected evidence via `evidence.Client` (default: collect-only) |

Environment variables (all follow the existing `internal/config` pattern —
see `agents-go/README.md`'s table for the pre-existing ones):

| Var | Default | Meaning |
|---|---|---|
| `AGENT_JOB_PULL_ENABLED` | `true` | feature flag gating the whole pipeline |
| `AGENT_JOB_DEFAULT_TIMEOUT` | `15s` | used when a request omits `timeoutSeconds` |
| `AGENT_JOB_MAX_TIMEOUT` | `60s` | hard cap on any request's timeout |
| `AGENT_JOB_MAX_OUTPUT_BYTES` | `1048576` (1 MiB) | global bound when a catalog entry doesn't set its own |
| `AGENT_JOB_IDEMPOTENCY_DIR` | `<TMP>/pramaan-agent-jobs` | where completed job results are cached |
| `AGENT_JOB_SOURCES_PATH` | `""` (use simulation fixtures) | path to the approved source registry JSON |

## Fixtures in `examples/job/`

| File | Purpose |
|---|---|
| `sources.json` | approved source registry for the demos (one entry marked unavailable) |
| `request-os-baseline.json` | happy-path job, works purely in simulation mode |
| `request-database-real-mode.json` | happy-path job exercising real (non-simulation) file-based collection |
| `request-source-unavailable.json` | job referencing the unavailable source, for the `SOURCE_UNAVAILABLE` path |
| `fixtures/pg-snapshot-hardened.json` | synthetic Postgres settings snapshot consumed by the real-mode demo |
