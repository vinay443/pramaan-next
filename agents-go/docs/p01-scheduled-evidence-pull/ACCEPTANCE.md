# P01 Acceptance Checklist

Requirement-by-requirement mapping to what was actually built and verified.
"Where" points at exact files; "Verified by" points at an actual test or
executed demo (see TEST_REPORT.md / RUNBOOK.md), not an assumption.

## Input contract

| Required field | Present in `JobRequest`? |
|---|---|
| job ID | yes — `JobID` |
| application ID | yes — `ApplicationID` |
| environment | yes — `Environment` |
| source ID | yes — `SourceID` |
| approved collector/query ID | yes — `CollectorID` |
| collector/query version | yes — `CollectorVersion` |
| validated parameters | yes — `Parameters map[string]string`, validated in `validateParameters` |
| timeout | yes — `TimeoutSeconds` |
| correlation ID | yes — `CorrelationID` |

## Numbered requirements

1. **Validate the incoming request.** → `validateShape` (`validate.go`). Verified: `TestMalformedRequestMissingFields`, `TestMalformedRequestJSON`, `TestInvalidRequestBadTimeout`, `TestInvalidRequestUnknownEnvironment`.
2. **Validate the requested collector/query is approved.** → `Catalog.Lookup` (`catalog.go`). Verified: `TestUnknownCollectorRejected`.
3. **Validate collector/query version.** → same `Catalog.Lookup`, `LookupUnsupportedVersion`. Verified: `TestUnsupportedVersionRejected`.
4. **Validate parameters against the approved contract.** → `validateParameters` against `ApprovedCollector.Params` whitelist. Verified: `TestInvalidParametersUnknownKey`, `TestInvalidParametersMissingRequired`.
5. **Validate the target/source.** → `Executor.validateSource` against `SourceStore`. Verified: `TestInvalidSourceUnknownID`, `TestSourceUnavailable`, `TestSourceRegistryUnreadableIsSourceUnavailable`.
6. **Reject unsupported or unknown collectors.** → same as #2/#3; a catalog miss never falls through to execution.
7. **Execute only the approved deterministic collector.** → the built `agent.Target.Kind`/`Technology` come only from the matched `ApprovedCollector`, run through the existing `agent.Registry`/`agent.Collector` (no dynamic dispatch beyond that fixed lookup).
8. **Support context cancellation.** → `Executor.Execute`'s `select` on `runCtx.Done()` with `context.Canceled` detection. Verified: `TestContextCancellationDistinctFromTimeout`.
9. **Enforce execution timeout.** → `context.WithTimeout` clamped to `Config.MaxTimeout`. Verified: `TestTimeoutReturnsPromptly` (asserts prompt return, not just eventual timeout).
10. **Enforce bounded result/output size.** → post-collection byte-count check against `ApprovedCollector.MaxOutputBytes`/`Config.GlobalMaxOutputBytes`, before submission. Verified: `TestExcessiveOutputRejectedBeforeSubmission` (also asserts nothing reaches the submitter).
11. **Return structured success/failure results.** → `JobResult`/`JobError` (`types.go`), always returned, never a panic. Verified: every test in the suite inspects a returned `JobResult`.
12. **Include job ID and correlation ID in results/logging.** → `JobResult.JobID`/`CorrelationID` always populated (`finish`); every `slog` call includes both. Verified: RUNBOOK.md demo output and TEST_REPORT.md's captured log lines.
13. **Avoid sensitive data in logs.** → `paramKeys` (`redact.go`) logs parameter *names* only, never values; `JobError.Message` never echoes a parameter value. Verified: `TestJobErrorNeverContainsRawParameterValues`.
14. **Handle source-unavailable errors safely.** → `ErrSourceUnavailable` → `StatusFailed`, no panic, no partial evidence submitted. Verified: `TestSourceUnavailable`, `TestSourceRegistryUnreadableIsSourceUnavailable`, and the RUNBOOK's real CLI demo against `request-source-unavailable.json`.
15. **Handle collector errors safely.** → non-empty `agent.Result.Err` → `StatusFailed`/`COLLECTOR_ERROR`, no panic. Verified: `TestCollectorFailureHandledSafely`.
16. **Support safe retry/idempotency behavior where appropriate.** → `Store`/`FileStore`/`MemoryStore` keyed by job ID; deterministic terminal states cached, `TIMEOUT`/`CANCELLED` deliberately not cached so they can be genuinely retried. Verified: `TestDuplicateJobIDIsIdempotent`, plus a real two-invocation CLI demo in RUNBOOK.md/TEST_REPORT.md.
17. **Never execute arbitrary SQL.** → confirmed: no `database/sql` import anywhere in `internal/job` (or the rest of `agents-go` — this was true before this change and remains true).
18. **Never execute arbitrary shell commands.** → confirmed: no `os/exec` import anywhere in `internal/job` (or the rest of `agents-go`).
19. **Never accept arbitrary commands from the caller.** → the only caller-influenced data is `Parameters`, and every key must be pre-listed in `ApprovedCollector.Params` (closed whitelist, `validateParameters`); unlisted keys are hard rejections, not passed through. Verified: `TestInvalidParametersUnknownKey`.
20. **Never dynamically select tools or collectors using an LLM.** → collector selection is a fixed map lookup (`Catalog.byID`) keyed by exactly the `(collectorId, collectorVersion)` string pair the caller supplied — no model, no embeddings, no ranking, no natural-language interpretation of any field.

## "Do NOT add" list

| Item | Status |
|---|---|
| LLM | not added — no LLM/AI SDK dependency anywhere in `agents-go/go.mod` (still stdlib-only, no `go.sum`) |
| AI agent framework | not added |
| autonomous planning | not added — execution order is a fixed, linear sequence of validation steps in `Execute`, not a plan the code constructs at runtime |
| natural-language command interpretation | not added — every field is matched against fixed string constants/regexes, never parsed as free-form instruction |
| arbitrary SQL | not added |
| arbitrary shell execution | not added |

## Testing requirements

All 15 named scenarios (valid request, invalid request, unknown collector
ID, unsupported collector version, invalid parameters, invalid source,
unavailable source, timeout, context cancellation, excessive output,
malformed request, duplicate/idempotent execution, collector failure,
successful collection, feature flag disabled) have a passing test — see
TEST_REPORT.md for the full list and raw output.

## Offline/local demonstration

`examples/job/` (fixtures) + RUNBOOK.md (commands) provide a demonstration
that runs with **zero** production credentials, zero network access, and
(for the simulation-mode demo) zero real infrastructure — both a
`SIMULATION_MODE=true` path and a "real mode reading a local file" path are
demonstrated, both actually executed during this task.

## Documentation required

README.md, ARCHITECTURE.md, CONTRACT.md (the API/CONTRACT doc), RUNBOOK.md,
TEST_REPORT.md, ACCEPTANCE.md (this file), ROLLBACK.md — all present in
`agents-go/docs/p01-scheduled-evidence-pull/`. Synthetic test fixtures are
in `agents-go/examples/job/`.

## Explicitly out of scope (Java/React untouched)

- No file under `backend-java/` or `frontend-react/` was created, modified,
  or deleted by this task.
- Java invoking this Go binary (however that will eventually work — process
  exec, or a future RPC per `contracts/proto/agent.proto`) is **not**
  implemented here; see "Integration dependency on Java" in the final report
  and in README.md.
