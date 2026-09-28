# Go Phase 1 Integration Contract

Scope: `agents-go/` ⇄ `backend-java/` boundary, as it exists today. Every
claim here is backed by reading both codebases — nothing here assumes an
endpoint, header, or field that isn't actually implemented.

## How Java is expected to invoke Go

**It doesn't, today.** There is no process-exec of the Go binary from
`backend-java`, and no RPC server on the Go side for Java to call. The
`AgentCollector` gRPC service in `contracts/proto/agent.proto` is a stub
only — no generated code, no server, no client, on either side.

The only integration that exists is the reverse direction: **Go calls
Java**, over plain HTTP, using two endpoints Java already implements for
general evidence ingestion (`EvidenceController` in `backend-java`):

- `POST {PRAMAAN_BACKEND_URL}/api/v1/evidence/ingest`
- `POST {PRAMAAN_BACKEND_URL}/api/v1/evidence/bulk`

These are not Go-specific endpoints — they're the same ones any evidence
producer (human upload, another integration) would use. Go is a client of
a general-purpose API, not a service Java dispatches work to.

If synchronous Java→Go invocation is wanted in the future (e.g. "run this
collector now, on demand"), that is a **new capability that does not exist
today** and would need: a new contract (REST or the existing but unbuilt
gRPC stub), a decision on where it runs (long-lived Go service vs. spawned
CLI process), and sign-off from both the `backend-java` and `agents-go`
owners per `CLAUDE.md`. Do not assume it exists when planning further Go
work.

## Input contract (Java → Go, or operator → Go)

Go is driven by a `Target` list, supplied either as a `-targets file.json`
CLI argument or via `SimulationTargets()` in simulation mode. There is no
network input into Go — no HTTP request ever reaches `agents-go`.

```json
{
  "kind": "database",
  "name": "payments-db-1",
  "application": "payments",
  "technology": "postgresql",
  "address": "10.0.0.5:5432",
  "params": {
    "snapshotPath": "/var/pramaan/snapshots/payments-db-1.json"
  }
}
```

`Target.Params` is the **only** place external data reaches a collector's
"real mode" (non-simulation), and it is always a fixed, named key per
collector (`snapshotPath`, `configPath`, `osReleasePath`, `sshdConfigPath`,
`auditStatusPath`, `governancePath`) pointing at a local file. There is no
free-form command, query, or shell string accepted anywhere.

## Output contract (Go → Java)

Go maps each `agent.Evidence` to the contract's `IngestRequest`
(`internal/evidence/model.go`), field-for-field identical to
`contracts/openapi/pramaan-backend.yaml`'s `IngestRequest` schema:

| Field | Notes |
|---|---|
| `applicationSlug` | required |
| `controlId` | required |
| `framework` | required |
| `sourceSystem` | required, `AGENT_<COLLECTOR>[_<TECH>]` |
| `sourceObjectId` | optional, `<target.Name>/<framework>|<controlId>` |
| `title` | optional |
| `contentType` | optional |
| `contentBase64` | optional — evidence payload, base64-encoded |
| `contentText` | optional — unused by Go today (Go always sends base64) |
| `collectedAt` | optional, RFC3339 UTC |
| `collectedBy` | optional, from `AGENT_COLLECTED_BY` config |
| `metadata` | optional, string map |
| `tags` | optional, string map |

Response: `IngestResult` (single) or `BulkIngestResponse` (bulk — Go always
uses bulk via `Runner.Run`), both matching the OpenAPI schemas exactly.

**Known contract drift, not fixed by this document:**
- `agent.CheckInfo.ID` serializes as JSON key `"id"`; the OpenAPI
  `CheckDefView` schema calls the same concept `checkId`. This only affects
  local `agent describe`/`agent checks` CLI output — nothing consumes it
  over the network today.
- `agent.Descriptor` (`Collectors []CollectorInfo`, plus `TotalChecks`,
  `GeneratedAt`) does not match the shape of the OpenAPI
  `AgentDescriptorView` schema (`collectors []string` + top-level `checks
  []CheckDefView`). Irrelevant in practice because `GET /api/v1/agents` is
  not implemented server-side — confirmed by a backend test
  (`ErrorHandlingWebTest`) that asserts it intentionally 404s so the
  frontend falls back to `contracts/mock-data/agents.json`.

Any future work to register agents with the backend must reconcile this
drift and requires a `backend-java` change — out of scope for Go-only work.

## Error contract

- HTTP status ≥300 from Java: `evidence.Client.post` attempts to decode the
  body as the contract's `ErrorBody` (`timestamp, status, error, message`)
  and surfaces `.Message` in the returned Go `error`; if decoding fails, the
  raw response body text is surfaced instead.
- Network/transport failure (backend unreachable, timeout): surfaced as a
  Go `error` from `http.Client.Do`, wrapped by `Runner.Run` as
  `fmt.Errorf("submit evidence: %w", err)`.
- Partial bulk failure: `BulkIngestResponse.errors[]` (`{index, message}`)
  — per-item failures inside an otherwise-successful bulk call are not Go
  errors, they're data in the response the caller (CLI) must inspect.
- A collector's own failure to gather facts (e.g. unreadable snapshot file
  in real mode) does **not** abort the run — it sets `Result.Err` and the
  collector still runs its checks against zero-valued facts (checks then
  naturally evaluate to `ERROR`/`FAIL` via `Expect`'s empty-string handling).
  This is deliberate graceful degradation, confirmed by
  `TestRealModeWithoutSourcesDegradesGracefully` and the governance
  collector's equivalent test.

## Timeout / cancellation behavior

- `evidence.Client` HTTP calls: bounded by `Options.Timeout`, sourced from
  `AGENT_HTTP_TIMEOUT` (default 15s), applied as `http.Client.Timeout`.
- `tls_collector.go`: the only collector with its own independent timeout —
  `DialTimeout` (5s), applied to the `tls.Dialer` for live certificate
  inspection.
- `Runner.Run` accepts a `context.Context` and threads it through to both
  collection (`CollectionContext.Ctx`) and submission (`Submitter.Submit`),
  but no collector currently checks `ctx.Done()` mid-collection — file
  reads and the TLS dial are the only blocking operations, and none observe
  cancellation explicitly today. This is a latent gap, not a designed
  behavior — do not assume mid-collection cancellation works.
- There is no retry logic anywhere in `agents-go` — a failed submit returns
  an error once; the CLI does not re-attempt.

## Correlation ID behavior

**None exists today.** Confirmed: no `correlation`, `X-Request-Id`,
`X-Correlation-Id`, `traceId`, or MDC usage anywhere in `agents-go` or
`backend-java`. `evidence.Client.post` sends only `Content-Type` and
`Accept` headers. If correlation across the Go→Java boundary is needed
later, it must be designed and added to both sides — this is a gap to be
filled, not a convention to be followed.

## Idempotency expectations

Handled entirely server-side, by contract: the OpenAPI `IngestResult.outcome`
enum (`CREATED`, `NEW_VERSION`, `DUPLICATE`) indicates how the backend
deduplicates submissions — Go has no dedup logic of its own and simply
reports whatever outcome the backend returns per item. Go does not compute
or send any client-side idempotency key; presumably the backend derives
duplication from the evidence content/control/source-object identity
(`sourceSystem` + `sourceObjectId` + content), but that logic lives in
`backend-java` and is out of scope for this Go-only document to describe
further.

## Security boundaries

- **No authentication exists today, either direction.** Confirmed:
  `backend-java` has no Spring Security dependency at all (`pom.xml` has no
  `spring-security-*` artifact), no `SecurityConfig`, no `@PreAuthorize`,
  no API-key filter on `/api/v1/evidence/ingest` or `/bulk`. `evidence.Client`
  sends no `Authorization`/API-key/client-cert. Treat the current setup as
  **local-network-trust only** — do not expose the backend's evidence
  endpoints or run the Go agent binary against anything beyond a trusted
  local/dev network without first adding real authentication on the Java
  side (out of scope for Go-only work; coordinate with Teammate 1).
- Go-side execution boundary (see `GO_PHASE1_ARCHITECTURE.md`): no
  `os/exec`, no `database/sql`, no dynamic file paths beyond named
  `Target.Params` keys scoped per collector. This is the actual security
  boundary Go enforces today, and any new collector must preserve it.
- The TLS collector's live dial uses `InsecureSkipVerify: true` — explicitly
  for inspection only (`//nolint:gosec // inspection only` in source), never
  used to establish a trusted connection for data exchange.

## Test strategy

Existing (see `GO_PHASE1_COMPONENT_MAP.md` for the full map):

- **Unit tests per package**, stdlib `testing` only, no external services —
  `go test ./...` runs with no Docker, no network, no backend, confirmed by
  the module having zero third-party dependencies.
- **Contract-exactness test**: `evidence/client_test.go`'s
  `TestSubmitBulkMatchesContract` decodes the outbound wire body as a generic
  `map[string]any` and asserts exact contract field names — this is the
  pattern to extend for any new field or collector output.
- **Simulation-mode coverage**: every collector has a deterministic
  simulation-mode test asserting exact PASS/FAIL/N-A outcomes.
- **Graceful-degradation coverage**: every real-mode collector has a test
  for the missing-source case (no panic, `Result.Err` set, checks still run).

For any new Go component, follow this existing pattern: table-style or
discrete `Test*` functions per scenario (this codebase does not use `t.Run`
sub-test tables — stay consistent with what's here), a simulation-mode
happy path, a real-mode degradation path, and — if the component touches
the wire contract — a generic-map decode assertion against exact contract
field names. Run only the focused package's tests for a scoped change
(`go test ./internal/<package>/...`), not the full suite, per `CLAUDE.md`.
