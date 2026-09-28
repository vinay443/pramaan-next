# P01 Architecture

## Where this sits

```
                 (future / out of scope: Java invocation)
Java backend  ───────────────────────────────────────────▶  agent job -request req.json
                                                                     │
                                                                     ▼
                                                        internal/job.Executor.Execute
                                                                     │
                              ┌──────────────────────────────────────┼───────────────────────────────┐
                              ▼                                      ▼                                ▼
                    1. validateShape                    4. Catalog.Lookup(id,version)     7. validateSource(SourceStore)
                    2. feature flag (Config)             5. validateParameters(whitelist)  8. Registry.Match (existing)
                    3. Idempotency.Get (replay)          6. (reject unknown/unsupported)   9. context.WithTimeout + goroutine
                                                                     │
                                                                     ▼
                                                     agent.Runner.Run(ctx, [target])   ◀── REUSED, not reimplemented
                                                       (existing match→collect→map)
                                                                     │
                                                                     ▼
                                                     capturingSubmitter (records IngestRequests)
                                                                     │
                                                     bound-check total bytes ──▶ EXCESSIVE_OUTPUT if over limit
                                                                     │
                                                     e.Submitter.Submit(...)  ◀── evidence.Client, REUSED
                                                                     │
                                                                     ▼
                                                              JobResult (JSON)
```

Java does not invoke Go today (confirmed in `agents-go/GO_PHASE1_ARCHITECTURE.md`
— no exec, no RPC, no listener anywhere in this codebase). This change keeps
that reality: `agent job` is a CLI subcommand, invokable by whatever process
wants to invoke it (a cron entry, a script, eventually a Java `ProcessBuilder`
call) — but no such caller is added here, because that would be a
`backend-java` change and this task is Go-only.

## Reuse, not reimplementation

Per `CLAUDE.md`'s "reuse before creating" rule and the existing Go patterns
found during inspection, `internal/job` builds on top of, and does not
duplicate:

| Existing thing | How `internal/job` uses it |
|---|---|
| `agent.Registry` (already populated by `collectors.Register` at startup) | `Executor.Registry` — the same registry `agent run` uses. A `Catalog` entry's `AgentType`/`Technology` must resolve against a collector already in this registry. |
| `agent.Collector.Collect` | Never called directly by `internal/job` — invoked through `agent.Runner.Run`, so collection + evidence-to-contract mapping is the exact same code path as `agent run`. |
| `agent.Runner` | Executes the one matched target and maps `agent.Evidence` → `evidence.IngestRequest`, exactly as `agent run` does. `internal/job` only wraps it with a timeout/cancellation guard and a capturing submitter. |
| `evidence.Client` / `agent.Submitter` | `Executor.Submitter` is typed as `agent.Submitter` — the real backend client (`evidence.NewClient`) satisfies it unmodified; tests use fakes. |
| `internal/config` conventions (`strEnv`/`boolEnv`/`durEnv`, `Config` struct, doc-commented env var table) | Extended, not replaced: `AGENT_JOB_*` env vars added following the exact same pattern (see `config.go`). |
| Deterministic-collector rule (no `os/exec`, no `database/sql`, fixed file-read params only) | Preserved: `internal/job` adds zero new I/O primitives. The only new file I/O is the source registry (`sources.go`, `os.ReadFile` + `json.Unmarshal`) and the idempotency `FileStore` (`idempotency.go`, `os.ReadFile`/`os.WriteFile` against a job-ID-derived filename that is regex-validated before use). |

Nothing in `internal/job` imports `os/exec`, `database/sql`, or any
templating/expression-evaluation package. `go vet` and `gofmt` are clean.

## Execution flow (in `Executor.Execute`)

1. **`validateShape`** — required-field presence (→ `MALFORMED_REQUEST` if
   missing) and semantic validity of job ID format, correlation ID format,
   environment enum, and non-negative timeout (→ `INVALID_REQUEST`).
2. **Feature flag** (`Config.FeatureEnabled`, from `AGENT_JOB_PULL_ENABLED`)
   — checked before any catalog/source/collector work. Off ⇒ `REJECTED /
   FEATURE_DISABLED`, nothing else runs.
3. **Idempotent replay** (`Idempotency.Get(jobID)`) — if this exact job ID
   already has a stored terminal result, it's returned immediately with
   `idempotentReplay: true`. The collector never runs a second time for the
   same job ID.
4. **Catalog lookup** (`Catalog.Lookup(collectorId, collectorVersion)`) —
   unknown ID ⇒ `UNKNOWN_COLLECTOR`; known ID, wrong version ⇒
   `UNSUPPORTED_COLLECTOR_VERSION`. There is no "latest version" fallback —
   an unrecognized version is always rejected, never silently upgraded or
   downgraded.
5. **Parameter validation** (`validateParameters`) against the matched
   catalog entry's `[]ParamRule` whitelist — any parameter key not on the
   whitelist is rejected outright (`INVALID_PARAMETERS`), not ignored. This
   is the only place a caller's data can reach a collector, and it is a
   closed list, not a pass-through.
6. **Source validation** (`validateSource`) — the source ID must exist in
   the approved `SourceStore`, its `environment` must match the job's, and
   its `technology` must match the collector's (fixed or parameter-selected).
   Unknown source ⇒ `REJECTED / INVALID_SOURCE` (bad request). Known but
   `available: false`, or the source registry itself unreadable ⇒ `FAILED /
   SOURCE_UNAVAILABLE` (an infra condition, not a bad request — safe to
   retry).
7. **Target construction + registry cross-check** — the job's
   `Parameters`/`SourceID` are folded into an `agent.Target`; a defensive
   `Registry.Match` confirms a real collector will actually pick it up (a
   catalog/registry mismatch is an internal wiring bug, reported as `FAILED /
   INTERNAL_ERROR`, never a panic).
8. **Timeout** — `req.timeoutSeconds` (or `Config.DefaultTimeout` if omitted),
   clamped to `Config.MaxTimeout`. `context.WithTimeout` wraps the caller's
   context.
9. **Bounded, cancellable execution** — `agent.Runner.Run` is called inside a
   goroutine; the caller `select`s on the timeout/cancellation context vs. a
   completion channel. Whichever fires first wins:
   - context deadline exceeded ⇒ `TIMEOUT`
   - context cancelled by the caller (e.g. a parent request aborted) ⇒
     `CANCELLED` — distinguished from `TIMEOUT` by inspecting
     `context.Cause`/`ctx.Err()` (`DeadlineExceeded` vs `Canceled`)
   - the collector returns first ⇒ proceed to evaluate its result
10. **Output bound** — total size of the mapped `IngestRequest.ContentBase64`
    (+`ContentText`) is compared against the catalog entry's
    `MaxOutputBytes`, or `Config.GlobalMaxOutputBytes` if unset. Over the
    limit ⇒ `FAILED / EXCESSIVE_OUTPUT`, and **nothing is submitted** — the
    bound is enforced before the evidence ever reaches the submitter.
11. **Collector error** — if the underlying `agent.Result.Err` is non-empty
    (e.g. an unreadable snapshot file), the job reports `FAILED /
    COLLECTOR_ERROR` with that message. It never panics or crashes the
    process on a collector-level failure.
12. **Submission** (optional) — if `Executor.Submitter` is configured, the
    exact `IngestRequest`s already produced by `agent.Runner` (via the
    capturing submitter) are submitted once, directly — no second collection
    pass. A submission failure is `FAILED / EVIDENCE_SUBMIT_FAILED`; the
    findings/evidence-count already computed are still returned, so a caller
    can see collection succeeded even though delivery failed.
13. **Idempotency store write** — only for the three *deterministic* terminal
    outcomes (`SUCCEEDED`, `FAILED`, `REJECTED`). `TIMEOUT` and `CANCELLED`
    are never cached, because they describe this attempt's execution
    environment, not a property of the request — a retry with the same job
    ID should genuinely re-attempt rather than replay a stale timeout.

## Why collection success ≠ compliance pass

`StatusSucceeded` means the approved collector ran and evidence was
gathered — it says nothing about whether individual `Finding`s are PASS or
FAIL. That judgment already belongs to the backend/rules layer in the
existing design (see `agents-go/README.md`: "Completeness / compliance /
reuse / summaries stay entirely backend-side"). P01 does not change that
separation of concerns.

## Safety boundary (explicitly verified, not assumed)

- No `os/exec` anywhere in `internal/job` (or the rest of `agents-go`).
- No `database/sql` anywhere in `internal/job`.
- No parameter value is ever interpolated into a file path template, SQL
  string, or shell string — parameter values are only ever looked up as
  fixed map entries (`Target.Params[key]`) inside the pre-existing,
  hard-coded collector logic in `internal/collectors`.
- Every parameter key a job can set is enumerated ahead of time in
  `DefaultCatalog()` (`catalog.go`) — an unlisted key is a hard rejection,
  not a warning.
- Every collector/version a job can invoke is enumerated ahead of time in
  `DefaultCatalog()` — there is no dynamic collector resolution, reflection,
  or plugin loading.
- No LLM dependency, no natural-language interpretation of any field, no
  autonomous decision about which collector to run — the caller names the
  exact `(collectorId, collectorVersion)` pair, and that's the only thing
  that runs.
- The idempotency `FileStore` uses the job ID as a filename; `validateShape`
  enforces `^[A-Za-z0-9][A-Za-z0-9_-]{0,99}$` on the job ID *before* it is
  ever used as a path component, which rules out path traversal
  (`../`, absolute paths, null bytes, separators) by construction.

## Known limitation: cooperative cancellation

`internal/job`'s timeout/cancellation is enforced at the *job* layer (the
`select` in step 9), not inside individual collectors — none of the existing
Phase 1 collectors check `ctx.Done()` mid-collection (confirmed during
inspection: file reads and the TLS dial in `internal/collectors` don't
observe context cancellation). This means:

- The job *result* is returned promptly at the timeout/cancellation boundary
  (verified by `TestTimeoutReturnsPromptly`, which asserts the call returns
  well before the fake collector's artificial delay elapses).
- The underlying goroutine running the actual collector may keep running in
  the background briefly after the job reports `TIMEOUT`/`CANCELLED`, until
  its blocking operation (a bounded file read or a TLS dial with its own 5s
  internal timeout) completes on its own. It is discarded, not leaked
  indefinitely — every current collector's blocking operation is
  self-bounded.

This is documented here rather than silently assumed away; see
ACCEPTANCE.md for how this maps to the P01 requirement text.
