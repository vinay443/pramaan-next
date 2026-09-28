# Go Phase 1 Component Map

Scope: `agents-go/` only. Exact packages/types as they exist today.

## Package inventory

### `cmd/agent` (main package)

| File | Role |
|---|---|
| `main.go` | CLI entrypoint. Subcommands: `describe`, `checks`, `run [-targets f.json] [-submit]`. No server, no listener. |

### `internal/agent` — collector contract, registry, run pipeline

No GRC business rules, no scheduler logic, no API server (per package doc).

| Type / func | File | Purpose |
|---|---|---|
| `Collector` interface | `collector.go` | `Type()`, `Technologies()`, `Checks()`, `Supports(Target) bool`, `Collect(CollectionContext, Target) Result` — every collector implements this |
| `Target` | `collector.go` | `{Kind, Name, Application, Technology, Address string; Params map[string]string}` — the only input shape into a collector |
| `Facts` | `collector.go` | `map[string]string`, keyed data a collector extracts before running checks |
| `Finding` | `collector.go` | one check's outcome: `CheckID, ControlID, Framework, Title, Status, Observed, Expected, Detail` |
| `Evidence` | `collector.go` | one evidence bundle a collector produces (content + metadata + tags) |
| `Result` | `collector.go` | one collector run's output: `Target, Simulated, Facts, Findings, Evidence, StartedAt, EndedAt, Err` |
| `Check` | `checks.go` | `{ID, ControlID, Framework, Title, Description, Expected string; Eval func(Facts) (status, observed, detail string)}` — the fixed, versioned unit of evaluation |
| `RunChecks`, `Summarize`, `Expect`, `ExpectOneOf`, `DaysUntil` | `checks.go` | check-evaluation helpers, pure functions over `Facts` |
| `Registry` | `registry.go` | in-process registry of `Collector`s by `Type()`; `Register` panics on duplicate type; `Match(Target)` picks the first collector whose `Supports` returns true |
| `CheckInfo`, `CollectorInfo`, `Descriptor` | `registry.go` | self-describing registration document, produced by `Registry.Describe()`; consumed only by the CLI (`agent describe`) — no backend endpoint accepts it today |
| `Submitter` interface | `runner.go` | `Submit(ctx, []evidence.IngestRequest) (evidence.BulkIngestResponse, error)` — `*evidence.Client` satisfies it |
| `Runner` | `runner.go` | orchestrates: match targets → collect → map evidence → one bulk submit call |
| `RunReport` | `runner.go` | CLI-facing summary of a run: targets, unmatched, results, finding stats, submission outcome |
| `EvidencePayload`, `MarshalPayload` | `runner.go` | canonical JSON shape stored as evidence content (`collector, technology, target, simulated, collectedAt, facts, findings`) |

### `internal/collectors` — the deterministic collectors themselves

| File | Collector type | Technologies | Notes |
|---|---|---|---|
| `os_collector.go` | `os` | `linux` | reads `/etc/os-release`, `/etc/ssh/sshd_config` (or param-overridden paths) |
| `db_collector.go` | `database` | `postgresql`, `mysql` | reads a JSON settings **snapshot file** (`snapshotPath` param) — never connects to a live database, no `database/sql` |
| `middleware_collector.go` | `middleware` | `nginx`, `tomcat` | reads a config file (`configPath` param), string-matches known directives |
| `tls_collector.go` | `tls` | `tls` | **only collector with a live network call** — read-only `tls.Dial` with a 5s timeout, `InsecureSkipVerify` for inspection only |
| `governance_collector.go` | `governance` | `governance` | reads a JSON export file (`governancePath` param) — Phase 2 |
| `catalog.go` | — | — | `All()`, `Register(*agent.Registry)`, `AllChecks()` — wires all 5 collectors |
| `common.go` | — | — | shared helpers: `evidenceFor` (groups findings into evidence bundles by framework+control), `sourceSystem`, `readFileMaybe` |
| `simulation.go` | — | — | deterministic synthetic fixtures for every collector — no network, no exec |
| `targets.go` | — | — | `SimulationTargets()` (11 hardcoded targets), `ParseTargets([]byte)` |

Every collector's real-mode data source is either (a) an operator-provided
local file path, scoped via a named `Target.Params` key specific to that
collector, or (b) — TLS only — a read-only handshake. There is no generic
query or command capability.

### `internal/evidence` — wire types + HTTP client to the Java backend

| Type / func | File | Purpose |
|---|---|---|
| `IngestRequest`, `IngestResult`, `BulkError`, `BulkIngestResponse`, `ErrorBody` | `model.go` | wire types that **must match** `contracts/openapi/pramaan-backend.yaml` (`IngestRequest`/`IngestResult`/`BulkIngestResponse`/`ErrorBody` schemas) — package doc explicitly forbids adding fields not in the contract |
| `Client` | `client.go` | `BaseURL, HTTP, DryRun, Logger, captured` |
| `Client.Ingest` | `client.go` | `POST {BaseURL}/api/v1/evidence/ingest` |
| `Client.SubmitBulk` / `Client.Submit` | `client.go` | `POST {BaseURL}/api/v1/evidence/bulk`; `Submit` satisfies `agent.Submitter` |
| `Client.post` | `client.go` | shared HTTP mechanics — `Content-Type`/`Accept` headers only, **no auth header**, 1MB-limited response read, decodes `ErrorBody.Message` on non-2xx |

### `internal/config` — env-driven configuration

| Env var | Default | Field |
|---|---|---|
| `SIMULATION_MODE` | `false` | `SimulationMode` |
| `PRAMAAN_BACKEND_URL` | `http://localhost:8080` | `BackendBaseURL` |
| `AGENT_SUBMIT_DRY_RUN` | `= SimulationMode` | `SubmitDryRun` |
| `AGENT_ID` | `agent-local` | `AgentID` |
| `AGENT_NAME` | hostname, else `AGENT_ID` | `AgentName` |
| `AGENT_ENV` | `dev` | `Environment` |
| `AGENT_COLLECTED_BY` | `pramaan-agent` | `CollectedBy` |
| `AGENT_HTTP_TIMEOUT` | `15s` | `HTTPTimeout` |

No feature-flag framework beyond this — env vars with `strEnv`/`boolEnv`/`durEnv`
helpers that fall back to defaults on missing/invalid values.

## Test coverage map

| Test file | What it actually covers |
|---|---|
| `internal/agent/registry_test.go` | register/match/describe happy path; duplicate-registration panic |
| `internal/agent/runner_test.go` | evidence→`IngestRequest` mapping correctness, `CollectedAt` formatting, base64 round-trip, bulk submit wiring |
| `internal/collectors/collectors_test.go` | per-collector simulation-mode pass paths (os, database, middleware, tls); graceful degradation when real-mode has no source file; catalog registers all 5 collectors with ≥15 checks |
| `internal/collectors/governance_collector_test.go` | governance simulation pass path, control→framework mapping, a failing scenario driven by a temp JSON file via `governancePath`, graceful degradation with no source |
| `internal/collectors/mysql_collector_test.go` | MySQL vs Postgres technology gating within the shared `database` collector |
| `internal/config/config_test.go` | dry-run default derivation from `SIMULATION_MODE`, explicit override precedence, non-sim defaults |
| `internal/evidence/client_test.go` | wire-format exactness against contract field names (decoded as `map[string]any`, not just the typed struct), dry-run makes no network call, server error messages surface verbatim |

Build/test: `go build ./... && go test ./...` (module is stdlib-only — no
`go.sum`, no third-party imports anywhere in `agents-go`).
