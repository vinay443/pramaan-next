import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { getEvidenceDashboard, getTrend, listRuns } from '../api/endpoints'
import { useAsync } from '../hooks/useAsync'
import { DataTable, ErrorNote, LineChart, Loading, Section, StatCard, StatusPill } from '../components/ui'

function MetricIcon({ d }: { d: ReactNode }) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      {d}
    </svg>
  )
}
const ICONS = {
  records: <path d="M4 7h16M6 7v11a1 1 0 0 0 1 1h10a1 1 0 0 0 1-1V7M9 11h6" />,
  versions: <path d="M12 3 3 8l9 5 9-5-9-5ZM3 13l9 5 9-5" />,
  apps: <path d="M4 4h7v7H4zM13 4h7v7h-7zM4 13h7v7H4zM13 13h7v7h-7z" />,
  frameworks: <path d="M12 3 5 6v5c0 4.5 3 8 7 10 4-2 7-5.5 7-10V6l-7-3Z" />,
  sources: <path d="M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18ZM3 12h18M12 3c2.5 2.7 3.8 5.8 3.8 9S14.5 18.3 12 21c-2.5-2.7-3.8-5.8-3.8-9S9.5 5.7 12 3Z" />,
  runs: <path d="M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18ZM12 7v5l3 2" />,
}

export function Dashboard() {
  const board = useAsync(() => getEvidenceDashboard(), [])
  const runs = useAsync(() => listRuns(0, 5), [])
  const trend = useAsync(() => getTrend(), [])

  const trendPoints = trend.data ? [...trend.data.points, trend.data.current] : []
  const volumeSeries = trendPoints.map((p) => ({
    label: new Date(p.takenAt).toLocaleDateString(undefined, { month: 'short', day: 'numeric' }),
    value: p.evidenceCount,
  }))
  const integritySeries = trendPoints.map((p) => ({
    label: new Date(p.takenAt).toLocaleDateString(undefined, { month: 'short', day: 'numeric' }),
    value: p.integrityChecked === 0 ? 100 : Math.round((p.integrityIntact / p.integrityChecked) * 1000) / 10,
  }))

  const d = board.data
  const integrity = d?.integrity
  const integrityTone =
    integrity && integrity.mismatch === 0 && integrity.missingObject === 0 ? 'PASS' : 'FAIL'

  return (
    <div className="page">
      <h1>Dashboard</h1>
      <p className="muted">
        Evidence repository snapshot — <code>GET /api/v1/evidence/dashboard</code>. Integrity =
        SHA-256 recomputed from the object store for every current version.
      </p>

      {board.loading ? <Loading what="evidence dashboard" /> : null}
      {board.error ? <ErrorNote message={board.error} /> : null}

      <div className="stat-grid">
        <StatCard label="Evidence records" value={d?.records ?? '—'} icon={<MetricIcon d={ICONS.records} />} />
        <StatCard label="Versions" value={d?.versions ?? '—'} icon={<MetricIcon d={ICONS.versions} />} />
        <StatCard label="Applications" value={d?.applications ?? '—'} icon={<MetricIcon d={ICONS.apps} />} />
        <StatCard label="Frameworks" value={d?.frameworks ?? '—'} icon={<MetricIcon d={ICONS.frameworks} />} />
        <StatCard label="Sources" value={d?.sources ?? '—'} icon={<MetricIcon d={ICONS.sources} />} />
        <StatCard label="Scheduler runs" value={runs.data?.totalItems ?? '—'} icon={<MetricIcon d={ICONS.runs} />} />
      </div>

      <div className="chart-grid">
        <Section title="Evidence volume over time">
          {trend.loading ? <Loading what="trend" /> : null}
          {trend.error ? <ErrorNote message={trend.error} /> : null}
          {trend.data ? (
            <>
              <LineChart points={volumeSeries} ariaLabel="evidence record count per snapshot" />
              <p className="muted small">
                Persisted posture snapshots (<code>GET /api/v1/insight/trend</code>). Latest point is the live count.
              </p>
            </>
          ) : null}
        </Section>

        <Section title="Integrity pass-rate over time">
          {trend.data ? (
            <>
              <LineChart
                points={integritySeries}
                ariaLabel="hash integrity pass rate per snapshot"
                yMax={100}
                valueSuffix="%"
              />
              <p className="muted small">% of checked current versions that hashed intact per snapshot.</p>
            </>
          ) : null}
        </Section>
      </div>

      <Section title="Hash integrity">
        {integrity ? (
          <>
            <p>
              <StatusPill status={integrityTone} />{' '}
              {integrity.intact}/{integrity.checked} current versions verified intact
              {integrity.mismatch > 0 ? `, ${integrity.mismatch} mismatch` : ''}
              {integrity.missingObject > 0 ? `, ${integrity.missingObject} missing from store` : ''}.
            </p>
            <div className="stat-grid">
              <StatCard label="Intact" value={integrity.intact} />
              <StatCard label="Mismatch" value={integrity.mismatch} />
              <StatCard label="Missing object" value={integrity.missingObject} />
              <StatCard label="Duplicate hashes" value={d?.duplicateHashes ?? '—'} />
            </div>
          </>
        ) : null}
      </Section>

      <Section title="Freshness">
        {d ? (
          <div className="stat-grid">
            <StatCard label="Fresh (≤30d)" value={d.freshness.fresh} />
            <StatCard label="Aging (≤90d)" value={d.freshness.aging} />
            <StatCard label={`Stale (>${d.staleAfterDays}d)`} value={d.freshness.stale} />
            <StatCard label="Unknown" value={d.freshness.unknown} />
          </div>
        ) : null}
      </Section>

      <Section title="Evidence by source">
        {d ? (
          <DataTable
            rows={d.bySource}
            rowKey={(r) => String(r.sourceSystem)}
            columns={[
              { header: 'Source system', cell: (r) => String(r.sourceSystem) },
              { header: 'Evidence', cell: (r) => String(r.count), align: 'right' },
            ]}
          />
        ) : null}
      </Section>

      <Section title="Recent scheduler runs" actions={<Link to="/scheduler">Open scheduler →</Link>}>
        {runs.loading ? <Loading what="runs" /> : null}
        {runs.error ? <ErrorNote message={runs.error} /> : null}
        {runs.data ? (
          <DataTable
            rows={runs.data.items}
            rowKey={(r) => r.runId}
            columns={[
              { header: 'Run', cell: (r) => <Link to={`/scheduler?run=${r.runId}`}>{r.runId}</Link> },
              { header: 'Trigger', cell: (r) => r.trigger },
              { header: 'Status', cell: (r) => <StatusPill status={r.status} /> },
              { header: 'Ingested', cell: (r) => r.ingested, align: 'right' },
              { header: 'Duplicates', cell: (r) => r.duplicates, align: 'right' },
              { header: 'Finished', cell: (r) => fmt(r.finishedAt) },
            ]}
          />
        ) : null}
      </Section>
    </div>
  )
}

function fmt(iso?: string | null): string {
  return iso ? new Date(iso).toLocaleString() : '—'
}
