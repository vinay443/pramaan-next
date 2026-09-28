# Go Phase 1 Architecture

Scope: `agents-go/` only. This document describes what exists today, verified by
reading the code — it does not propose new endpoints, servers, or protocols
that aren't already present.

## What agents-go is

A **CLI/batch process** (`cmd/agent`), not a service. It has no listener of
any kind — no `net/http` server, no gRPC server, no router. It:

1. builds a `Registry` of deterministic collectors (`internal/collectors`),
2. matches a list of `Target`s against them,
3. runs each matched collector's fixed `Check`s against facts it gathers,
4. maps resulting `Evidence` to the OpenAPI-contract `IngestRequest` shape,
5. POSTs them to the Java backend's existing `/api/v1/evidence/ingest` /
   `/bulk` endpoints (`internal/evidence.Client`).

Java does not invoke Go today. There is no reverse call: no
`ProcessBuilder`/`exec` of the Go binary from `backend-java`, and no RPC
server for Java to call into. The only cross-service traffic is Go calling
out to Java over HTTP, using an endpoint Java already implements today for
other purposes.

A `.proto` service (`contracts/proto/agent.proto`, `AgentCollector` with
`Describe`/`RunChecks` RPCs) is defined but **not implemented anywhere** — no
generated stubs, no gRPC server, no gRPC client, in either `agents-go` or
`backend-java`. Treat it as an aspirational contract stub, not existing
integration surface.

## Use cases that require Go

Per `agents-go/README.md` and the collector catalog (`internal/collectors/catalog.go`):

| Phase | Use case | Collector(s) |
|---|---|---|
| Phase 1 | OS/server hardening evidence (sshd config, audit logging, baseline version) | `os` |
| Phase 1 | Database hardening evidence (TLS, audit, password hashing, pg_hba) | `database` (postgresql, mysql) |
| Phase 1 | Middleware hardening evidence (TLS versions, banner suppression, HSTS, autodeploy) | `middleware` (nginx, tomcat) |
| Phase 1 | Certificate/TLS posture evidence (protocol version, expiry, key strength, self-signed) | `tls` (the only collector that makes a live network call — a read-only `tls.Dial`) |
| Phase 2 | Change-management / secure-SDLC governance evidence | `governance` |

These require Go specifically because they need a deployable, dependency-free
binary that can run close to the infrastructure being inspected (a server, a
database host, a reverse proxy) without carrying a JVM or Node runtime. They
do **not** require Go to run inside the Java process or to expose a network
service — a batch/CLI model satisfies every current use case.

## Required vs optional components

**Required for any Phase 1 collection run:**
- `internal/agent` — `Registry`, `Collector` interface, `Check` framework, `Runner`
- `internal/collectors` — at least one collector (`os`, `database`, `middleware`, `tls`)
- `internal/evidence` — `Client` to submit to the backend (or `DryRun` for local/simulated runs)
- `internal/config` — env-driven configuration
- `cmd/agent` — CLI entrypoint

**Optional / Phase 2:**
- `governance` collector — only needed once change-management/secure-SDLC evidence is in scope
- Real submission (`AGENT_SUBMIT_DRY_RUN=false` / `-submit`) — optional for
  local development; `SIMULATION_MODE=true` makes the whole pipeline run
  with zero external dependencies (no bank servers, no running backend)

**Not present, and out of scope unless separately decided:**
- Any Go-side HTTP/gRPC server
- Any mechanism for Java to invoke Go directly (process exec or RPC)
- Any registration endpoint consumption — `agent describe` produces a
  `Descriptor` locally; there is no backend endpoint today that accepts it
  (see `GO_PHASE1_INTEGRATION_CONTRACT.md`)

## Architectural rule: deterministic collectors, not agents

Every collector in `internal/collectors` follows one fixed shape:

> fixed collector type → fixed set of predefined `Check`s → fixed fact keys
> extracted from a snapshot/config file via simple parsing, or (TLS only) a
> read-only TLS handshake.

Confirmed by code inspection, not by convention alone: there is no
`os/exec` import anywhere in the module, no `database/sql` usage, no
dynamic SQL construction, no shell invocation, and no LLM/agent-loop
dependency. The "governance" collector's `governancePath` target parameter
is passed straight to `os.ReadFile` and then `json.Unmarshal`'d into a fixed
Go struct — it is not a general-purpose file or query interface.

Any new Go component in this codebase must preserve this shape:

- No LLMs, no autonomous tool selection, no agent loops.
- No natural-language-to-SQL generation, no arbitrary SQL execution.
- No arbitrary shell execution (no `os/exec`).
- No unrestricted filesystem access — file paths come only from explicit,
  versioned `Target.Params` values scoped to a known collector, never from
  free-form user input.
- No arbitrary commands/queries supplied by a caller at runtime. Every
  operation a collector can perform is a named, versioned `Check` compiled
  into the binary ahead of time.

## Known gaps (documented, not fixed here)

- **No authentication** on the evidence-ingest path in either direction —
  confirmed: `backend-java` has no Spring Security dependency or config at
  all, and `evidence.Client.post` sends only `Content-Type`/`Accept`
  headers, no API key/bearer/mTLS.
- **No correlation-ID convention** exists between Go and Java today.
- **`GET /api/v1/agents`** is defined in the OpenAPI contract but not
  implemented server-side (a backend test asserts it 404s intentionally, so
  the frontend can fall back to `contracts/mock-data/agents.json`). Go's
  local `agent describe` output (`Descriptor`) has no backend endpoint to
  POST to today.
- The `Descriptor`/`CheckInfo` JSON shapes produced by `internal/agent` do
  not exactly match `AgentDescriptorView`/`CheckDefView` in the OpenAPI
  contract (field-name and shape differences — see
  `GO_PHASE1_INTEGRATION_CONTRACT.md`). This is pre-existing, not introduced
  by any change proposed here.

These gaps are recorded so future Go-side work doesn't assume they're
solved. Closing them (auth, correlation IDs, an `/api/v1/agents` endpoint)
requires backend-java changes and is out of scope for a Go-only task —
coordinate with the `backend-java` owner (Teammate 1) per `CLAUDE.md` before
attempting to close any of them.
