import { getNationalRollup } from '../api/endpoints'
import { useAsync } from '../hooks/useAsync'
import { DataTable, ErrorNote, Loading, Section, StatCard, StatusPill } from '../components/ui'

/** Nationally-aggregated cross-region/cross-app rollup — distinct from Enterprise.tsx's
 *  flat per-region table: a region x framework breakdown, plus regions ranked by how
 *  far they trail the national average (furthest-behind first). Backed by
 *  GET /api/v1/insight/national/rollup (EnterpriseDashboardService.nationalRollup()). */
export function NationalDashboard() {
  const rollup = useAsync(() => getNationalRollup(), [])
  const r = rollup.data

  return (
    <div className="page">
      <h1>National Rollup</h1>
      <p className="muted">
        Cross-region, cross-application aggregation: compliance broken down by region and framework, and
        regions ranked by their gap to the national average. Backed by{' '}
        <code>/api/v1/insight/national/rollup</code>.
      </p>

      {rollup.loading ? <Loading what="national rollup" /> : null}
      {rollup.error ? <ErrorNote message={rollup.error} /> : null}

      {r ? (
        <>
          <div className="stat-grid">
            <StatCard label="National compliance" value={`${r.nationalCompliancePct}%`} />
            <StatCard label="National completeness" value={`${r.nationalCompletenessPct}%`} />
            <StatCard label="Applications mapped" value={r.applications} />
            <StatCard label="Regions" value={r.laggingRegions.length} />
          </div>

          <Section title="Regions ranked by gap to national average (furthest-behind first)">
            <DataTable
              rows={r.laggingRegions}
              rowKey={(g) => g.region}
              columns={[
                { header: 'Region', cell: (g) => g.region },
                { header: 'Compliance %', cell: (g) => `${g.compliancePct}%` },
                {
                  header: 'Gap vs national',
                  cell: (g) => `${g.gapVsNationalPct > 0 ? '+' : ''}${g.gapVsNationalPct}pp`,
                },
                { header: 'RAG', cell: (g) => <StatusPill status={g.rag} /> },
              ]}
            />
          </Section>

          <Section title="Region x framework breakdown">
            <DataTable
              rows={r.byRegionFramework}
              rowKey={(row) => `${row.region}-${row.framework}`}
              columns={[
                { header: 'Region', cell: (row) => row.region },
                { header: 'Framework', cell: (row) => row.framework },
                { header: 'Expected', cell: (row) => row.expected, align: 'right' },
                { header: 'Compliant', cell: (row) => row.compliant, align: 'right' },
                { header: 'Compliance %', cell: (row) => `${row.compliancePct}%`, align: 'right' },
              ]}
            />
          </Section>
        </>
      ) : null}
    </div>
  )
}
