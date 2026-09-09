import { getTrend } from '../api/endpoints'
import { useAsync } from '../hooks/useAsync'
import { DataTable, ErrorNote, Loading, Section, StatCard } from '../components/ui'

export function Trend() {
  const report = useAsync(() => getTrend(), [])
  const d = report.data
  const current = d?.current
  const first = d && d.points.length > 0 ? d.points[0] : undefined
  const delta =
    current && first ? Math.round((current.compliancePct - first.compliancePct) * 10) / 10 : undefined

  // simple inline sparkline
  const spark = (() => {
    if (!d || d.points.length < 2) return null
    const w = 320
    const h = 60
    const xs = d.points.map((_, i) => (i / (d.points.length - 1)) * w)
    const ys = d.points.map((p) => h - (p.compliancePct / 100) * h)
    const path = xs.map((x, i) => `${i === 0 ? 'M' : 'L'}${x.toFixed(1)},${ys[i].toFixed(1)}`).join(' ')
    return (
      <svg width={w} height={h} role="img" aria-label="compliance trend">
        <path d={path} fill="none" stroke="currentColor" strokeWidth="2" />
      </svg>
    )
  })()

  return (
    <div className="page">
      <h1>Compliance Trend &amp; Closure</h1>
      <p className="muted">
        Trend from persisted posture snapshots; closure metrics from the evidence lifecycle audit trail.
        Backed by <code>GET /api/v1/insight/trend</code>.
      </p>

      {report.loading ? <Loading what="trend" /> : null}
      {report.error ? <ErrorNote message={report.error} /> : null}

      {d ? (
        <>
          <div className="stat-grid">
            <StatCard label="Current compliance" value={current ? `${current.compliancePct}%` : '—'} hint="live rollup" />
            <StatCard label="Change vs first snapshot" value={delta != null ? `${delta > 0 ? '+' : ''}${delta}%` : '—'} />
            <StatCard label="Approvals" value={d.closure.approvals} />
            <StatCard label="Avg days to approve" value={d.closure.avgDaysToApprove ?? '—'} />
            <StatCard label="Rejections" value={d.closure.rejections} />
          </div>

          <Section title="Compliance % over time">
            {spark}
            <DataTable
              rows={d.points}
              rowKey={(p) => p.takenAt}
              columns={[
                { header: 'Snapshot', cell: (p) => new Date(p.takenAt).toLocaleDateString() },
                { header: 'Compliance %', cell: (p) => `${p.compliancePct}%` },
                { header: 'Completeness %', cell: (p) => `${p.completenessPct}%` },
                { header: 'Approved evidence', cell: (p) => p.approvedEvidence },
                { header: 'Open findings', cell: (p) => p.openFindings },
              ]}
            />
          </Section>

          <Section title="Collection throughput (scheduler runs)">
            <DataTable
              rows={d.collection}
              rowKey={(c) => c.at}
              columns={[
                { header: 'Finished', cell: (c) => new Date(c.at).toLocaleString() },
                { header: 'Ingested', cell: (c) => c.ingested },
                { header: 'Duplicates', cell: (c) => c.duplicates },
                { header: 'Failed', cell: (c) => c.failed },
              ]}
            />
          </Section>
        </>
      ) : null}
    </div>
  )
}
