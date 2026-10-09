import { Link } from 'react-router-dom'
import { listApplications } from '../api/endpoints'
import type { DashboardAutomatedKpis, DashboardFilterParams } from '../api/types'
import { useAsync } from '../hooks/useAsync'
import { Section, StatCard, StatusPill } from './ui'

const METHODS = ['scheduled', 'manual', 'bulk', 'on-demand', 'predefined-query']

const fmtTime = (iso?: string | null) => (iso ? new Date(iso).toLocaleString() : '—')

/** Evidence Repository query string for the same filters the KPIs use (collected-before is exclusive). */
export function repositoryQuery(f: DashboardFilterParams): string {
  const q = new URLSearchParams()
  if (f.application) q.set('applicationSlug', f.application)
  if (f.framework) q.set('framework', f.framework)
  if (f.collectionMethod) q.set('collectionMethod', f.collectionMethod)
  if (f.from) q.set('collectedAfter', `${f.from}T00:00:00Z`)
  if (f.to) q.set('collectedBefore', `${new Date(Date.parse(`${f.to}T00:00:00Z`) + 86_400_000).toISOString().slice(0, 19)}Z`)
  return q.toString()
}

/** Application / framework / collection method / date range, with the active filters shown and a Reset. */
export function DashboardFilterBar({
  filters,
  onChange,
  frameworks,
}: {
  filters: DashboardFilterParams
  onChange: (next: DashboardFilterParams) => void
  frameworks: string[]
}) {
  const apps = useAsync(() => listApplications(), [])
  const set = (patch: DashboardFilterParams) => onChange({ ...filters, ...patch })
  const active = Object.entries(filters).filter(([, v]) => !!v)

  return (
    <Section
      title="Filters"
      actions={
        <button type="button" onClick={() => onChange({})} disabled={active.length === 0}>
          Reset
        </button>
      }
    >
      <div className="filter-row">
        <label>
          Application
          <select aria-label="Application" value={filters.application ?? ''} onChange={(e) => set({ application: e.target.value || undefined })}>
            <option value="">All applications</option>
            {(apps.data ?? []).filter((a) => a.active).map((a) => (
              <option key={a.slug} value={a.slug}>
                {a.name}
              </option>
            ))}
          </select>
        </label>
        <label>
          Framework
          <select aria-label="Framework" value={filters.framework ?? ''} onChange={(e) => set({ framework: e.target.value || undefined })}>
            <option value="">All frameworks</option>
            {frameworks.map((f) => (
              <option key={f} value={f}>
                {f}
              </option>
            ))}
          </select>
        </label>
        <label>
          Collection method
          <select
            aria-label="Collection method"
            value={filters.collectionMethod ?? ''}
            onChange={(e) => set({ collectionMethod: e.target.value || undefined })}
          >
            <option value="">Any method</option>
            {METHODS.map((m) => (
              <option key={m} value={m}>
                {m}
              </option>
            ))}
          </select>
        </label>
        <label>
          From
          <input type="date" aria-label="From date" value={filters.from ?? ''} onChange={(e) => set({ from: e.target.value || undefined })} />
        </label>
        <label>
          To
          <input type="date" aria-label="To date" value={filters.to ?? ''} onChange={(e) => set({ to: e.target.value || undefined })} />
        </label>
      </div>
      <p className="muted small" aria-label="Active filters">
        {active.length === 0 ? 'No filters: showing everything.' : `Active filters: ${active.map(([k, v]) => `${k} = ${v}`).join(' · ')}`}
      </p>
    </Section>
  )
}

/** Run / schedule KPIs from stored scheduler runs. A metric the backend could not compute is not shown. */
export function AutomatedCollectionSection({
  kpis,
  filters,
}: {
  kpis: DashboardAutomatedKpis
  filters: DashboardFilterParams
}) {
  const repoQs = repositoryQuery({ ...filters, collectionMethod: filters.collectionMethod ?? 'scheduled' })
  const finished = kpis.completed + kpis.partial + kpis.failed
  return (
    <Section
      title="Automated collection"
      actions={
        <span className="row-actions">
          <Link to="/scheduler">Open Scheduler →</Link> <Link to={`/evidence?${repoQs}`}>View matching evidence →</Link>
        </span>
      }
    >
      <div className="stat-grid">
        <StatCard label="Total runs" value={kpis.totalRuns} hint={`${kpis.scheduledRuns} scheduled · ${kpis.manualRuns} manual`} />
        <StatCard label="Completed" value={kpis.completed} />
        <StatCard label="Partial" value={kpis.partial} />
        <StatCard label="Failed" value={kpis.failed} />
        {kpis.successRatePct !== null ? (
          <StatCard label="Success rate" value={`${kpis.successRatePct}%`} hint={`${kpis.completed} of ${finished} finished runs`} />
        ) : null}
        <StatCard label="Evidence ingested" value={kpis.evidenceIngested} />
        <StatCard label="Duplicates skipped" value={kpis.duplicatesSkipped} />
        <StatCard label="Active schedules" value={kpis.activeSchedules} hint={kpis.nextDueAt ? `Next due ${fmtTime(kpis.nextDueAt)}` : undefined} />
        <StatCard
          label="Within SLA"
          value={`${kpis.runsWithinSla} / ${kpis.runsWithinSla + kpis.runsBreached}`}
          hint={`${kpis.runsBreached} breached · SLA ${kpis.slaSeconds}s`}
        />
        <StatCard label="Open observations" value={kpis.openObservations} />
        <StatCard label="Unread notifications" value={kpis.unreadNotifications} />
      </div>
      <p>
        <strong>Last run:</strong>{' '}
        {kpis.lastRun ? (
          <>
            <StatusPill status={kpis.lastRun.status} /> {fmtTime(kpis.lastRun.at)}
            {kpis.lastRun.scheduleName ? ` · ${kpis.lastRun.scheduleName}` : ''} ·{' '}
            <Link to={`/scheduler?run=${kpis.lastRun.runId}`}>open run</Link>
          </>
        ) : (
          'no runs match these filters'
        )}
      </p>
    </Section>
  )
}
