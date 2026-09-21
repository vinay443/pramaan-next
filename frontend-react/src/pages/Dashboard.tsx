import { useState } from 'react'
import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { getComparison, getEvidenceDashboard, getLeadershipDashboard, getTrend, listRuns } from '../api/endpoints'
import { useAsync } from '../hooks/useAsync'
import { useControlResultsSummary } from '../hooks/useControlResultsSummary'
import { useCompletenessSummary } from '../hooks/useCompletenessSummary'
import { DataTable, ErrorNote, LineChart, Loading, Section, StatCard, StatusPill } from '../components/ui'
import { PERSONA_STORAGE_KEY } from './PersonaLogin'
import type { EvidenceDashboard } from '../api/types'

function MetricIcon({ d }: { d: ReactNode }) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      {d}
    </svg>
  )
}

/** Card-style navigation tile (icon + label + hint) — same visual language as the
 *  sidebar nav links in Layout.tsx, sized to fill a dashboard section instead of
 *  reading as a bare inline text link. */
function LinkTile({ to, icon, label, hint }: { to: string; icon: ReactNode; label: string; hint: string }) {
  return (
    <Link to={to} className="card dash-link-tile">
      <span className="dash-link-tile-icon">{icon}</span>
      <span className="dash-link-tile-body">
        <span className="dash-link-tile-label">{label}</span>
        <span className="dash-link-tile-hint">{hint}</span>
      </span>
      <span className="dash-link-tile-arrow" aria-hidden="true">→</span>
    </Link>
  )
}

/** Color-coded urgency tile (colored left border + large number + label + one-line
 *  context) — App Owner Overview's "needs attention" row. */
function UrgencyCard({
  tone,
  value,
  label,
  context,
}: {
  tone: 'amber' | 'red' | 'blue'
  value: ReactNode
  label: string
  context: string
}) {
  return (
    <div className={`card dash-urgency-card dash-urgency-${tone}`}>
      <div className="dash-urgency-value">{value}</div>
      <div className="dash-urgency-label">{label}</div>
      <div className="dash-urgency-context">{context}</div>
    </div>
  )
}
const ICONS = {
  records: <path d="M4 7h16M6 7v11a1 1 0 0 0 1 1h10a1 1 0 0 0 1-1V7M9 11h6" />,
  versions: <path d="M12 3 3 8l9 5 9-5-9-5ZM3 13l9 5 9-5" />,
  apps: <path d="M4 4h7v7H4zM13 4h7v7h-7zM4 13h7v7H4zM13 13h7v7h-7z" />,
  frameworks: <path d="M12 3 5 6v5c0 4.5 3 8 7 10 4-2 7-5.5 7-10V6l-7-3Z" />,
  sources: <path d="M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18ZM3 12h18M12 3c2.5 2.7 3.8 5.8 3.8 9S14.5 18.3 12 21c-2.5-2.7-3.8-5.8-3.8-9S9.5 5.7 12 3Z" />,
  runs: <path d="M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18ZM12 7v5l3 2" />,
  clipboard: <path d="M9 4h6v3H9zM7 5H5v16h14V5h-2M9 12h6M9 16h4" />,
  file: <path d="M7 3h7l5 5v13H7zM14 3v5h5M10 13h6M10 17h6" />,
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

  const personaCode = localStorage.getItem(PERSONA_STORAGE_KEY)
  const isAppOwner = personaCode === 'APP'
  const isAuditor = personaCode === 'AUDITOR'
  const isFunctionalHead = personaCode === 'FH'
  const isVerticalHead = personaCode === 'VH'
  const isCIO = personaCode === 'CIO'
  const isAdmin = personaCode === 'ADMIN'

  const statGrid = (
    <div className="stat-grid">
      <StatCard label="Evidence records" value={d?.records ?? '—'} icon={<MetricIcon d={ICONS.records} />} />
      <StatCard label="Versions" value={d?.versions ?? '—'} icon={<MetricIcon d={ICONS.versions} />} />
      <StatCard label="Applications" value={d?.applications ?? '—'} icon={<MetricIcon d={ICONS.apps} />} />
      <StatCard label="Frameworks" value={d?.frameworks ?? '—'} icon={<MetricIcon d={ICONS.frameworks} />} />
      <StatCard label="Sources" value={d?.sources ?? '—'} icon={<MetricIcon d={ICONS.sources} />} />
      <StatCard label="Scheduler runs" value={runs.data?.totalItems ?? '—'} icon={<MetricIcon d={ICONS.runs} />} />
    </div>
  )

  if (isAppOwner) {
    return (
      <AppOwnerDashboard
        board={board}
        runs={runs}
        trend={trend}
        volumeSeries={volumeSeries}
        integritySeries={integritySeries}
        d={d}
        integrity={integrity}
        integrityTone={integrityTone}
      />
    )
  }

  if (isAuditor) {
    return <AuditorDashboard board={board} d={d} integrity={integrity} integrityTone={integrityTone} />
  }

  if (isFunctionalHead) {
    return <FunctionalHeadDashboard board={board} d={d} />
  }

  if (isVerticalHead) {
    return <VerticalHeadDashboard board={board} d={d} trend={trend} integritySeries={integritySeries} />
  }

  if (isCIO) {
    return (
      <CIODashboard board={board} d={d} integrity={integrity} integrityTone={integrityTone} trend={trend} integritySeries={integritySeries} />
    )
  }

  if (isAdmin) {
    return <AdminDashboard board={board} d={d} integrity={integrity} integrityTone={integrityTone} />
  }

  return (
    <div className="page">
      <h1>Dashboard</h1>
      <p className="muted">
        Evidence repository snapshot — <code>GET /api/v1/evidence/dashboard</code>. Integrity =
        SHA-256 recomputed from the object store for every current version.
      </p>

      {board.loading ? <Loading what="evidence dashboard" /> : null}
      {board.error ? <ErrorNote message={board.error} /> : null}

      {statGrid}

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

type AsyncBoard = ReturnType<typeof useAsync<Awaited<ReturnType<typeof getEvidenceDashboard>>>>
type AsyncRuns = ReturnType<typeof useAsync<Awaited<ReturnType<typeof listRuns>>>>
type AsyncTrend = ReturnType<typeof useAsync<Awaited<ReturnType<typeof getTrend>>>>

const APP_OWNER_TABS = ['Overview', 'Integrity & Freshness', 'Sources & Activity'] as const
type AppOwnerTab = (typeof APP_OWNER_TABS)[number]

/** App Owner (role 'APP') dashboard — reorganized into local tabs so every tab fits
 *  one viewport with no page scroll. Other personas keep the original single-page layout. */
function AppOwnerDashboard({
  board,
  runs,
  trend,
  volumeSeries,
  integritySeries,
  d,
  integrity,
  integrityTone,
}: {
  board: AsyncBoard
  runs: AsyncRuns
  trend: AsyncTrend
  volumeSeries: Array<{ label: string; value: number }>
  integritySeries: Array<{ label: string; value: number }>
  d: EvidenceDashboard | undefined
  integrity: EvidenceDashboard['integrity'] | undefined
  integrityTone: string
}) {
  const [tab, setTab] = useState<AppOwnerTab>('Overview')
  const [chartView, setChartView] = useState<'volume' | 'integrity'>('volume')

  const integrityPassRate =
    integrity && integrity.checked > 0 ? Math.round((integrity.intact / integrity.checked) * 1000) / 10 : null

  return (
    <div className="page dash-app-owner">
      <h1>Dashboard</h1>
      <p className="muted">
        Evidence repository snapshot — <code>GET /api/v1/evidence/dashboard</code>. Integrity =
        SHA-256 recomputed from the object store for every current version.
      </p>

      {board.loading ? <Loading what="evidence dashboard" /> : null}
      {board.error ? <ErrorNote message={board.error} /> : null}

      <div className="dash-tabs" role="tablist" aria-label="Dashboard sections">
        {APP_OWNER_TABS.map((t) => (
          <button
            key={t}
            type="button"
            role="tab"
            aria-selected={t === tab}
            className={t === tab ? 'primary' : undefined}
            onClick={() => setTab(t)}
          >
            {t}
          </button>
        ))}
      </div>

      {tab === 'Overview' ? (
        <div className="dash-tab-panel">
          <div className="dash-hero-row">
            <StatCard size="lg" label="Evidence records" value={d?.records ?? '—'} icon={<MetricIcon d={ICONS.records} />} />
            <StatCard size="lg" label="Versions" value={d?.versions ?? '—'} icon={<MetricIcon d={ICONS.versions} />} />
            <StatCard size="lg" label="Scheduler runs" value={runs.data?.totalItems ?? '—'} icon={<MetricIcon d={ICONS.runs} />} />
          </div>

          <div className="stat-grid dash-rate-row">
            <StatCard label="Integrity pass-rate" value={integrityPassRate === null ? '—' : `${integrityPassRate}%`} />
            <StatCard label="Stale evidence" value={d?.freshness.stale ?? '—'} />
            <StatCard label="Mismatch" value={integrity?.mismatch ?? '—'} />
          </div>

          <div className="dash-urgency-row">
            <UrgencyCard
              tone="amber"
              value={d?.freshness.stale ?? '—'}
              label="Stale evidence"
              context="Renew before audit."
            />
            <UrgencyCard
              tone="red"
              value={integrity ? integrity.mismatch + integrity.missingObject : '—'}
              label="Integrity issues"
              context="Needs re-verification."
            />
            <UrgencyCard
              tone="blue"
              value={d?.duplicateHashes ?? '—'}
              label="Duplicate hashes"
              context="Same content collected more than once — safe to dedupe."
            />
          </div>

          <div className="dash-chip-row">
            <span className="dash-chip dash-chip-ok">
              Fresh <strong>{d?.freshness.fresh ?? '—'}</strong>
            </span>
            <span className="dash-chip dash-chip-warn">
              Aging <strong>{d?.freshness.aging ?? '—'}</strong>
            </span>
            <span className="dash-chip dash-chip-bad">
              Stale <strong>{d?.freshness.stale ?? '—'}</strong>
            </span>
            <span className="dash-chip dash-chip-muted">
              Unknown <strong>{d?.freshness.unknown ?? '—'}</strong>
            </span>
          </div>
        </div>
      ) : null}

      {tab === 'Integrity & Freshness' ? (
        <div className="dash-tab-panel">
          <div className="dash-grid-2">
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
          </div>

          <Section
            title={chartView === 'volume' ? 'Evidence volume over time' : 'Integrity pass-rate over time'}
            actions={
              <div className="row-actions">
                <button
                  type="button"
                  className={chartView === 'volume' ? 'primary' : undefined}
                  onClick={() => setChartView('volume')}
                >
                  Evidence volume
                </button>
                <button
                  type="button"
                  className={chartView === 'integrity' ? 'primary' : undefined}
                  onClick={() => setChartView('integrity')}
                >
                  Integrity pass-rate
                </button>
              </div>
            }
          >
            {trend.loading ? <Loading what="trend" /> : null}
            {trend.error ? <ErrorNote message={trend.error} /> : null}
            {trend.data ? (
              chartView === 'volume' ? (
                <>
                  <LineChart points={volumeSeries} ariaLabel="evidence record count per snapshot" />
                  <p className="muted small">
                    Persisted posture snapshots (<code>GET /api/v1/insight/trend</code>). Latest point is the live
                    count.
                  </p>
                </>
              ) : (
                <>
                  <LineChart
                    points={integritySeries}
                    ariaLabel="hash integrity pass rate per snapshot"
                    yMax={100}
                    valueSuffix="%"
                  />
                  <p className="muted small">% of checked current versions that hashed intact per snapshot.</p>
                </>
              )
            ) : null}
          </Section>
        </div>
      ) : null}

      {tab === 'Sources & Activity' ? (
        <div className="dash-tab-panel dash-grid-2">
          <Section title="Evidence by source" actions={<Link to="/evidence">Open repository →</Link>}>
            {d ? (
              <DataTable
                className="dash-capped-table"
                rows={d.bySource.slice(0, 6)}
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
                className="dash-capped-table"
                rows={runs.data.items}
                rowKey={(r) => r.runId}
                columns={[
                  {
                    header: 'Run',
                    cell: (r) => (
                      <Link className="dash-truncate" to={`/scheduler?run=${r.runId}`} title={r.runId}>
                        {r.runId}
                      </Link>
                    ),
                    className: 'dash-truncate-col',
                  },
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
      ) : null}
    </div>
  )
}

const AUDITOR_TABS = ['Overview', 'Control & Compliance', 'Audit Readiness'] as const
type AuditorTab = (typeof AUDITOR_TABS)[number]

/** Auditor (role 'AUDITOR') dashboard — same local-tab pattern as AppOwnerDashboard,
 *  scoped to audit-relevant signals only (integrity, freshness, control/completeness
 *  posture, framework/application scope). No evidence volume trend, evidence-by-source
 *  table, or scheduler runs — those are App Owner concerns. */
function AuditorDashboard({
  board,
  d,
  integrity,
  integrityTone,
}: {
  board: AsyncBoard
  d: EvidenceDashboard | undefined
  integrity: EvidenceDashboard['integrity'] | undefined
  integrityTone: string
}) {
  const [tab, setTab] = useState<AuditorTab>('Overview')
  const controlResults = useControlResultsSummary()
  const completeness = useCompletenessSummary()

  return (
    <div className="page dash-auditor">
      <h1>Dashboard</h1>
      <p className="muted">
        Audit posture snapshot — <code>GET /api/v1/evidence/dashboard</code>. Integrity = SHA-256 recomputed
        from the object store for every current version.
      </p>

      {board.loading ? <Loading what="evidence dashboard" /> : null}
      {board.error ? <ErrorNote message={board.error} /> : null}

      <div className="dash-tabs" role="tablist" aria-label="Dashboard sections">
        {AUDITOR_TABS.map((t) => (
          <button
            key={t}
            type="button"
            role="tab"
            aria-selected={t === tab}
            className={t === tab ? 'primary' : undefined}
            onClick={() => setTab(t)}
          >
            {t}
          </button>
        ))}
      </div>

      {tab === 'Overview' ? (
        <div className="dash-tab-panel dash-grid-2">
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
        </div>
      ) : null}

      {tab === 'Control & Compliance' ? (
        <div className="dash-tab-panel dash-grid-2">
          <Section title="Control results" actions={<Link to="/control-results">Open control results →</Link>}>
            {controlResults.loading ? <Loading what="control results" /> : null}
            {controlResults.error ? <ErrorNote message={controlResults.error} /> : null}
            {controlResults.data ? (
              <div className="stat-grid">
                <StatCard label="PASS" value={controlResults.counts.PASS ?? 0} />
                <StatCard label="WARNING" value={controlResults.counts.WARNING ?? 0} />
                <StatCard label="FAIL" value={controlResults.counts.FAIL ?? 0} />
                <StatCard label="N/A" value={controlResults.counts.NOT_APPLICABLE ?? 0} />
              </div>
            ) : null}
          </Section>

          <Section title="Evidence completeness" actions={<Link to="/completeness">Open completeness →</Link>}>
            {completeness.loading ? <Loading what="evidence completeness" /> : null}
            {completeness.error ? <ErrorNote message={completeness.error} /> : null}
            {completeness.data ? (
              <div className="stat-grid">
                <StatCard
                  label="Avg completeness"
                  value={completeness.totals.avgCompletenessPct === null ? '—' : `${completeness.totals.avgCompletenessPct}%`}
                />
                <StatCard label="Evaluated" value={completeness.totals.evaluatedFrameworks} hint="frameworks" />
                <StatCard label="Partial" value={completeness.totals.partialFrameworks} hint="frameworks" />
                <StatCard label="Not evaluated" value={completeness.totals.notEvaluatedFrameworks} hint="frameworks" />
              </div>
            ) : null}
          </Section>
        </div>
      ) : null}

      {tab === 'Audit Readiness' ? (
        <div className="dash-tab-panel dash-grid-2">
          <Section title="Audit scope">
            {d ? (
              <p className="muted">
                {d.frameworks} framework{d.frameworks === 1 ? '' : 's'} and {d.applications} application
                {d.applications === 1 ? '' : 's'} currently in scope for evidence collection.
              </p>
            ) : null}
            <div className="stat-grid">
              <StatCard label="Frameworks" value={d?.frameworks ?? '—'} icon={<MetricIcon d={ICONS.frameworks} />} />
              <StatCard label="Applications" value={d?.applications ?? '—'} icon={<MetricIcon d={ICONS.apps} />} />
            </div>
          </Section>

          <Section title="Audit resources">
            <div className="dash-tile-grid">
              <LinkTile
                to="/audit-prep"
                icon={<MetricIcon d={ICONS.clipboard} />}
                label="Audit Prep"
                hint="Deterministic readiness checklist from completeness, compliance and evidence lifecycle."
              />
              <LinkTile
                to="/reports"
                icon={<MetricIcon d={ICONS.file} />}
                label="Reports"
                hint="Regulatory report catalogue, downloadable as JSON or CSV."
              />
            </div>
          </Section>
        </div>
      ) : null}
    </div>
  )
}

const FH_TABS = ['Overview', 'Control & Risk', 'Attention Needed'] as const
type FHTab = (typeof FH_TABS)[number]

/** Functional Head (role 'FH') dashboard — same local-tab pattern as AppOwnerDashboard /
 *  AuditorDashboard. Scoped to cross-application control/compliance posture and
 *  action items (completeness gaps, stale evidence). No scheduler runs, evidence-by-
 *  source table, or Audit Prep/Reports links — those are App Owner/Auditor concerns.
 *  The "Control & Risk" tab links out to Compliance instead of showing a portfolio
 *  compliance number: GET /api/v1/insight/compliance is scoped to one application at a
 *  time (see Compliance.tsx), there's no cross-application aggregate to show here. */
function FunctionalHeadDashboard({ board, d }: { board: AsyncBoard; d: EvidenceDashboard | undefined }) {
  const [tab, setTab] = useState<FHTab>('Overview')
  const controlResults = useControlResultsSummary()
  const completeness = useCompletenessSummary()

  const totals = completeness.totals
  const completenessTone = totals.notEvaluatedFrameworks > 0 || totals.partialFrameworks > 0 ? 'FAIL' : 'PASS'
  const staleTone = d && d.freshness.stale > 0 ? 'FAIL' : 'PASS'

  return (
    <div className="page dash-fh">
      <h1>Dashboard</h1>
      <p className="muted">
        Functional posture snapshot — <code>GET /api/v1/evidence/dashboard</code> and{' '}
        <code>GET /api/v1/check-results</code>.
      </p>

      {board.loading ? <Loading what="evidence dashboard" /> : null}
      {board.error ? <ErrorNote message={board.error} /> : null}

      <div className="dash-tabs" role="tablist" aria-label="Dashboard sections">
        {FH_TABS.map((t) => (
          <button
            key={t}
            type="button"
            role="tab"
            aria-selected={t === tab}
            className={t === tab ? 'primary' : undefined}
            onClick={() => setTab(t)}
          >
            {t}
          </button>
        ))}
      </div>

      {tab === 'Overview' ? (
        <div className="dash-tab-panel">
          <div className="stat-grid">
            <StatCard label="Applications" value={d?.applications ?? '—'} icon={<MetricIcon d={ICONS.apps} />} />
            <StatCard label="Frameworks" value={d?.frameworks ?? '—'} icon={<MetricIcon d={ICONS.frameworks} />} />
          </div>

          <Section title="Control results" actions={<Link to="/control-results">Open control results →</Link>}>
            {controlResults.loading ? <Loading what="control results" /> : null}
            {controlResults.error ? <ErrorNote message={controlResults.error} /> : null}
            {controlResults.data ? (
              <div className="stat-grid">
                <StatCard label="PASS" value={controlResults.counts.PASS ?? 0} />
                <StatCard label="WARNING" value={controlResults.counts.WARNING ?? 0} />
                <StatCard label="FAIL" value={controlResults.counts.FAIL ?? 0} />
                <StatCard label="N/A" value={controlResults.counts.NOT_APPLICABLE ?? 0} />
              </div>
            ) : null}
          </Section>
        </div>
      ) : null}

      {tab === 'Control & Risk' ? (
        <div className="dash-tab-panel dash-grid-2">
          <Section title="Control results — detail" actions={<Link to="/control-results">Open control results →</Link>}>
            <p className="muted small">Deterministic rule evaluation over stored evidence, across the portfolio.</p>
            {controlResults.loading ? <Loading what="control results" /> : null}
            {controlResults.error ? <ErrorNote message={controlResults.error} /> : null}
            {controlResults.data ? (
              <div className="stat-grid">
                <StatCard label="PASS" value={controlResults.counts.PASS ?? 0} />
                <StatCard label="WARNING" value={controlResults.counts.WARNING ?? 0} />
                <StatCard label="FAIL" value={controlResults.counts.FAIL ?? 0} />
                <StatCard label="N/A" value={controlResults.counts.NOT_APPLICABLE ?? 0} />
              </div>
            ) : null}
          </Section>

          <Section title="Compliance">
            <p className="muted small">
              Compliance verdicts are rolled up per application against the expected-control catalog — pick an
              application to see its detail.
            </p>
            <div className="dash-tile-grid">
              <LinkTile
                to="/compliance"
                icon={<MetricIcon d={ICONS.frameworks} />}
                label="Compliance"
                hint="Deterministic check verdicts rolled up against the expected-control catalog, per application."
              />
            </div>
          </Section>
        </div>
      ) : null}

      {tab === 'Attention Needed' ? (
        <div className="dash-tab-panel dash-grid-2">
          <Section title="Evaluation gaps">
            {completeness.loading ? <Loading what="evidence completeness" /> : null}
            {completeness.error ? <ErrorNote message={completeness.error} /> : null}
            {completeness.data ? (
              <>
                <p>
                  <StatusPill status={completenessTone} /> {totals.partialFrameworks} framework
                  {totals.partialFrameworks === 1 ? '' : 's'} partially evaluated, {totals.notEvaluatedFrameworks}{' '}
                  not evaluated yet.
                </p>
                <div className="stat-grid">
                  <StatCard label="Evaluated" value={totals.evaluatedFrameworks} />
                  <StatCard label="Partial" value={totals.partialFrameworks} />
                  <StatCard label="Not evaluated" value={totals.notEvaluatedFrameworks} />
                </div>
              </>
            ) : null}
          </Section>

          <Section title="Stale evidence">
            {d ? (
              <>
                <p>
                  <StatusPill status={staleTone} /> {d.freshness.stale} evidence record
                  {d.freshness.stale === 1 ? '' : 's'} older than {d.staleAfterDays} days.
                </p>
                <div className="stat-grid">
                  <StatCard label="Stale" value={d.freshness.stale} />
                  <StatCard label="Aging" value={d.freshness.aging} />
                  <StatCard label="Fresh" value={d.freshness.fresh} />
                </div>
              </>
            ) : null}
          </Section>
        </div>
      ) : null}
    </div>
  )
}

const VH_TABS = ['Overview', 'Portfolio Comparison', 'Leadership Rollup'] as const
type VHTab = (typeof VH_TABS)[number]

/** Vertical Head (role 'VH') dashboard — same local-tab pattern as the other persona
 *  dashboards, but higher-altitude/trend-oriented: cross-application comparison and
 *  the executive leadership rollup, rather than FH's per-control remediation framing.
 *  No scheduler runs, evidence-by-source, or per-app operational detail.
 *
 *  Both "Portfolio Comparison" and "Leadership Rollup" are genuine cross-application
 *  aggregates already returned by their backend endpoints (GET /api/v1/insight/comparison
 *  with no application filter = all applications; GET /api/v1/insight/leadership has no
 *  scoping parameter at all) — unlike FH's Compliance card, there's no single-entity
 *  scoping problem here, so both tabs reuse the same API calls Comparison.tsx and
 *  Leadership.tsx already make, with no new aggregation logic. */
function VerticalHeadDashboard({
  board,
  d,
  trend,
  integritySeries,
}: {
  board: AsyncBoard
  d: EvidenceDashboard | undefined
  trend: AsyncTrend
  integritySeries: Array<{ label: string; value: number }>
}) {
  const [tab, setTab] = useState<VHTab>('Overview')
  const comparison = useAsync(() => getComparison(), [])
  const leadership = useAsync(() => getLeadershipDashboard(), [])

  const cd = comparison.data
  const ld = leadership.data

  return (
    <div className="page dash-vh">
      <h1>Dashboard</h1>
      <p className="muted">
        Portfolio trend snapshot — <code>GET /api/v1/evidence/dashboard</code>,{' '}
        <code>GET /api/v1/insight/comparison</code> and <code>GET /api/v1/insight/leadership</code>.
      </p>

      {board.loading ? <Loading what="evidence dashboard" /> : null}
      {board.error ? <ErrorNote message={board.error} /> : null}

      <div className="dash-tabs" role="tablist" aria-label="Dashboard sections">
        {VH_TABS.map((t) => (
          <button
            key={t}
            type="button"
            role="tab"
            aria-selected={t === tab}
            className={t === tab ? 'primary' : undefined}
            onClick={() => setTab(t)}
          >
            {t}
          </button>
        ))}
      </div>

      {tab === 'Overview' ? (
        <div className="dash-tab-panel">
          <div className="stat-grid">
            <StatCard label="Applications" value={d?.applications ?? '—'} icon={<MetricIcon d={ICONS.apps} />} />
            <StatCard label="Frameworks" value={d?.frameworks ?? '—'} icon={<MetricIcon d={ICONS.frameworks} />} />
          </div>

          <Section title="Integrity pass-rate over time">
            {trend.loading ? <Loading what="trend" /> : null}
            {trend.error ? <ErrorNote message={trend.error} /> : null}
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
      ) : null}

      {tab === 'Portfolio Comparison' ? (
        <div className="dash-tab-panel">
          <Section title="Cross-application comparison" actions={<Link to="/comparison">Open comparison →</Link>}>
            {comparison.loading ? <Loading what="comparison" /> : null}
            {comparison.error ? <ErrorNote message={comparison.error} /> : null}
            {cd ? (
              <>
                <p className="muted small">
                  Side-by-side compliance posture and control-gap list across every application.
                </p>
                <div className="stat-grid">
                  <StatCard label="Applications compared" value={cd.applications.length} />
                  <StatCard label="Frameworks compared" value={cd.frameworks.length} />
                  <StatCard label="Controls tracked" value={cd.controls.length} />
                  <StatCard label="Gaps" value={cd.gaps.length} />
                </div>
              </>
            ) : null}
          </Section>
        </div>
      ) : null}

      {tab === 'Leadership Rollup' ? (
        <div className="dash-tab-panel">
          <Section title="Executive rollup" actions={<Link to="/leadership">Open leadership dashboard →</Link>}>
            {leadership.loading ? <Loading what="leadership dashboard" /> : null}
            {leadership.error ? <ErrorNote message={leadership.error} /> : null}
            {ld ? (
              <>
                <p className="muted small">
                  Portfolio rollup of the actual deterministic compliance and completeness results across every
                  application.
                </p>
                <div className="stat-grid">
                  <StatCard label="Portfolio compliance" value={`${ld.compliancePct}%`} hint={`${ld.compliant}/${ld.expected} controls`} />
                  <StatCard label="Evidence completeness" value={`${ld.completenessPct}%`} hint={`${ld.covered}/${ld.expected} covered`} />
                  <StatCard label="Missing evidence" value={ld.missing} />
                  <StatCard label="Stale evidence" value={ld.stale} />
                </div>
              </>
            ) : null}
          </Section>
        </div>
      ) : null}
    </div>
  )
}

const CIO_TABS = ['Posture Overview', 'Framework Coverage', 'Risk Concentration'] as const
type CIOTab = (typeof CIO_TABS)[number]

/** CIO (role 'CIO') dashboard — same local-tab pattern as the other persona
 *  dashboards. Deliberately reuses the same data sources as VerticalHeadDashboard
 *  (getLeadershipDashboard, getComparison, getEvidenceDashboard) — the difference is
 *  framing/altitude (org-wide headline metrics for a board-level audience), not new
 *  data. No new API calls or aggregation logic: gaps[] has no severity/count field
 *  (checked ComparisonReport in api/types.ts — {applicationSlug, framework, controlId,
 *  status} only), so "Risk Concentration" shows the real total (gaps.length) and a
 *  capped, unsorted slice of the raw rows rather than inventing a severity ranking.
 *
 *  getComparison()/getLeadershipDashboard() are re-fetched here rather than shared
 *  with VerticalHeadDashboard's calls: the two components are mutually exclusive
 *  (only one persona's dashboard mounts at a time), and lifting those calls up to
 *  Dashboard() would mean editing the existing VH branch, which is out of scope. */
function CIODashboard({
  board,
  d,
  integrity,
  integrityTone,
  trend,
  integritySeries,
}: {
  board: AsyncBoard
  d: EvidenceDashboard | undefined
  integrity: EvidenceDashboard['integrity'] | undefined
  integrityTone: string
  trend: AsyncTrend
  integritySeries: Array<{ label: string; value: number }>
}) {
  const [tab, setTab] = useState<CIOTab>('Posture Overview')
  const leadership = useAsync(() => getLeadershipDashboard(), [])
  const comparison = useAsync(() => getComparison(), [])

  const ld = leadership.data
  const cd = comparison.data

  return (
    <div className="page dash-cio">
      <h1>Dashboard</h1>
      <p className="muted">
        Board-level posture snapshot — <code>GET /api/v1/insight/leadership</code>,{' '}
        <code>GET /api/v1/insight/comparison</code> and <code>GET /api/v1/evidence/dashboard</code>.
      </p>

      {board.loading ? <Loading what="evidence dashboard" /> : null}
      {board.error ? <ErrorNote message={board.error} /> : null}

      <div className="dash-tabs" role="tablist" aria-label="Dashboard sections">
        {CIO_TABS.map((t) => (
          <button
            key={t}
            type="button"
            role="tab"
            aria-selected={t === tab}
            className={t === tab ? 'primary' : undefined}
            onClick={() => setTab(t)}
          >
            {t}
          </button>
        ))}
      </div>

      {tab === 'Posture Overview' ? (
        <div className="dash-tab-panel">
          {leadership.loading ? <Loading what="leadership dashboard" /> : null}
          {leadership.error ? <ErrorNote message={leadership.error} /> : null}
          {ld ? (
            <div className="stat-grid">
              <StatCard label="Portfolio compliance" value={`${ld.compliancePct}%`} hint={`${ld.compliant}/${ld.expected} controls`} />
              <StatCard label="Evidence completeness" value={`${ld.completenessPct}%`} hint={`${ld.covered}/${ld.expected} covered`} />
              <StatCard label="Missing evidence" value={ld.missing} />
              <StatCard label="Stale evidence" value={ld.stale} />
            </div>
          ) : null}

          <Section title="Integrity pass-rate over time">
            {trend.loading ? <Loading what="trend" /> : null}
            {trend.error ? <ErrorNote message={trend.error} /> : null}
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
      ) : null}

      {tab === 'Framework Coverage' ? (
        <div className="dash-tab-panel">
          <div className="stat-grid">
            <StatCard label="Applications" value={d?.applications ?? '—'} icon={<MetricIcon d={ICONS.apps} />} />
            <StatCard label="Frameworks" value={d?.frameworks ?? '—'} icon={<MetricIcon d={ICONS.frameworks} />} />
          </div>

          <Section title="Framework coverage spread" actions={<Link to="/comparison">Open comparison →</Link>}>
            {comparison.loading ? <Loading what="comparison" /> : null}
            {comparison.error ? <ErrorNote message={comparison.error} /> : null}
            {cd ? (
              <>
                <p className="muted small">
                  Compliance-percentage spread per framework across every application — how consistently each
                  framework is applied org-wide.
                </p>
                <DataTable
                  className="dash-capped-table"
                  rows={cd.frameworks.slice(0, 5)}
                  rowKey={(f) => f.framework}
                  columns={[
                    { header: 'Framework', cell: (f) => f.framework },
                    { header: 'Min %', cell: (f) => `${f.minPct}%`, align: 'right' },
                    { header: 'Max %', cell: (f) => `${f.maxPct}%`, align: 'right' },
                    { header: 'Spread', cell: (f) => `${f.spreadPct}%`, align: 'right' },
                  ]}
                />
              </>
            ) : null}
          </Section>
        </div>
      ) : null}

      {tab === 'Risk Concentration' ? (
        <div className="dash-tab-panel dash-grid-2">
          <Section title="Control gaps" actions={<Link to="/comparison">Open comparison →</Link>}>
            {comparison.loading ? <Loading what="comparison" /> : null}
            {comparison.error ? <ErrorNote message={comparison.error} /> : null}
            {cd ? (
              <>
                <div className="stat-grid">
                  <StatCard label="Total gaps" value={cd.gaps.length} />
                </div>
                <DataTable
                  className="dash-capped-table"
                  rows={cd.gaps.slice(0, 6)}
                  rowKey={(g) => `${g.applicationSlug}|${g.framework}|${g.controlId}`}
                  columns={[
                    { header: 'Application', cell: (g) => g.applicationSlug },
                    { header: 'Framework', cell: (g) => g.framework },
                    { header: 'Control', cell: (g) => g.controlId },
                    { header: 'Status', cell: (g) => <StatusPill status={g.status} /> },
                  ]}
                />
              </>
            ) : null}
          </Section>

          <Section title="Org-wide integrity & freshness risk">
            {integrity && d ? (
              <>
                <p>
                  <StatusPill status={integrityTone} /> {integrity.mismatch} mismatch, {integrity.missingObject}{' '}
                  missing from store, {d.freshness.stale} evidence record{d.freshness.stale === 1 ? '' : 's'} stale.
                </p>
                <div className="stat-grid">
                  <StatCard label="Mismatch" value={integrity.mismatch} />
                  <StatCard label="Missing object" value={integrity.missingObject} />
                  <StatCard label="Stale evidence" value={d.freshness.stale} />
                </div>
              </>
            ) : null}
          </Section>
        </div>
      ) : null}
    </div>
  )
}

const ADMIN_TABS = ['System Health', 'Ingestion & Sources', 'Scope'] as const
type AdminTab = (typeof ADMIN_TABS)[number]

/** Admin (role 'ADMIN') dashboard — same local-tab pattern as the other persona
 *  dashboards. Scoped to operational/system signals already available from
 *  getEvidenceDashboard() and listRuns(): integrity, freshness, ingestion sources and
 *  scheduler activity, and the onboarded footprint (applications/frameworks/sources).
 *  No user/role management, access logs, admin-action audit trail, or system
 *  configuration — there's no backing API for any of that; the ADMIN persona isn't
 *  wired to admin-specific endpoints, so this dashboard doesn't invent any. */
function AdminDashboard({
  board,
  d,
  integrity,
  integrityTone,
}: {
  board: AsyncBoard
  d: EvidenceDashboard | undefined
  integrity: EvidenceDashboard['integrity'] | undefined
  integrityTone: string
}) {
  const [tab, setTab] = useState<AdminTab>('System Health')
  const runs = useAsync(() => listRuns(0, 15), [])

  return (
    <div className="page dash-admin">
      <h1>Dashboard</h1>
      <p className="muted">
        Operational snapshot — <code>GET /api/v1/evidence/dashboard</code> and{' '}
        <code>GET /api/v1/scheduler/runs</code>.
      </p>

      {board.loading ? <Loading what="evidence dashboard" /> : null}
      {board.error ? <ErrorNote message={board.error} /> : null}

      <div className="dash-tabs" role="tablist" aria-label="Dashboard sections">
        {ADMIN_TABS.map((t) => (
          <button
            key={t}
            type="button"
            role="tab"
            aria-selected={t === tab}
            className={t === tab ? 'primary' : undefined}
            onClick={() => setTab(t)}
          >
            {t}
          </button>
        ))}
      </div>

      {tab === 'System Health' ? (
        <div className="dash-tab-panel">
          <div className="stat-grid">
            <StatCard label="Evidence records" value={d?.records ?? '—'} icon={<MetricIcon d={ICONS.records} />} />
            <StatCard label="Versions" value={d?.versions ?? '—'} icon={<MetricIcon d={ICONS.versions} />} />
          </div>

          <div className="dash-grid-2">
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
          </div>
        </div>
      ) : null}

      {tab === 'Ingestion & Sources' ? (
        <div className="dash-tab-panel dash-grid-2">
          <Section title="Evidence by source" actions={<Link to="/evidence">Open repository →</Link>}>
            {d ? (
              <DataTable
                className="dash-capped-table dash-capped-table-tall"
                rows={d.bySource.slice(0, 12)}
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
                className="dash-capped-table dash-capped-table-tall"
                rows={runs.data.items}
                rowKey={(r) => r.runId}
                columns={[
                  {
                    header: 'Run',
                    cell: (r) => (
                      <Link className="dash-truncate" to={`/scheduler?run=${r.runId}`} title={r.runId}>
                        {r.runId}
                      </Link>
                    ),
                    className: 'dash-truncate-col',
                  },
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
      ) : null}

      {tab === 'Scope' ? (
        <div className="dash-tab-panel">
          <Section title="Operational footprint">
            <p className="muted small">Applications, frameworks and source systems currently onboarded.</p>
            <div className="stat-grid">
              <StatCard label="Applications" value={d?.applications ?? '—'} icon={<MetricIcon d={ICONS.apps} />} />
              <StatCard label="Frameworks" value={d?.frameworks ?? '—'} icon={<MetricIcon d={ICONS.frameworks} />} />
              <StatCard label="Sources" value={d?.sources ?? '—'} icon={<MetricIcon d={ICONS.sources} />} />
            </div>
          </Section>
        </div>
      ) : null}
    </div>
  )
}

function fmt(iso?: string | null): string {
  return iso ? new Date(iso).toLocaleString() : '—'
}
