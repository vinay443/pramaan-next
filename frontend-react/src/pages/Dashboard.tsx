import { useState } from 'react'
import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import {
  getAuditSchedule,
  getCompliance,
  getComparison,
  getEnterpriseDashboard,
  getEvidenceDashboard,
  getLeadershipDashboard,
  getTrend,
  listApplications,
  listRuns,
  transitionEvidenceLifecycle,
} from '../api/endpoints'
import { ComplianceView } from './Compliance'
import { LeadershipView } from './Leadership'
import { NationalEnterpriseView } from './NationalDashboard'
import { useAsync } from '../hooks/useAsync'
import { useAppOwnerData } from '../hooks/useAppOwnerData'
import { useControlResultsSummary } from '../hooks/useControlResultsSummary'
import { useCompletenessSummary } from '../hooks/useCompletenessSummary'
import { DataTable, ErrorNote, LineChart, Loading, Section, StatCard, StatusPill } from '../components/ui'
import { PERSONA_STORAGE_KEY } from './PersonaLogin'
import type { EvidenceDashboard, EvidenceView, LifecycleStateCounts } from '../api/types'
import {
  AUDIT_READINESS_BAND_THRESHOLD_PCT,
  AUDIT_READINESS_WEIGHTS,
  MOCK_APP_OWNER_COUNTS,
  MOCK_APP_OWNER_OVERVIEW,
  MOCK_APPLICATION_PROFILE,
  MOCK_AUDIT_READINESS,
  MOCK_FINDINGS,
  MOCK_REJECTIONS,
  MOCK_REMEDIATION,
  MOCK_WORK_QUEUE,
  MOCK_WORK_QUEUE_OPEN_COUNT,
  USE_MOCK_APP_OWNER_DATA,
} from './mockAppOwnerDashboard'

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
    return <AppOwnerDashboard staleAfterDays={d?.staleAfterDays ?? 90} />
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
type AsyncTrend = ReturnType<typeof useAsync<Awaited<ReturnType<typeof getTrend>>>>

const APP_OWNER_TABS = ['Evidence', 'Frameworks', 'Applications', 'Audit Readiness'] as const
type AppOwnerTab = (typeof APP_OWNER_TABS)[number]

/** "Draft" | "Submitted" | "Re-upload Requested" | "Closed" — same mapping as the
 *  backend's EvidenceLifecycleSummaryService (DRAFT/SUBMITTED kept as-is, REJECTED reads
 *  as "Re-upload Requested", APPROVED/EXPIRED/SUPERSEDED all read as "Closed"). */
function lifecycleLabel(state: string | undefined): string {
  switch (state) {
    case 'DRAFT':
      return 'Draft'
    case 'SUBMITTED':
      return 'Submitted'
    case 'REJECTED':
      return 'Re-upload Requested'
    default:
      return 'Closed'
  }
}

function ageDaysOf(iso: string | undefined): number | null {
  if (!iso) return null
  return Math.max(0, Math.round((Date.now() - Date.parse(iso)) / 86_400_000))
}

/** Computed — no Priority field exists on evidence; derived from lifecycle state + freshness
 *  so the Controls/Findings tables have something to rank/sort on, same idea as the POC. */
function priorityOf(e: EvidenceView, staleAfterDays: number): 'Critical' | 'High' | 'Medium' | 'Low' {
  if ((e.lifecycleState as string) === 'REJECTED') return 'Critical'
  const age = ageDaysOf(e.latest?.collectedAt ?? e.updatedAt)
  if (age !== null && age > staleAfterDays) return 'High'
  if ((e.lifecycleState as string) === 'SUBMITTED') return 'Medium'
  return 'Low'
}

/** Computed — evidence freshness expiry (last collected + the freshness window), used for
 *  both "Due" and "Expiry" since there is no separate due-date field in the data model. */
function expiryDateOf(e: EvidenceView, staleAfterDays: number): string | undefined {
  const base = e.latest?.collectedAt ?? e.updatedAt
  if (!base) return undefined
  return new Date(Date.parse(base) + staleAfterDays * 86_400_000).toISOString()
}

function fmtDate(iso: string | undefined): string {
  if (!iso) return '—'
  return new Date(iso).toLocaleDateString(undefined, { year: 'numeric', month: 'short', day: 'numeric' })
}

/** Overview's top KPI row (Draft/Submitted/Re-upload Requested) — also reused verbatim at
 *  the top of the Controls tab, matching the POC screenshots. */
function LifecycleKpiRow({ counts }: { counts: LifecycleStateCounts | undefined }) {
  return (
    <div className="dash-hero-row">
      <StatCard size="lg" label="Draft" value={counts?.draft ?? '—'} />
      <StatCard size="lg" label="Submitted" value={counts?.submitted ?? '—'} />
      <StatCard size="lg" label="Re-upload Req" value={counts?.rejected ?? '—'} />
    </div>
  )
}

/** Overview's status chip row (Draft/Submitted/Re-upload Requested/Closed) — also reused at
 *  the top of the Controls tab. */
function LifecycleChipRow({ counts }: { counts: LifecycleStateCounts | undefined }) {
  const closed = counts ? counts.approved + counts.expired + counts.superseded : undefined
  return (
    <div className="dash-chip-row">
      <span className="dash-chip dash-chip-muted">
        Draft Evidence <strong>{counts?.draft ?? '—'}</strong>
      </span>
      <span className="dash-chip dash-chip-warn">
        Submitted Evidence <strong>{counts?.submitted ?? '—'}</strong>
      </span>
      <span className="dash-chip dash-chip-bad">
        Re-upload Requested <strong>{counts?.rejected ?? '—'}</strong>
      </span>
      <span className="dash-chip dash-chip-ok">
        Closed <strong>{closed ?? '—'}</strong>
      </span>
    </div>
  )
}

/** App Owner (role 'APP') dashboard — visually matched to the ECS POC's Application Owner
 *  Dashboard (dark card KPI layout), wired to PRAMAAN's real evidence lifecycle, trend
 *  (closure) and compliance endpoints — see useAppOwnerData for the shared fetch. Regrouped
 *  into four tabs (Evidence, Frameworks, Applications, Audit Readiness); each reuses the same
 *  sub-components/JSX the original six-tab layout used, just moved and regrouped. */
function AppOwnerDashboard({ staleAfterDays }: { staleAfterDays: number }) {
  const [tab, setTab] = useState<AppOwnerTab>('Evidence')
  const { app, loading: appLoading, error: appError } = useOwnedApplication()
  const owner = useAppOwnerData(app?.slug)

  const counts = USE_MOCK_APP_OWNER_DATA ? MOCK_APP_OWNER_COUNTS : owner.lifecycle.data?.counts
  const closure = owner.trend.data?.closure
  const pendingAging = USE_MOCK_APP_OWNER_DATA ? MOCK_APP_OWNER_OVERVIEW.pendingAging : owner.lifecycle.data?.pendingAging
  const auditorSla = USE_MOCK_APP_OWNER_DATA ? MOCK_APP_OWNER_OVERVIEW.auditorSla : owner.lifecycle.data?.auditorSla
  const closureRatePct = USE_MOCK_APP_OWNER_DATA
    ? MOCK_APP_OWNER_OVERVIEW.closureRatePct
    : closure && closure.approvals + closure.rejections > 0
      ? Math.round((closure.approvals / (closure.approvals + closure.rejections)) * 1000) / 10
      : null
  const avgReviewTimeDays = USE_MOCK_APP_OWNER_DATA ? MOCK_APP_OWNER_OVERVIEW.avgReviewTimeDays : closure?.avgDaysToApprove
  const rejectionTrendPct = USE_MOCK_APP_OWNER_DATA ? MOCK_APP_OWNER_OVERVIEW.rejectionTrendPct : closure?.rejectionTrendPct

  const items = owner.evidence.data?.items ?? []
  const rejectionReasonByEvidenceId = new Map(
    (owner.lifecycle.data?.rejections ?? []).map((r) => [r.evidenceId, r]),
  )
  const rejectedEvidenceCount = USE_MOCK_APP_OWNER_DATA ? MOCK_APP_OWNER_OVERVIEW.highlights.rejectedEvidence : counts?.rejected
  const expiringCount = USE_MOCK_APP_OWNER_DATA
    ? MOCK_APP_OWNER_OVERVIEW.highlights.expiringStale
    : items.filter((e) => {
        const age = ageDaysOf(e.latest?.collectedAt ?? e.updatedAt)
        return age !== null && age > staleAfterDays
      }).length

  async function doTransition(evidenceId: string, action: 'SUBMIT' | 'RESET') {
    await transitionEvidenceLifecycle(evidenceId, action)
    owner.evidence.reload()
    owner.lifecycle.reload()
  }

  return (
    <div className="page dash-app-owner">
      <div className="own-header">
        <div>
          <h1>Application Owner Dashboard</h1>
          <p className="muted">Governance posture and prioritized actions</p>
        </div>
        <div className="own-header-user">
          <span className="own-header-name">{app?.owner ?? 'App Owner'}</span>
          <span className="pill pill-muted">Application Owner</span>
        </div>
      </div>

      {appLoading ? <Loading what="your application" /> : null}
      {appError ? <ErrorNote message={appError} /> : null}
      {!appLoading && !appError && !app ? <p className="muted">No application is assigned to you.</p> : null}

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

      {owner.lifecycle.error ? <ErrorNote message={owner.lifecycle.error} /> : null}
      {owner.trend.error ? <ErrorNote message={owner.trend.error} /> : null}
      {owner.evidence.error ? <ErrorNote message={owner.evidence.error} /> : null}

      {tab === 'Evidence' ? (
        <div className="dash-tab-panel">
          {owner.lifecycle.loading ? <Loading what="evidence lifecycle summary" /> : null}
          <LifecycleKpiRow counts={counts} />

          <div className="stat-grid dash-rate-row">
            <StatCard label="Closure rate" value={closureRatePct === null ? '—' : `${closureRatePct}%`} hint="In-scope evidence" />
            <StatCard label="Avg review time" value={avgReviewTimeDays == null ? '—' : `${avgReviewTimeDays}d`} hint="Auditor turnaround" />
            <StatCard
              label="Rejection trend"
              value={rejectionTrendPct == null ? '—' : `${rejectionTrendPct > 0 ? '↑' : '↓'} ${Math.abs(rejectionTrendPct)}%`}
              hint="Month over month"
            />
            <StatCard
              label="Pending aging"
              value={pendingAging?.count ?? '—'}
              hint={pendingAging?.avgDaysInQueue == null ? 'items' : `Avg ${pendingAging.avgDaysInQueue} days in queue`}
            />
            <StatCard
              label="Auditor SLA"
              value={auditorSla?.pct == null ? '—' : `${auditorSla.pct}%`}
              hint={auditorSla ? `Within ${auditorSla.targetDays}-day target` : undefined}
            />
          </div>

          <div className="dash-urgency-row">
            <UrgencyCard
              tone="red"
              value={rejectedEvidenceCount ?? '—'}
              label="Rejected Evidence"
              context="Resubmission needed."
            />
            <UrgencyCard tone="blue" value={expiringCount ?? '—'} label="Expiring / Stale" context="Renew before audit." />
          </div>

          <LifecycleChipRow counts={counts} />

          {USE_MOCK_APP_OWNER_DATA ? (
            <p className="muted small">Demo data — see mockAppOwnerDashboard.ts.</p>
          ) : (
            <p className="muted small">
              Closure metrics scoped to this application (<code>GET /api/v1/insight/trend?applicationSlug=</code>).
              Auditor SLA and Pending Aging are computed from evidence lifecycle timestamps (
              <code>GET /api/v1/insight/evidence-lifecycle/summary</code>) — there is no direct backend field for
              either.
            </p>
          )}

          <EvidenceRejectionsSection
            rejections={USE_MOCK_APP_OWNER_DATA ? MOCK_REJECTIONS : owner.lifecycle.data?.rejections}
            loading={USE_MOCK_APP_OWNER_DATA ? false : owner.lifecycle.loading}
          />

          <Section title="Needs Resubmission">
            <div className="own-remediation-list">
              {USE_MOCK_APP_OWNER_DATA ? null : owner.evidence.loading ? <Loading what="remediation items" /> : null}
              {USE_MOCK_APP_OWNER_DATA
                ? MOCK_REMEDIATION.map((r) => (
                    <div key={r.id} className="card own-remediation-card">
                      <div>
                        <strong>{r.framework} · {r.controlCode}</strong>
                        <p className="muted small">{r.application} — resubmission required</p>
                      </div>
                      <Link
                        className="primary-link-btn"
                        to={`/bulk-upload?${new URLSearchParams({ applicationSlug: r.applicationSlugForDeepLink, framework: r.framework, controlId: r.controlCode }).toString()}`}
                      >
                        Resubmit Evidence
                      </Link>
                    </div>
                  ))
                : items
                    .filter((e) => e.lifecycleState === 'REJECTED')
                    .map((e) => (
                      <div key={e.evidenceId} className="card own-remediation-card">
                        <div>
                          <strong>{e.framework} · {e.controlId}</strong>
                          <p className="muted small">{app?.name ?? '—'} — resubmission required</p>
                        </div>
                        <Link
                          className="primary-link-btn"
                          to={`/bulk-upload?${new URLSearchParams({ applicationSlug: app?.slug ?? '', framework: e.framework, controlId: e.controlId }).toString()}`}
                        >
                          Resubmit Evidence
                        </Link>
                      </div>
                    ))}
              {!USE_MOCK_APP_OWNER_DATA && !owner.evidence.loading && items.filter((e) => e.lifecycleState === 'REJECTED').length === 0 ? (
                <p className="muted">Nothing needs resubmission right now.</p>
              ) : null}
            </div>
          </Section>
        </div>
      ) : null}

      {tab === 'Frameworks' ? (
        <div className="dash-tab-panel">
          <OwnedAppScope>
            {(a) => (
              <Section title="Framework Compliance">
                <ComplianceView slug={a.slug} actionable />
              </Section>
            )}
          </OwnedAppScope>

          <Section
            title="Pending Actions Work Queue"
            actions={
              <span className="pill pill-warn">
                {USE_MOCK_APP_OWNER_DATA
                  ? MOCK_WORK_QUEUE_OPEN_COUNT
                  : items.filter((e) => e.lifecycleState !== 'APPROVED').length}{' '}
                open
              </span>
            }
          >
            <p className="muted small">Consolidated across all frameworks — evidence lifecycle joined with this application's controls.</p>
            {USE_MOCK_APP_OWNER_DATA ? null : owner.evidence.loading ? <Loading what="pending actions" /> : null}
            {USE_MOCK_APP_OWNER_DATA ? (
              <DataTable
                rows={MOCK_WORK_QUEUE}
                rowKey={(r) => r.id}
                columns={[
                  { header: 'Framework', cell: (r) => r.framework },
                  { header: 'Application', cell: (r) => r.application },
                  { header: 'Control', cell: (r) => r.controlCode },
                  { header: 'Evidence', cell: (r) => r.evidenceFile },
                  { header: 'Action', cell: (r) => <span className="pill pill-muted">{r.action}</span> },
                  { header: 'Priority', cell: (r) => <StatusPill status={r.priority} tone="bad" /> },
                  { header: 'Aging', cell: (r) => r.aging, align: 'right' },
                  { header: 'Status', cell: (r) => <StatusPill status={r.status} /> },
                  { header: 'Due date', cell: (r) => r.due },
                  { header: 'Comments', cell: (r) => r.comments },
                  { header: 'Submitted', cell: (r) => r.submitted },
                  { header: 'Expiry', cell: (r) => r.expiry },
                  {
                    header: 'Actions',
                    cell: () => (
                      <div className="row-actions">
                        <button type="button" disabled>
                          View
                        </button>
                        <button type="button" disabled>
                          Submit
                        </button>
                        <button type="button" disabled>
                          Cancel
                        </button>
                      </div>
                    ),
                  },
                ]}
              />
            ) : (
              <DataTable
                rows={items.filter((e) => ['DRAFT', 'SUBMITTED', 'REJECTED'].includes(e.lifecycleState as string))}
                rowKey={(e) => e.evidenceId}
                columns={[
                  { header: 'Framework', cell: (e) => e.framework },
                  { header: 'Application', cell: () => app?.name ?? '—' },
                  { header: 'Control', cell: (e) => e.controlId },
                  { header: 'Evidence', cell: (e) => e.title || e.evidenceKey },
                  {
                    header: 'Action',
                    cell: (e) => {
                      const age = ageDaysOf(e.latest?.collectedAt ?? e.updatedAt)
                      return age !== null && age > staleAfterDays ? <span className="pill pill-muted">Expiring evidence</span> : '—'
                    },
                  },
                  { header: 'Priority', cell: (e) => <StatusPill status={priorityOf(e, staleAfterDays)} tone={priorityOf(e, staleAfterDays) === 'Critical' ? 'bad' : priorityOf(e, staleAfterDays) === 'High' ? 'warn' : 'muted'} /> },
                  { header: 'Aging', cell: (e) => `${ageDaysOf(e.updatedAt) ?? '—'}d`, align: 'right' },
                  { header: 'Status', cell: (e) => <StatusPill status={lifecycleLabel(e.lifecycleState as string)} /> },
                  { header: 'Due date', cell: (e) => fmtDate(expiryDateOf(e, staleAfterDays)) },
                  { header: 'Comments', cell: (e) => rejectionReasonByEvidenceId.get(e.evidenceId)?.reason ?? '—' },
                  { header: 'Submitted', cell: (e) => fmtDate(e.updatedAt) },
                  { header: 'Expiry', cell: (e) => fmtDate(expiryDateOf(e, staleAfterDays)) },
                  {
                    header: 'Actions',
                    cell: (e) => (
                      <div className="row-actions">
                        <Link to={`/evidence/${e.evidenceId}`}>View</Link>
                        <button type="button" disabled={e.lifecycleState !== 'DRAFT'} onClick={() => doTransition(e.evidenceId, 'SUBMIT')}>
                          Submit
                        </button>
                        <button type="button" disabled={e.lifecycleState !== 'SUBMITTED'} onClick={() => doTransition(e.evidenceId, 'RESET')}>
                          Cancel
                        </button>
                      </div>
                    ),
                  },
                ]}
              />
            )}
          </Section>

          <Section title="Critical Findings">
            {USE_MOCK_APP_OWNER_DATA ? null : owner.evidence.loading ? <Loading what="findings" /> : null}
            <div className="dash-tile-grid own-findings-grid">
              {USE_MOCK_APP_OWNER_DATA
                ? MOCK_FINDINGS.map((f) => (
                    <div key={f.id} className="card own-finding-card">
                      <div className="own-finding-head">
                        <strong>{f.framework} · {f.controlCode}</strong>
                        <StatusPill status={f.priority} tone="bad" />
                      </div>
                      <p className="muted small">
                        {f.application} — {f.description}
                      </p>
                      <button type="button" disabled>
                        Review →
                      </button>
                    </div>
                  ))
                : items
                    .filter((e) => e.lifecycleState === 'REJECTED' || priorityOf(e, staleAfterDays) === 'High')
                    .map((e) => {
                      const priority = priorityOf(e, staleAfterDays)
                      const reason = rejectionReasonByEvidenceId.get(e.evidenceId)?.reason
                      return (
                        <div key={e.evidenceId} className="card own-finding-card">
                          <div className="own-finding-head">
                            <strong>{e.framework} · {e.controlId}</strong>
                            <StatusPill status={priority} tone={priority === 'Critical' ? 'bad' : 'warn'} />
                          </div>
                          <p className="muted small">
                            {app?.name ?? '—'} — {reason ?? 'Evidence aging past freshness threshold; renew before audit.'}
                          </p>
                          <Link to={`/evidence/${e.evidenceId}`}>Review →</Link>
                        </div>
                      )
                    })}
              {!USE_MOCK_APP_OWNER_DATA &&
              !owner.evidence.loading &&
              items.filter((e) => e.lifecycleState === 'REJECTED' || priorityOf(e, staleAfterDays) === 'High').length === 0 ? (
                <p className="muted">No prioritized findings for this application right now.</p>
              ) : null}
            </div>
          </Section>
        </div>
      ) : null}

      {tab === 'Applications' ? (
        <div className="dash-tab-panel">
          {app ? (
            <>
              <Section title="Application Profile">
                <div className="stat-grid">
                  <StatCard label="Application" value={app.name} />
                  <StatCard label="Criticality" value={app.criticality ?? '—'} />
                  <StatCard label="Business Unit" value={app.businessUnit ?? '—'} />
                  <StatCard label="Region" value={MOCK_APPLICATION_PROFILE.region} />
                </div>
              </Section>
              <UpcomingAuditsSection slug={app.slug} />
            </>
          ) : null}
        </div>
      ) : null}

      {tab === 'Audit Readiness' ? (
        <div className="dash-tab-panel">{app ? <AuditReadinessTab slug={app.slug} counts={counts} /> : null}</div>
      ) : null}
    </div>
  )
}

/** Applications tab — audits from GET /api/v1/insight/audit-schedule scoped to one application. */
function UpcomingAuditsSection({ slug }: { slug: string }) {
  const schedule = useAsync(() => getAuditSchedule(), [])
  const audits = (schedule.data?.audits ?? []).filter((a) => a.applicationSlugs.includes(slug))

  return (
    <Section title="Upcoming Audits">
      {schedule.loading ? <Loading what="audit schedule" /> : null}
      {schedule.error ? <ErrorNote message={schedule.error} /> : null}
      {schedule.data ? (
        audits.length === 0 ? (
          <p className="muted">No upcoming audits scheduled for this application.</p>
        ) : (
          <DataTable
            rows={audits}
            rowKey={(a) => a.id}
            columns={[
              { header: 'Framework', cell: (a) => a.framework },
              { header: 'Audit', cell: (a) => a.auditName },
              { header: 'Scheduled date', cell: (a) => fmtDate(a.scheduledDate) },
              { header: 'Readiness', cell: (a) => `${a.readyCount}/${a.totalCount} ready`, align: 'right' },
            ]}
          />
        )
      ) : null}
      <p className="muted small">
        <code>GET /api/v1/insight/audit-schedule</code>, filtered to this application.
      </p>
    </Section>
  )
}

/** Audit Readiness tab — a composite score (Control Coverage 50% + Approved Evidence 30% +
 *  Freshness 20%) scoped to one application. Control Coverage reuses GET /insight/compliance;
 *  Approved Evidence reuses the evidence-lifecycle counts already fetched for the Evidence tab;
 *  Freshness has no per-application backend source yet, so it's a mock placeholder — see
 *  mockAppOwnerDashboard.ts. */
function AuditReadinessTab({ slug, counts }: { slug: string; counts: LifecycleStateCounts | undefined }) {
  const compliance = useAsync(() => getCompliance(slug), [slug])
  const controlCoveragePct = compliance.data?.compliancePct ?? 0
  const totalEvidence = counts
    ? counts.draft + counts.submitted + counts.approved + counts.rejected + counts.expired + counts.superseded
    : 0
  const approvedEvidencePct =
    counts && totalEvidence > 0 ? Math.round((counts.approved / totalEvidence) * 1000) / 10 : 0
  const freshnessPct = MOCK_AUDIT_READINESS.freshnessPct
  const composite =
    Math.round(
      (controlCoveragePct * AUDIT_READINESS_WEIGHTS.controlCoverage +
        approvedEvidencePct * AUDIT_READINESS_WEIGHTS.approvedEvidence +
        freshnessPct * AUDIT_READINESS_WEIGHTS.freshness) *
        10,
    ) / 10
  const band = composite >= AUDIT_READINESS_BAND_THRESHOLD_PCT ? 'Ready' : 'At Risk'

  return (
    <>
      {compliance.loading ? <Loading what="compliance" /> : null}
      {compliance.error ? <ErrorNote message={compliance.error} /> : null}

      <Section title="Audit Readiness Score">
        <div className="stat-grid">
          <StatCard size="lg" label="Composite score" value={`${composite}%`} hint="Coverage 50% · Approved evidence 30% · Freshness 20%" />
          <StatCard label="Control coverage" value={`${controlCoveragePct}%`} hint="GET /api/v1/insight/compliance" />
          <StatCard label="Approved evidence" value={`${approvedEvidencePct}%`} hint="Evidence lifecycle summary" />
          <StatCard label="Freshness" value={`${freshnessPct}%`} hint="Demo placeholder" />
        </div>
        <p>
          <StatusPill status={band} tone={band === 'Ready' ? 'ok' : 'bad'} />{' '}
          {band === 'Ready'
            ? 'This application clears the readiness bar for its next audit.'
            : 'This application is below the readiness bar — prioritize open items before the next audit.'}
        </p>
      </Section>

      <Section title="Coverage by Framework">
        {compliance.data ? (
          compliance.data.byFramework.length === 0 ? (
            <p className="muted">No frameworks in scope.</p>
          ) : (
            <DataTable
              rows={compliance.data.byFramework}
              rowKey={(f) => f.framework}
              columns={[
                { header: 'Framework', cell: (f) => f.framework },
                { header: 'Expected', cell: (f) => f.expected, align: 'right' },
                { header: 'Compliant', cell: (f) => f.compliant, align: 'right' },
                { header: 'Coverage %', cell: (f) => `${f.compliancePct}%`, align: 'right' },
              ]}
            />
          )
        ) : null}
      </Section>
    </>
  )
}

/** Collapsible "Evidence Rejections" table — the app's rejection audit trail from
 *  GET /api/v1/insight/evidence-lifecycle/summary. */
function EvidenceRejectionsSection({
  rejections,
  loading,
}: {
  rejections: import('../api/types').RejectionAuditRow[] | undefined
  loading: boolean
}) {
  const [expanded, setExpanded] = useState(true)
  return (
    <Section
      title={`Evidence Rejections${rejections ? ` (${rejections.length})` : ''}`}
      actions={
        <button type="button" className="ghost" onClick={() => setExpanded((v) => !v)}>
          {expanded ? '▲ Collapse' : '▼ Expand'}
        </button>
      }
    >
      {loading ? <Loading what="evidence rejections" /> : null}
      {expanded && rejections ? (
        rejections.length > 0 ? (
          <DataTable
            rows={rejections}
            rowKey={(r) => `${r.evidenceId}-${r.rejectedAt}`}
            columns={[
              { header: 'Framework', cell: (r) => r.framework },
              { header: 'Control', cell: (r) => r.controlId },
              { header: 'Reason', cell: (r) => r.reason ?? '—' },
              { header: 'Rejected By', cell: (r) => r.rejectedBy ?? '—' },
              { header: 'Rejected At', cell: (r) => fmt(r.rejectedAt) },
              { header: 'Workflow State', cell: (r) => <StatusPill status={r.workflowState} /> },
            ]}
          />
        ) : (
          <p className="muted">No rejected evidence for this application.</p>
        )
      ) : null}
      <p className="muted small">
        From the evidence lifecycle audit trail, filtered to rejections (
        <code>GET /api/v1/insight/evidence-lifecycle/summary</code>).
      </p>
    </Section>
  )
}

/** The App Owner's application. The persona login carries no application identity yet,
 *  so this takes the first active application (the same one the Applications list sorts
 *  first). Swap this single lookup once the backend exposes "my applications". */
function useOwnedApplication() {
  const apps = useAsync(() => listApplications(), [])
  const app = (apps.data ?? []).find((a) => a.active) ?? apps.data?.[0]
  return { app, loading: apps.loading, error: apps.error }
}

function OwnedAppScope({ children }: { children: (app: { slug: string; name: string }) => ReactNode }) {
  const { app, loading, error } = useOwnedApplication()
  if (loading) return <Loading what="your application" />
  if (error) return <ErrorNote message={error} />
  if (!app) return <p className="muted">No application is assigned to you.</p>
  return (
    <>
      <p className="muted">
        Scoped to your application: <strong>{app.name}</strong>
      </p>
      {children(app)}
    </>
  )
}


const AUDITOR_TABS = ['Overview', 'Control & Compliance', 'Audit Readiness', 'Closure & Trend'] as const
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

      {tab === 'Closure & Trend' ? (
        <div className="dash-tab-panel">
          <ClosureTrend />
        </div>
      ) : null}
    </div>
  )
}

/** Auditor "Closure & Trend": the closure half of /trend (evidence approval velocity from the
 *  lifecycle audit trail) plus the org-wide compliance % line for context. Deliberately no
 *  business-unit / region cuts, risk ranking or collection throughput. */
function ClosureTrend() {
  const report = useAsync(() => getTrend(), [])
  const d = report.data
  const series = d
    ? [...d.points, d.current].map((p) => ({
        label: new Date(p.takenAt).toLocaleDateString(undefined, { month: 'short', day: 'numeric' }),
        value: p.compliancePct,
      }))
    : []

  return (
    <>
      {report.loading ? <Loading what="closure metrics" /> : null}
      {report.error ? <ErrorNote message={report.error} /> : null}
      {d ? (
        <>
          <Section title="Evidence closure" actions={<Link to="/evidence-lifecycle">Open evidence lifecycle →</Link>}>
            <div className="stat-grid">
              <StatCard label="Approvals" value={d.closure.approvals} />
              <StatCard label="Avg days to approve" value={d.closure.avgDaysToApprove ?? '—'} />
              <StatCard label="Rejections" value={d.closure.rejections} />
            </div>
            <p className="muted small">
              From the evidence lifecycle audit trail (<code>GET /api/v1/insight/trend</code>).
            </p>
          </Section>

          <Section title="Compliance % over time (org-wide)">
            {series.length > 1 ? (
              <LineChart
                points={series}
                ariaLabel="org-wide compliance percentage per snapshot"
                yMax={100}
                valueSuffix="%"
              />
            ) : (
              <p className="muted">No trend history recorded yet.</p>
            )}
            <p className="muted small">Latest point is the live rollup.</p>
          </Section>
        </>
      ) : null}
    </>
  )
}

const FH_TABS =['Overview', 'Control & Risk', 'Attention Needed', 'Leadership Rollup'] as const
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

      {tab === 'Leadership Rollup' ? (
        <div className="dash-tab-panel">
          <FunctionRollup />
        </div>
      ) : null}
    </div>
  )
}

/** Functional Head "Leadership Rollup": the Leadership view scoped to one function, plus that
 *  function's compliance trend. The persona carries no function yet, so function = application
 *  businessUnit, chosen with the selector below (defaults to the first one). */
function FunctionRollup() {
  const apps = useAsync(() => listApplications(), [])
  const units = [...new Set((apps.data ?? []).map((a) => a.businessUnit).filter((b): b is string => !!b))].sort()
  const [picked, setPicked] = useState('')
  const unit = picked || units[0] || ''

  const trend = useAsync(() => (unit ? getTrend(unit) : Promise.resolve(undefined)), [unit])
  const slugs = new Set((apps.data ?? []).filter((a) => a.businessUnit === unit).map((a) => a.slug))
  const series = trend.data
    ? [...trend.data.points, trend.data.current].map((p) => ({
        label: new Date(p.takenAt).toLocaleDateString(undefined, { month: 'short', day: 'numeric' }),
        value: p.compliancePct,
      }))
    : []

  if (apps.loading) return <Loading what="functions" />
  if (apps.error) return <ErrorNote message={apps.error} />
  if (units.length === 0) return <p className="muted">No function (business unit) is defined for any application.</p>

  return (
    <>
      <div className="filter-row">
        <label>
          Function
          <select aria-label="Function" value={unit} onChange={(e) => setPicked(e.target.value)}>
            {units.map((u) => (
              <option key={u} value={u}>
                {u}
              </option>
            ))}
          </select>
        </label>
        <Link to="/leadership">See full Leadership dashboard →</Link>
      </div>

      <Section title={`${unit} — compliance trend`}>
        {trend.loading ? <Loading what="trend" /> : null}
        {trend.error ? <ErrorNote message={trend.error} /> : null}
        {trend.data ? (
          series.length > 1 ? (
            <>
              <LineChart points={series} ariaLabel={`${unit} compliance percentage per snapshot`} yMax={100} valueSuffix="%" />
              <p className="muted small">
                <code>GET /api/v1/insight/trend?businessUnit=</code>. Latest point is the live figure.
              </p>
            </>
          ) : (
            <p className="muted">No trend history recorded for this function yet.</p>
          )
        ) : null}
      </Section>

      <LeadershipView slugs={slugs} />
    </>
  )
}

const VH_TABS =['Overview', 'Portfolio Comparison', 'Leadership Rollup'] as const
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

  // The persona carries no vertical yet, so a vertical = the business units ticked here
  // (default: all). Every scoped call below sends them as repeatable ?businessUnit=.
  const apps = useAsync(() => listApplications(), [])
  const allUnits = [...new Set((apps.data ?? []).map((a) => a.businessUnit).filter((b): b is string => !!b))].sort()
  const [picked, setPicked] = useState<string[] | null>(null)
  const units = picked ?? allUnits
  const scope = units.length > 0 ? units : undefined
  const scopeKey = units.join('|')
  function toggleUnit(u: string) {
    const next = units.includes(u) ? units.filter((x) => x !== u) : [...units, u]
    if (next.length > 0) setPicked(next) // keep at least one: an empty scope means "everything" to the API
  }

  const complianceTrend = useAsync(() => (apps.data ? getTrend(scope) : Promise.resolve(undefined)), [scopeKey, !!apps.data])
  const enterprise = useAsync(() => (apps.data ? getEnterpriseDashboard(scope) : Promise.resolve(undefined)), [scopeKey, !!apps.data])
  const comparison = useAsync(() => (apps.data ? getComparison(undefined, undefined, scope) : Promise.resolve(undefined)), [scopeKey, !!apps.data])
  const leadership = useAsync(() => (apps.data ? getLeadershipDashboard(scope) : Promise.resolve(undefined)), [scopeKey, !!apps.data])

  const cd = comparison.data
  const ld = leadership.data
  const ed = enterprise.data
  const complianceSeries = complianceTrend.data
    ? [...complianceTrend.data.points, complianceTrend.data.current].map((p) => ({
        label: new Date(p.takenAt).toLocaleDateString(undefined, { month: 'short', day: 'numeric' }),
        value: p.compliancePct,
      }))
    : []

  return (
    <div className="page dash-vh">
      <h1>Dashboard</h1>
      <p className="muted">
        Portfolio trend snapshot — <code>GET /api/v1/evidence/dashboard</code>,{' '}
        <code>GET /api/v1/insight/comparison</code> and <code>GET /api/v1/insight/leadership</code>.
      </p>

      {board.loading ? <Loading what="evidence dashboard" /> : null}
      {board.error ? <ErrorNote message={board.error} /> : null}

      {allUnits.length > 0 ? (
        <fieldset className="filter-row">
          <legend>Vertical scope — business units</legend>
          {allUnits.map((u) => (
            <label key={u}>
              <input type="checkbox" checked={units.includes(u)} onChange={() => toggleUnit(u)} /> {u}
            </label>
          ))}
        </fieldset>
      ) : null}

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

          <div className="chart-grid">
            <Section title="Compliance % over time">
              {complianceTrend.loading ? <Loading what="trend" /> : null}
              {complianceTrend.error ? <ErrorNote message={complianceTrend.error} /> : null}
              {complianceTrend.data ? (
                complianceSeries.length > 1 ? (
                  <>
                    <LineChart
                      points={complianceSeries}
                      ariaLabel="vertical compliance percentage per snapshot"
                      yMax={100}
                      valueSuffix="%"
                    />
                    <p className="muted small">
                      Selected business units only (<code>GET /api/v1/insight/trend?businessUnit=</code>). Latest
                      point is the live figure.
                    </p>
                  </>
                ) : (
                  <p className="muted">No trend history recorded for the selected business units yet.</p>
                )
              ) : null}
            </Section>

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
                  <p className="muted small">
                    % of checked current versions that hashed intact per snapshot. Estate-wide: integrity is not
                    tracked per business unit.
                  </p>
                </>
              ) : null}
            </Section>
          </div>
        </div>
      ) : null}

      {tab === 'Portfolio Comparison' ? (
        <div className="dash-tab-panel">
          <Section title="Business-unit comparison" actions={<Link to="/comparison">Open comparison →</Link>}>
            {enterprise.loading ? <Loading what="business-unit comparison" /> : null}
            {enterprise.error ? <ErrorNote message={enterprise.error} /> : null}
            {ed ? (
              <>
                <p className="muted small">
                  Business units in this vertical compared against each other, lowest compliance first
                  (<code>GET /api/v1/insight/enterprise?businessUnit=</code>). Difference is vs. the vertical's own
                  compliance ({ed.portfolio.compliancePct}%).
                </p>
                <DataTable
                  rows={[...ed.byBusinessUnit].sort((a, b) => a.compliancePct - b.compliancePct)}
                  rowKey={(g) => g.key}
                  columns={[
                    { header: 'Business unit', cell: (g) => g.key },
                    { header: 'Applications', cell: (g) => g.applications, align: 'right' },
                    { header: 'Compliance %', cell: (g) => `${g.compliancePct}%`, align: 'right' },
                    { header: 'Completeness %', cell: (g) => `${g.completenessPct}%`, align: 'right' },
                    {
                      header: 'vs. vertical',
                      cell: (g) => {
                        const v = Math.round((g.compliancePct - ed.portfolio.compliancePct) * 10) / 10
                        return `${v > 0 ? '+' : ''}${v} pts`
                      },
                      align: 'right',
                    },
                  ]}
                />
              </>
            ) : null}
            {comparison.loading ? <Loading what="comparison" /> : null}
            {comparison.error ? (
              <p className="muted small">Control-level comparison unavailable: {comparison.error}</p>
            ) : null}
            {cd ? (
              <div className="stat-grid">
                <StatCard label="Frameworks compared" value={cd.frameworks.length} />
                <StatCard label="Controls tracked" value={cd.controls.length} />
                <StatCard label="Gaps" value={cd.gaps.length} />
              </div>
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
                  Rollup of the actual deterministic compliance and completeness results across the applications in
                  the selected business units (<code>GET /api/v1/insight/leadership?businessUnit=</code>).
                </p>
                <div className="stat-grid">
                  <StatCard label="Vertical compliance" value={`${ld.compliancePct}%`} hint={`${ld.compliant}/${ld.expected} controls`} />
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

const CIO_TABS = ['Posture Overview', 'Framework Coverage', 'Risk Concentration', 'National & Enterprise'] as const
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
  const complianceSeries = trend.data
    ? [...trend.data.points, trend.data.current].map((p) => ({
        label: new Date(p.takenAt).toLocaleDateString(undefined, { month: 'short', day: 'numeric' }),
        value: p.compliancePct,
      }))
    : []

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

      {tab === 'National & Enterprise' ? (
        <div className="dash-tab-panel">
          <Section title="Org-wide compliance % trend" actions={<Link to="/trend">Open trend →</Link>}>
            {trend.loading ? <Loading what="trend" /> : null}
            {trend.error ? <ErrorNote message={trend.error} /> : null}
            {trend.data ? (
              complianceSeries.length > 1 ? (
                <>
                  <LineChart
                    points={complianceSeries}
                    ariaLabel="org-wide compliance percentage per snapshot"
                    yMax={100}
                    valueSuffix="%"
                  />
                  <p className="muted small">
                    Every stored snapshot, oldest to newest (<code>GET /api/v1/insight/trend</code>); the latest
                    point is the live figure.
                  </p>
                </>
              ) : (
                <p className="muted">No trend history recorded yet.</p>
              )
            ) : null}
          </Section>

          <NationalEnterpriseView />
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
