# P01 Rollback

This feature is additive and isolated: it added one new package
(`internal/job`), a new CLI subcommand (`agent job`), a handful of new
optional fields on `internal/config.Config`, and new doc/example files. It
does not modify the behavior of `agent describe`, `agent checks`, or
`agent run`, and it never touches `backend-java` or `frontend-react`.

## Option 1 — disable without any code change (recommended first step)

Set the feature flag off wherever `agent job` would be invoked:

```bash
AGENT_JOB_PULL_ENABLED=false
```

Every job request is then rejected (`status: REJECTED`, `error.code:
FEATURE_DISABLED`) before any validation, collection, or submission occurs
— confirmed by `TestFeatureFlagDisabledRejectsBeforeExecution` and the
RUNBOOK.md demo. This is reversible instantly (unset the variable, or set it
to `true`) and requires no deploy of new code.

## Option 2 — stop invoking the subcommand

Since nothing in `backend-java`/`frontend-react` invokes `agent job` (this
task did not add such a caller — see README.md/ARCHITECTURE.md), rollback
may simply mean: whatever script, cron entry, or manual process was calling
`agent job` stops calling it. `agent describe`, `agent checks`, and `agent
run` are completely unaffected either way.

## Option 3 — full code revert

If the package itself needs to be removed:

1. Delete `agents-go/internal/job/` (the entire package).
2. Revert the `job` subcommand block and the `"github.com/.../internal/job"`
   import in `agents-go/cmd/agent/main.go` (the `describe`/`checks`/`run`
   subcommands and their code are untouched by this revert).
3. Revert the `Job*` fields and `intEnv` helper added to
   `agents-go/internal/config/config.go` (or leave them — they're inert
   config surface with no effect on `describe`/`checks`/`run`, since no
   pre-existing code path reads them).
4. Delete `agents-go/examples/job/` and
   `agents-go/docs/p01-scheduled-evidence-pull/`.
5. Rebuild and re-run the full suite to confirm the pre-existing packages
   are unaffected:

   ```bash
   cd agents-go
   go build ./...
   go test ./...
   ```

   Expected: identical to the pre-P01 baseline (`agent`, `collectors`,
   `config`, `evidence` all `ok`).

## Data cleanup

The only persistent state this feature creates is the idempotency cache
directory (`AGENT_JOB_IDEMPOTENCY_DIR`, default `<TMP>/pramaan-agent-jobs`).
It contains only job results (status, findings, timestamps) — no
credentials, no evidence content, no raw parameter values (per the logging
contract in CONTRACT.md, and by construction: `JobResult` never carries
parameter values). Deleting that directory is safe at any time:

```bash
rm -rf "$AGENT_JOB_IDEMPOTENCY_DIR"   # or the OS-temp default, if unset
```

## Backend/evidence impact

If `-submit` was used against a real `backend-java` instance, evidence was
already ingested through the existing, unmodified `/api/v1/evidence/ingest`
and `/bulk` endpoints — the same endpoints `agent run` uses. Rolling back
P01 does not retract already-submitted evidence; that would be a
`backend-java`-side data operation (deleting/superseding evidence records),
out of scope for a Go-only rollback and not something this task's changes
can or should do unilaterally.
