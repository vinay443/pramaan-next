# P01 API / Contract

This is a Go-internal contract (types in `internal/job`), not a network API —
`agents-go` still has no listener (see `agents-go/GO_PHASE1_ARCHITECTURE.md`).
The CLI (`agent job`) reads a request as a JSON file and writes a result as
JSON to stdout (and optionally to a file via `-out`). A future Java-side
invocation would use the same two JSON shapes, however it chooses to invoke
the binary — that invocation mechanism is not implemented here.

## Input contract — `JobRequest` (`internal/job/types.go`)

```json
{
  "jobId": "string, required, ^[A-Za-z0-9][A-Za-z0-9_-]{0,99}$",
  "applicationId": "string, required",
  "environment": "string, required, one of: dev | test | staging | production",
  "sourceId": "string, required — must exist in the approved source registry",
  "collectorId": "string, required — must exist in the approved Catalog",
  "collectorVersion": "string, required — must match an approved version of collectorId",
  "parameters": { "string": "string, optional — whitelisted per collectorId+version" },
  "timeoutSeconds": "int, optional, must be >= 0 (0 = use the default timeout)",
  "correlationId": "string, required, ^[A-Za-z0-9][A-Za-z0-9_.:-]{0,199}$"
}
```

Every field on this struct is exactly what the P01 spec asked for: job ID,
application ID, environment, source ID, approved collector/query ID,
collector/query version, validated parameters, timeout, correlation ID.

## Approved catalog (`internal/job/catalog.go`, `DefaultCatalog()`)

| `collectorId` | `collectorVersion` | Underlying `agent.Collector` | Required parameters | Optional parameters |
|---|---|---|---|---|
| `os-baseline` | `v1` | `os` / `linux` | — | `name`, `address`, `osReleasePath`, `sshdConfigPath`, `auditStatusPath` |
| `database-hardening` | `v1` | `database` / `postgresql` or `mysql` | `technology` (enum: `postgresql`, `mysql`), `snapshotPath` | `name`, `address` |
| `middleware-hardening` | `v1` | `middleware` / `nginx` or `tomcat` | `technology` (enum: `nginx`, `tomcat`), `configPath` | `name`, `address` |
| `tls-posture` | `v1` | `tls` | `address` | `name` |
| `governance-sdlc` | `v1` | `governance` | `governancePath` | `name`, `address` |

Any parameter key not listed for the requested `(collectorId, version)` is
rejected. Any `collectorId`/`collectorVersion` not listed above is rejected.
This catalog is compiled into the binary — extending it is a code change
(and a version bump if an existing ID's contract changes), never a runtime
configuration a caller can influence.

## Output contract — `JobResult` (`internal/job/types.go`)

```json
{
  "jobId": "string — echoed from the request",
  "correlationId": "string — echoed from the request",
  "status": "SUCCEEDED | FAILED | REJECTED | TIMEOUT | CANCELLED",
  "collectorId": "string",
  "collectorVersion": "string",
  "error": {
    "code": "see Error contract below",
    "message": "string — human-readable, never echoes raw parameter values or evidence content"
  },
  "findings": [ "agent.Finding — same shape agent run already produces" ],
  "evidenceCount": "int — number of evidence bundles collected",
  "outputBytes": "int — total encoded evidence size measured against the bound",
  "submitted": "bool — true only if evidence was actually POSTed to the backend",
  "idempotentReplay": "bool — true if this result was served from cache, not re-executed",
  "startedAt": "RFC3339 timestamp",
  "endedAt": "RFC3339 timestamp",
  "durationMs": "int64"
}
```

`error` is present only when `status != SUCCEEDED`. `job ID` and
`correlation ID` are always present on every result, including rejections —
so a caller can always correlate a result back to its request and to logs
(the CLI's `slog` lines are keyed the same way).

## Error contract — `ErrorCode` (`internal/job/types.go`)

| Code | `status` | Meaning | Retry guidance |
|---|---|---|---|
| `MALFORMED_REQUEST` | `REJECTED` | Required field missing, or the request body wasn't valid JSON | Fix the request; retrying unchanged will repeat the same rejection (and idempotency will replay it) |
| `INVALID_REQUEST` | `REJECTED` | A field is present but semantically invalid (bad job ID format, unknown environment, negative timeout) | Fix the request |
| `FEATURE_DISABLED` | `REJECTED` | `AGENT_JOB_PULL_ENABLED=false` | Retry once the flag is re-enabled |
| `UNKNOWN_COLLECTOR` | `REJECTED` | `collectorId` is not in the approved catalog | Use an approved ID |
| `UNSUPPORTED_COLLECTOR_VERSION` | `REJECTED` | `collectorId` is approved but not at this `collectorVersion` | Use an approved version |
| `INVALID_PARAMETERS` | `REJECTED` | A parameter key isn't whitelisted, a required one is missing, or an enum value is unsupported | Fix the parameters |
| `INVALID_SOURCE` | `REJECTED` | `sourceId` isn't in the approved registry, or its environment/technology doesn't match the job | Use an approved, matching source |
| `SOURCE_UNAVAILABLE` | `FAILED` | The source is approved but currently marked unavailable, or the source registry itself couldn't be read | Safe to retry later |
| `COLLECTOR_ERROR` | `FAILED` | The underlying collector reported an error (e.g. unreadable snapshot/config file) | Safe to retry after fixing the underlying source data |
| `EXCESSIVE_OUTPUT` | `FAILED` | Collected evidence exceeded the configured size bound; nothing was submitted | Not safe to blindly retry with the same parameters — the source data itself is oversized |
| `TIMEOUT` | `TIMEOUT` | The collector didn't finish within the effective timeout | Safe to retry (not cached — a fresh attempt will actually re-run) |
| `CANCELLED` | `CANCELLED` | The caller's context was cancelled before completion | Safe to retry (not cached) |
| `EVIDENCE_SUBMIT_FAILED` | `FAILED` | Collection succeeded but the backend rejected/couldn't be reached for submission | Safe to retry — collection was fine, only delivery failed |
| `INTERNAL_ERROR` | `FAILED` | A catalog entry has no matching registered collector (a wiring bug, not a caller error) | Report to the Go collector owner; not a caller-fixable condition |

## Timeout / cancellation behavior

- Effective timeout = `req.timeoutSeconds` if `> 0`, else
  `AGENT_JOB_DEFAULT_TIMEOUT` (default 15s); clamped to at most
  `AGENT_JOB_MAX_TIMEOUT` (default 60s) regardless of what the request asks
  for.
- The job result is returned promptly at whichever boundary fires first —
  timeout, caller cancellation, or collector completion (see
  ARCHITECTURE.md's "Known limitation" section for the one caveat: the
  underlying collector goroutine may finish its own bounded I/O shortly
  after, in the background, without affecting the result already returned).

## Idempotency contract

- Keyed by `jobId` alone (not by content hash) — the P01 spec calls for a
  job ID; re-submitting the *same* job ID is treated as a retry of the same
  logical job, not a new one.
- `SUCCEEDED`, `FAILED`, and `REJECTED` results are cached and replayed
  verbatim (`idempotentReplay: true`) on any later call with the same job
  ID — the collector is never invoked twice for one job ID.
- `TIMEOUT` and `CANCELLED` are never cached — see ARCHITECTURE.md for why.
- Persistence: `internal/job.FileStore` (one JSON file per job ID, directory
  from `AGENT_JOB_IDEMPOTENCY_DIR`), so replay works across separate CLI
  invocations, not just within one process. `internal/job.MemoryStore` is
  available for embedding this package directly in a longer-lived process.

## Logging contract

Every `Execute` call logs exactly one structured line per phase reached
(`slog`, same convention as `evidence.Client`), always including `jobId` and
`correlationId`. Parameter *values* are never logged — only parameter
*keys* (`paramKeys` in `redact.go`) — and the final `JobError.Message` is
built from fixed, code-authored strings plus the caller-controlled field
*names* (not values) that failed validation, so it cannot echo a secret a
caller mistakenly put in a parameter value (verified by
`TestJobErrorNeverContainsRawParameterValues`).
