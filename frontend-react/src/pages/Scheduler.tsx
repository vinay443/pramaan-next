import { useEffect, useRef, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { getRun, listApplications, listRuns, retryRun, startRun } from '../api/endpoints'
import { emitDataEvent } from '../api/events'
import type { RunView } from '../api/types'
import { useAsync } from '../hooks/useAsync'
import { DataTable, ErrorNote, JsonBlock, Loading, Section, StatCard, StatusPill } from '../components/ui'

const TERMINAL = new Set(['COMPLETED', 'FAILED'])
const isTerminal = (s: string | undefined) => s !== undefined && TERMINAL.has(s)
const isActive = (s: string) => s === 'RUNNING' || s === 'PENDING'

// ---- Collection pipeline -------------------------------------------------
// The stages a scheduler run actually goes through in this codebase, in order.
// Traced from SchedulerRunExecutor.execute() + EvidenceIngestionService.ingest():
//   markRunning -> resolve active applications -> for each source: resolve the
//   integration + integration.collect() -> per collected item: ingest (SHA-256
//   hash, resolve control->framework mapping, dedup vs latest version, write to
//   object store + new version + tags + rule re-evaluation) -> run.complete().
// The backend only persists PENDING/RUNNING/COMPLETED/FAILED plus the final
// tallies (received/ingested/duplicates/failed) — there is no per-stage state —
// so stage status here is derived from the run's status + those counts.

type StageState = 'pending' | 'active' | 'done' | 'failed'

interface Stage {
  key: string
  label: string
  /** Count/hint pulled from an existing run field, or null when the backend tracks none. */
  detail?: (r: RunView) => string | null
}

const PIPELINE: Stage[] = [
  { key: 'queued', label: 'Queued' },
  {
    key: 'apps',
    label: 'Applications resolved',
    detail: (r) => (r.applications.length ? `${r.applications.length} named` : 'all onboarded'),
  },
  {
    key: 'sources',
    label: 'Sources polled',
    detail: (r) => `${r.sources.length} source${r.sources.length === 1 ? '' : 's'}`,
  },
  { key: 'received', label: 'Evidence received', detail: (r) => `${r.received} recv` },
  // No backing count — the control->framework mapping is resolved per item inside
  // ingest() and never tallied on the run.
  { key: 'mapping', label: 'Control mappings resolved' },
  { key: 'dedup', label: 'Dedup / reuse check', detail: (r) => `${r.duplicates} dup` },
  { key: 'stored', label: 'Evidence stored', detail: (r) => `${r.ingested} ingested` },
  { key: 'complete', label: 'Complete', detail: (r) => (r.failed ? `${r.failed} failed` : null) },
]

function stageStates(r: RunView): Record<string, StageState> {
  const fill = (v: StageState) =>
    Object.fromEntries(PIPELINE.map((s) => [s.key, v])) as Record<string, StageState>

  if (r.status === 'PENDING') return { ...fill('pending'), queued: 'active' }
  if (r.status === 'RUNNING') return { ...fill('active'), queued: 'done', complete: 'pending' }
  if (r.status === 'COMPLETED') return fill('done')

  // FAILED — mark the stage the failure most likely landed on. received===0 means
  // no source produced anything (integration resolution / collect failed);
  // otherwise ingestion/storage failed.
  const failedAt = r.received === 0 ? 'sources' : 'stored'
  const out: Record<string, StageState> = {}
  let done = true
  for (const s of PIPELINE) {
    if (s.key === 'complete') {
      out[s.key] = 'failed'
    } else if (!done) {
      out[s.key] = 'pending'
    } else if (s.key === failedAt) {
      out[s.key] = 'failed'
      done = false
    } else {
      out[s.key] = 'done'
    }
  }
  return out
}

const MARKER: Record<StageState, string> = { done: '✓', failed: '✕', active: '', pending: '' }

export function RunPipeline({ run }: { run: RunView }) {
  const states = stageStates(run)
  return (
    <ol className="pipeline" aria-label="Collection pipeline">
      {PIPELINE.map((stage) => {
        const state = states[stage.key]
        const detail = stage.detail?.(run) ?? null
        return (
          <li
            key={stage.key}
            className={`pipeline-step is-${state}`}
            aria-label={`${stage.label}: ${state}`}
          >
            <span className="pipeline-marker" aria-hidden="true">
              {state === 'active' ? <span className="state-spinner" /> : MARKER[state]}
            </span>
            <span className="pipeline-label">{stage.label}</span>
            {detail ? <span className="pipeline-detail">{detail}</span> : null}
          </li>
        )
      })}
    </ol>
  )
}

export function Scheduler() {
  const [params, setParams] = useSearchParams()
  const selectedRunId = params.get('run') ?? undefined

  const applications = useAsync(() => listApplications(), [])
  const onboardedApps = (applications.data ?? []).filter((a) => a.active)
  const runs = useAsync(() => listRuns(0, 25), [], {
    pollWhile: (d) => (d?.items ?? []).some((r) => isActive(r.status)),
  })
  const selected = useAsync<RunView | undefined>(
    () => (selectedRunId ? getRun(selectedRunId) : Promise.resolve(undefined)),
    [selectedRunId, runs.data?.items.length],
    { pollWhile: (d) => !!d && !isTerminal(d.status) },
  )

  // When the tracked run reaches a terminal state, refresh the runs table and —
  // if it completed — tell the Evidence Repository its list is now stale so
  // newly pulled evidence shows up without navigating there and back.
  const runsReload = runs.reload
  const lastStatus = useRef<string>()
  const selectedStatus = selected.data?.status
  useEffect(() => {
    const prev = lastStatus.current
    lastStatus.current = selectedStatus
    if (!selectedStatus || prev === selectedStatus) return
    if (isTerminal(selectedStatus)) {
      runsReload()
      if (selectedStatus === 'COMPLETED') emitDataEvent('evidence-changed')
    }
  }, [selectedStatus, runsReload])

  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string>()

  const noneOnboarded = !applications.loading && onboardedApps.length === 0

  async function start() {
    setBusy(true)
    setError(undefined)
    try {
      // No per-app / per-source selection — run collection across every onboarded
      // application and every configured source. Omitting applications/sources
      // makes the backend default to all active apps + its default source set.
      const run = await startRun({ requestedBy: 'ui' })
      runs.reload()
      setParams({ run: run.runId })
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e))
    } finally {
      setBusy(false)
    }
  }

  async function retry(runId: string) {
    setBusy(true)
    setError(undefined)
    try {
      const run = await retryRun(runId)
      runs.reload()
      setParams({ run: run.runId })
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="page">
      <h1>Scheduler</h1>

      <Section
        title="Start a collection run"
        actions={
          <button className="primary" onClick={start} disabled={busy || noneOnboarded}>
            {busy ? 'Working…' : 'Run Collection'}
          </button>
        }
      >
        {noneOnboarded ? (
          <ErrorNote message="No applications onboarded — onboard at least one application before running the scheduler." />
        ) : (
          <p className="muted">
            Runs collection across all {onboardedApps.length || ''} onboarded application
            {onboardedApps.length === 1 ? '' : 's'} and every configured source.
          </p>
        )}
        {error ? <ErrorNote message={error} /> : null}
      </Section>

      <Section title="Runs">
        {runs.loading ? <Loading what="runs" /> : null}
        {runs.error ? <ErrorNote message={runs.error} /> : null}
        {runs.data ? (
          <DataTable
            rows={runs.data.items}
            rowKey={(r) => r.runId}
            onRowClick={(r) => setParams({ run: r.runId })}
            columns={[
              { header: 'Run', cell: (r) => r.runId },
              { header: 'Trigger', cell: (r) => r.trigger },
              { header: 'Status', cell: (r) => <StatusPill status={r.status} /> },
              { header: 'Recv', cell: (r) => r.received },
              { header: 'Ingested', cell: (r) => r.ingested },
              { header: 'Dup', cell: (r) => r.duplicates },
              { header: 'Failed', cell: (r) => r.failed },
              {
                header: '',
                cell: (r) => (
                  <button
                    disabled={busy || r.status === 'RUNNING' || r.status === 'PENDING'}
                    onClick={(e) => {
                      e.stopPropagation()
                      retry(r.runId)
                    }}
                  >
                    Retry
                  </button>
                ),
              },
            ]}
          />
        ) : null}
      </Section>

      {selectedRunId ? (
        <Section title={`Run ${selectedRunId}`}>
          {selected.loading ? <Loading what="run" /> : null}
          {selected.error ? <ErrorNote message={selected.error} /> : null}
          {selected.data ? (
            <>
              <RunPipeline run={selected.data} />
              <div className="stat-grid">
                <StatCard label="Status" value={<StatusPill status={selected.data.status} />} />
                <StatCard label="Received" value={selected.data.received} />
                <StatCard label="Ingested" value={selected.data.ingested} />
                <StatCard label="Duplicates" value={selected.data.duplicates} />
                <StatCard label="Failed" value={selected.data.failed} />
              </div>
              <p className="muted">{selected.data.message}</p>
              <h3>Per source</h3>
              <JsonBlock value={selected.data.perSource} />
            </>
          ) : null}
        </Section>
      ) : null}
    </div>
  )
}
