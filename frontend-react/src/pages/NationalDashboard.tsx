import { getNationalRollup } from '../api/endpoints'
import { useAsync } from '../hooks/useAsync'
import { DataTable, ErrorNote, Loading, Section, StatCard, StatusPill } from '../components/ui'

/** National & enterprise dashboard — the merged replacement for the old Enterprise and flat
 *  National pages. Backed by GET /api/v1/insight/national/rollup, which carries the regions
 *  (RAG + gap to the national average), region x framework breakdown, and the business-unit /
 *  criticality cuts and top risks. /enterprise redirects here. */
export function NationalDashboard() {
  return (
    <div className="page">
      <h1>National &amp; Enterprise Compliance</h1>
      <p className="muted">
        Regions ranked by gap to the national average, business-unit and criticality cuts, top risks, and the
        region x framework breakdown. Backed by <code>GET /api/v1/insight/national/rollup</code>.
      </p>
      <NationalEnterpriseView showFrameworkBreakdown />
    </div>
  )
}

/** Shared body of the /national page and the CIO dashboard's "National & Enterprise" tab. */
export function NationalEnterpriseView({ showFrameworkBreakdown = false }: { showFrameworkBreakdown?: boolean }) {
  const rollup = useAsync(() => getNationalRollup(), [])
  const r = rollup.data
  const appsByRegion = new Map((r?.regions ?? []).map((x) => [x.region, x.applications]))

  return (
    <>
      {rollup.loading ? <Loading what="national & enterprise rollup" /> : null}
      {rollup.error ? <ErrorNote message={rollup.error} /> : null}

      {r ? (
        <>
          <div className="stat-grid">
            <StatCard label="National compliance" value={`${r.nationalCompliancePct}%`} />
            <StatCard label="National completeness" value={`${r.nationalCompletenessPct}%`} />
            <StatCard
              label="Enterprise compliance"
              value={`${r.portfolio.compliancePct}%`}
              hint={`${r.portfolio.applications} applications, all regions`}
            />
            <StatCard label="Applications mapped to regions" value={r.applications} />
            <StatCard label="Open findings" value={Math.max(0, r.portfolio.expected - r.portfolio.compliant)} />
          </div>

          <Section title="Regions ranked by gap to national average (furthest-behind first)">
            <DataTable
              rows={r.laggingRegions}
              rowKey={(g) => g.region}
              columns={[
                { header: 'Region', cell: (g) => g.region },
                { header: 'Applications', cell: (g) => (appsByRegion.get(g.region) ?? []).join(', ') || '—' },
                { header: 'Compliance %', cell: (g) => `${g.compliancePct}%`, align: 'right' },
                {
                  header: 'Gap vs national',
                  cell: (g) => `${g.gapVsNationalPct > 0 ? '+' : ''}${g.gapVsNationalPct}pp`,
                  align: 'right',
                },
                { header: 'RAG', cell: (g) => <StatusPill status={g.rag} /> },
              ]}
            />
          </Section>

          <div className="dash-grid-2">
            <Section title="By business unit">
              <DataTable
                rows={r.byBusinessUnit}
                rowKey={(g) => g.key}
                columns={[
                  { header: 'Business unit', cell: (g) => g.key },
                  { header: 'Apps', cell: (g) => g.applications, align: 'right' },
                  { header: 'Compliance %', cell: (g) => `${g.compliancePct}%`, align: 'right' },
                  { header: 'Completeness %', cell: (g) => `${g.completenessPct}%`, align: 'right' },
                ]}
              />
            </Section>

            <Section title="By criticality">
              <DataTable
                rows={r.byCriticality}
                rowKey={(g) => g.key}
                columns={[
                  { header: 'Criticality', cell: (g) => <StatusPill status={g.key} /> },
                  { header: 'Apps', cell: (g) => g.applications, align: 'right' },
                  { header: 'Compliance %', cell: (g) => `${g.compliancePct}%`, align: 'right' },
                  { header: 'Completeness %', cell: (g) => `${g.completenessPct}%`, align: 'right' },
                ]}
              />
            </Section>
          </div>

          <Section title="Top risks (most critical first, then lowest compliance)">
            <DataTable
              rows={r.topRisks}
              rowKey={(p) => p.applicationSlug}
              columns={[
                { header: 'Application', cell: (p) => p.name },
                { header: 'Criticality', cell: (p) => <StatusPill status={p.criticality} /> },
                { header: 'Compliance %', cell: (p) => `${p.compliancePct}%`, align: 'right' },
                { header: 'Non-compliant', cell: (p) => p.nonCompliant, align: 'right' },
                { header: 'Missing evidence', cell: (p) => p.missingEvidence, align: 'right' },
              ]}
            />
          </Section>

          {showFrameworkBreakdown ? (
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
          ) : null}
        </>
      ) : null}
    </>
  )
}
