import { useEffect, useRef, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { getRun, listApplications, listRuns, listSources, retryRun, startRun } from '../api/endpoints'
import { emitDataEvent } from '../api/events'
import type { RunView } from '../api/types'
import { useAsync } from '../hooks/useAsync'

const TERMINAL = new Set(['COMPLETED', 'FAILED'])
const isTerminal = (s: string | undefined) => s !== undefined && TERMINAL.has(s)
const isActive = (s: string) => s === 'RUNNING' || s === 'PENDING'
import { DataTable, ErrorNote, JsonBlock, Loading, Section, StatCard, StatusPill } from '../components/ui'

export function Scheduler() {
  const [params, setParams] = useSearchParams()
  const selectedRunId = params.get('run') ?? undefined

  const sources = useAsync(() => listSources(), [])
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

  const [chosenSources, setChosenSources] = useState<string[]>([])
  const [chosenApps, setChosenApps] = useState<string[]>([])
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string>()

  function toggleSource(s: string) {
    setChosenSources((cur) => (cur.includes(s) ? cur.filter((x) => x !== s) : [...cur, s]))
  }

  function toggleApp(s: string) {
    setChosenApps((cur) => (cur.includes(s) ? cur.filter((x) => x !== s) : [...cur, s]))
  }

  const noneOnboarded = !applications.loading && onboardedApps.length === 0

  async function start() {
    setBusy(true)
    setError(undefined)
    try {
      const run = await startRun({
        applications: chosenApps.length ? chosenApps : undefined,
        sources: chosenSources.length ? chosenSources : undefined,
        requestedBy: 'ui',
      })
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

      <Section title="Start a collection run" actions={<button className="primary" onClick={start} disabled={busy || noneOnboarded}>{busy ? 'Working…' : 'Start run'}</button>}>
        {noneOnboarded ? (
          <ErrorNote message="No applications onboarded — onboard at least one application before running the scheduler." />
        ) : null}
        <fieldset className="sources">
          <legend>
            Applications {applications.loading ? '(loading…)' : chosenApps.length === 0 ? '(blank = all onboarded)' : ''}
          </legend>
          {onboardedApps.map((a) => (
            <label key={a.slug} className="checkbox">
              <input type="checkbox" checked={chosenApps.includes(a.slug)} onChange={() => toggleApp(a.slug)} /> {a.name}{' '}
              <span className="muted small">({a.slug})</span>
            </label>
          ))}
        </fieldset>
        <fieldset className="sources">
          <legend>Sources {sources.loading ? '(loading…)' : chosenSources.length === 0 ? '(blank = default sources)' : ''}</legend>
          {(sources.data ?? []).map((s) => (
            <label key={s} className="checkbox">
              <input type="checkbox" checked={chosenSources.includes(s)} onChange={() => toggleSource(s)} /> {s}
            </label>
          ))}
        </fieldset>
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
