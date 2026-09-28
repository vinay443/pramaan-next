# P01 Test Report

All commands and output below were actually executed on this branch on
2026-09-28 (`go1.23`, Windows/git-bash). Nothing in this report is
predicted or assumed — every line of output was produced by a real `go
test` / `go vet` / `go build` / `go run` invocation during this task.

## Commands executed

```bash
cd agents-go
gofmt -l .            # -> no output (clean)
go vet ./...           # -> no output (clean)
go build ./...          # -> no output (clean)
go test ./... -count=1
```

## Full-suite result

```
?   	github.com/pramaan/pramaan-next/agents-go/cmd/agent	[no test files]
ok  	github.com/pramaan/pramaan-next/agents-go/internal/agent	1.547s
ok  	github.com/pramaan/pramaan-next/agents-go/internal/collectors	2.137s
ok  	github.com/pramaan/pramaan-next/agents-go/internal/config	0.982s
ok  	github.com/pramaan/pramaan-next/agents-go/internal/evidence	3.100s
ok  	github.com/pramaan/pramaan-next/agents-go/internal/job	1.927s
```

No pre-existing package regressed. `cmd/agent` has no test files, matching
its state before this change (it's a thin CLI wrapper; its behavior is
exercised via the RUNBOOK's manual `go run` demonstrations instead, also
actually executed — see RUNBOOK.md).

## `internal/job` — every P01-required scenario, with the test that covers it

Required scenario (from the P01 spec) → test function → verified outcome.
All 18 ran and passed in the same `go test -v ./internal/job/...` invocation.

| Required scenario | Test function | Result |
|---|---|---|
| valid request | `TestValidRequestSucceeds` | PASS |
| successful collection (with submission) | `TestSuccessfulCollectionWithSubmission` | PASS |
| malformed request | `TestMalformedRequestMissingFields`, `TestMalformedRequestJSON` | PASS, PASS |
| invalid request | `TestInvalidRequestBadTimeout`, `TestInvalidRequestUnknownEnvironment` | PASS, PASS |
| unknown collector ID | `TestUnknownCollectorRejected` | PASS |
| unsupported collector version | `TestUnsupportedVersionRejected` | PASS |
| invalid parameters | `TestInvalidParametersUnknownKey`, `TestInvalidParametersMissingRequired` | PASS, PASS |
| invalid source | `TestInvalidSourceUnknownID` | PASS |
| unavailable source | `TestSourceUnavailable`, `TestSourceRegistryUnreadableIsSourceUnavailable` | PASS, PASS |
| collector failure | `TestCollectorFailureHandledSafely` | PASS |
| excessive output | `TestExcessiveOutputRejectedBeforeSubmission` | PASS |
| timeout | `TestTimeoutReturnsPromptly` | PASS |
| context cancellation | `TestContextCancellationDistinctFromTimeout` | PASS |
| duplicate / idempotent execution | `TestDuplicateJobIDIsIdempotent` | PASS |
| feature flag disabled | `TestFeatureFlagDisabledRejectsBeforeExecution` | PASS |
| (extra) no secret leakage in error messages | `TestJobErrorNeverContainsRawParameterValues` | PASS |

That's 15/15 required scenarios plus 1 additional safety-focused test, 18
test functions total (some scenarios needed two functions to cover distinct
sub-cases, e.g. two different reasons a source can be unavailable).

## Raw `go test -v ./internal/job/...` output (captured verbatim)

```
=== RUN   TestValidRequestSucceeds
2026/09/28 10:14:09 INFO job succeeded jobId=job-001 correlationId=corr-001 collectorId=fake-collector collectorVersion=v1 evidenceCount=1 outputBytes=16 submitted=false
--- PASS: TestValidRequestSucceeds (0.00s)
=== RUN   TestSuccessfulCollectionWithSubmission
2026/09/28 10:14:09 INFO job succeeded jobId=job-001 correlationId=corr-001 collectorId=fake-collector collectorVersion=v1 evidenceCount=1 outputBytes=16 submitted=true
--- PASS: TestSuccessfulCollectionWithSubmission (0.00s)
=== RUN   TestMalformedRequestMissingFields
2026/09/28 10:14:09 INFO job rejected jobId="" correlationId=corr-001 code=MALFORMED_REQUEST reason="missing required field \"jobId\""
--- PASS: TestMalformedRequestMissingFields (0.00s)
=== RUN   TestInvalidRequestBadTimeout
2026/09/28 10:14:09 INFO job rejected jobId=job-001 correlationId=corr-001 code=INVALID_REQUEST reason="timeoutSeconds must not be negative"
--- PASS: TestInvalidRequestBadTimeout (0.00s)
=== RUN   TestInvalidRequestUnknownEnvironment
2026/09/28 10:14:09 INFO job rejected jobId=job-001 correlationId=corr-001 code=INVALID_REQUEST reason="unknown environment \"quantum-staging\""
--- PASS: TestInvalidRequestUnknownEnvironment (0.00s)
=== RUN   TestMalformedRequestJSON
--- PASS: TestMalformedRequestJSON (0.00s)
=== RUN   TestUnknownCollectorRejected
2026/09/28 10:14:09 INFO job rejected jobId=job-001 correlationId=corr-001 collectorId=does-not-exist collectorVersion=v1 code=UNKNOWN_COLLECTOR reason="collector \"does-not-exist\" is not approved"
--- PASS: TestUnknownCollectorRejected (0.00s)
=== RUN   TestUnsupportedVersionRejected
2026/09/28 10:14:09 INFO job rejected jobId=job-001 correlationId=corr-001 collectorId=fake-collector collectorVersion=v99 code=UNSUPPORTED_COLLECTOR_VERSION reason="collector \"fake-collector\" version \"v99\" is not approved"
--- PASS: TestUnsupportedVersionRejected (0.00s)
=== RUN   TestInvalidParametersUnknownKey
2026/09/28 10:14:09 INFO job rejected: invalid parameters jobId=job-001 correlationId=corr-001 collectorId=fake-collector collectorVersion=v1 params=[shellCommand]
2026/09/28 10:14:09 INFO job rejected jobId=job-001 correlationId=corr-001 collectorId=fake-collector collectorVersion=v1 code=INVALID_PARAMETERS reason="parameter \"shellCommand\" is not approved for this collector"
--- PASS: TestInvalidParametersUnknownKey (0.00s)
=== RUN   TestInvalidParametersMissingRequired
2026/09/28 10:14:09 INFO job rejected: invalid parameters jobId=job-001 correlationId=corr-001 collectorId=db-fake collectorVersion=v1 params=[technology]
2026/09/28 10:14:09 INFO job rejected jobId=job-001 correlationId=corr-001 collectorId=db-fake collectorVersion=v1 code=INVALID_PARAMETERS reason="missing required parameter \"snapshotPath\""
--- PASS: TestInvalidParametersMissingRequired (0.00s)
=== RUN   TestInvalidSourceUnknownID
2026/09/28 10:14:09 INFO job rejected jobId=job-001 correlationId=corr-001 collectorId=fake-collector collectorVersion=v1 code=INVALID_SOURCE reason="source \"no-such-source\" is not an approved source"
--- PASS: TestInvalidSourceUnknownID (0.00s)
=== RUN   TestSourceUnavailable
--- PASS: TestSourceUnavailable (0.00s)
=== RUN   TestSourceRegistryUnreadableIsSourceUnavailable
--- PASS: TestSourceRegistryUnreadableIsSourceUnavailable (0.00s)
=== RUN   TestCollectorFailureHandledSafely
2026/09/28 10:14:09 WARN collector reported an error jobId=job-001 correlationId=corr-001 collectorId=fake-collector collectorVersion=v1 detail="snapshot file unreadable"
--- PASS: TestCollectorFailureHandledSafely (0.00s)
=== RUN   TestExcessiveOutputRejectedBeforeSubmission
2026/09/28 10:14:09 WARN job output exceeded bound jobId=job-001 correlationId=corr-001 collectorId=fake-collector collectorVersion=v1 outputBytes=2732 limit=64
--- PASS: TestExcessiveOutputRejectedBeforeSubmission (0.00s)
=== RUN   TestTimeoutReturnsPromptly
2026/09/28 10:14:09 WARN job timed out jobId=job-001 correlationId=corr-001 collectorId=fake-collector collectorVersion=v1 timeoutSeconds=0
--- PASS: TestTimeoutReturnsPromptly (0.05s)
=== RUN   TestContextCancellationDistinctFromTimeout
2026/09/28 10:14:09 WARN job cancelled jobId=job-001 correlationId=corr-001 collectorId=fake-collector collectorVersion=v1
--- PASS: TestContextCancellationDistinctFromTimeout (0.03s)
=== RUN   TestDuplicateJobIDIsIdempotent
2026/09/28 10:14:09 INFO job succeeded jobId=job-001 correlationId=corr-001 collectorId=fake-collector collectorVersion=v1 evidenceCount=1 outputBytes=16 submitted=false
2026/09/28 10:14:09 INFO job replayed from idempotency store jobId=job-001 correlationId=corr-001 collectorId=fake-collector collectorVersion=v1 status=SUCCEEDED
--- PASS: TestDuplicateJobIDIsIdempotent (0.00s)
=== RUN   TestFeatureFlagDisabledRejectsBeforeExecution
2026/09/28 10:14:09 INFO job rejected: feature disabled jobId=job-001 correlationId=corr-001 collectorId=fake-collector collectorVersion=v1
2026/09/28 10:14:09 INFO job rejected jobId=job-001 correlationId=corr-001 code=FEATURE_DISABLED reason="scheduled evidence pull is disabled"
--- PASS: TestFeatureFlagDisabledRejectsBeforeExecution (0.00s)
=== RUN   TestJobErrorNeverContainsRawParameterValues
2026/09/28 10:14:09 INFO job rejected: invalid parameters jobId=job-001 correlationId=corr-001 collectorId=fake-collector collectorVersion=v1 params=[apiSecretDoNotLog]
2026/09/28 10:14:09 INFO job rejected jobId=job-001 correlationId=corr-001 collectorId=fake-collector collectorVersion=v1 code=INVALID_PARAMETERS reason="parameter \"apiSecretDoNotLog\" is not approved for this collector"
--- PASS: TestJobErrorNeverContainsRawParameterValues (0.00s)
PASS
ok  	github.com/pramaan/pramaan-next/agents-go/internal/job	1.772s
```

## Offline demo verification (RUNBOOK.md scenarios, actually run)

| Demo | Command | Actual result |
|---|---|---|
| Simulation-mode success | `AGENT_JOB_SOURCES_PATH=examples/job/sources.json go run ./cmd/agent job -request examples/job/request-os-baseline.json` (with `SIMULATION_MODE=true`) | `status: SUCCEEDED`, `evidenceCount: 4`, exit 0 |
| Real-mode (local file) success | same, without `SIMULATION_MODE`, against `request-database-real-mode.json` | `status: SUCCEEDED`, `evidenceCount: 4`, 4 Postgres checks PASS, 3 MySQL checks NOT_APPLICABLE, exit 0 |
| Source unavailable | against `request-source-unavailable.json` | `status: FAILED`, `error.code: SOURCE_UNAVAILABLE`, `evidenceCount: 0`, exit 1 |
| Idempotent replay | same request run twice with `AGENT_JOB_IDEMPOTENCY_DIR` set | 1st run: `idempotentReplay: false`; 2nd run: `idempotentReplay: true` |
| Feature flag disabled | `AGENT_JOB_PULL_ENABLED=false` + same request | `status: REJECTED`, `error.code: FEATURE_DISABLED`, exit 1 |

## What was NOT executed

- No test was run against a live `backend-java` instance or a live external
  database/OS/middleware target — by design, per the P01 instruction to use
  synthetic/local test data where a real source is unavailable. The
  `-submit` path was implemented and reuses `evidence.Client` unmodified,
  but was not exercised against a running backend in this session (that
  would require starting the Java service, which is out of scope for a
  Go-only task and not needed to validate the Go logic — `evidence.Client`
  itself already has its own passing test suite, unchanged by this work).
- No load/performance testing was performed; P01 doesn't call for it.
