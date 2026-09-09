# agents-go

Go 1.23. Collectors for Pramaan Next — Phase 1 technical hardening (OS, database,
middleware, TLS) plus the Phase 2 governance collector. **No business rules, no
scheduler business logic, no API server, no frontend** — collectors gather
signals, predefined checks turn them into deterministic PASS/FAIL findings, and
the evidence client submits them to the backend per
`../contracts/openapi/pramaan-backend.yaml`. Completeness / compliance / reuse /
summaries stay entirely backend-side.

## Components

| Piece | Package | Purpose |
|---|---|---|
| Agent registration | `internal/agent` (`Registry`, `Descriptor`) | in-process registry of collectors + a self-describing registration document (`agent describe`) |
| Collector contract | `internal/agent` (`Collector`, `Check`, `Facts`, `Finding`, `Evidence`) | interface every collector implements; predefined-check framework |
| Server / OS collector | `internal/collectors/os_collector.go` | linux — os-release, sshd hardening, audit logging |
| Database collector | `internal/collectors/db_collector.go` | postgresql — TLS, audit, password hashing, pg_hba (from a settings snapshot; no driver bundled) |
| Middleware collector | `internal/collectors/middleware_collector.go` | nginx / tomcat — TLS versions, banner suppression, HSTS, auto-deploy |
| TLS / certificate collector | `internal/collectors/tls_collector.go` | live `tls.Dial` inspection — protocol version, expiry, key strength, self-signed |
| Governance collector (Phase 2) | `internal/collectors/governance_collector.go` | change-management + secure-SDLC — approved change tickets (`ITPP-CHG-02`), enforced peer review (`DPSC-SDLC-04`), SAST/dependency-scan build gates (`PCI-DSS-6.2`), from a JSON governance export (`governancePath` param) |
| Predefined checks | `internal/collectors` (`AllChecks`) + each collector's `Checks()` | 19 checks across the five collectors |
| Evidence submission client | `internal/evidence` (`Client`) | `POST /api/v1/evidence/ingest` and `/bulk`; contract-exact request types; dry-run mode |
| Run pipeline | `internal/agent` (`Runner`) | match targets → collect → map `Evidence` to `IngestRequest` → bulk submit |

## SIMULATION_MODE

`SIMULATION_MODE=true`:

- every collector reads deterministic synthetic sources instead of real servers;
- the evidence client defaults to **dry-run** (logs, no HTTP) — override with
  `AGENT_SUBMIT_DRY_RUN=false` or `agent run -submit`.

So development needs no bank servers and no running backend.

## CLI

```bash
SIMULATION_MODE=true go run ./cmd/agent describe        # registration document
SIMULATION_MODE=true go run ./cmd/agent checks          # predefined check catalog
SIMULATION_MODE=true go run ./cmd/agent run             # collect 10 sim targets, dry-run submit
go run ./cmd/agent run -targets targets.json -submit    # real targets + real submission
```

Target file: JSON array of `{kind,name,application,technology,address,params}`.

## Config (env)

| Var | Default |
|---|---|
| `SIMULATION_MODE` | `false` |
| `PRAMAAN_BACKEND_URL` | `http://localhost:8080` |
| `AGENT_SUBMIT_DRY_RUN` | `true` when `SIMULATION_MODE`, else `false` |
| `AGENT_ID` / `AGENT_NAME` / `AGENT_ENV` | `agent-local` / hostname / `dev` |
| `AGENT_COLLECTED_BY` | `pramaan-agent` |
| `AGENT_HTTP_TIMEOUT` | `15s` |

## Test

```bash
go test ./...
```

Pure Go, standard library only — no Docker, no network, no backend.
