import { getEnterpriseDashboard, getNationalDashboard } from '../api/endpoints'
import { useAsync } from '../hooks/useAsync'
import { DataTable, ErrorNote, Loading, Section, StatCard, StatusPill } from '../components/ui'

export function Enterprise() {
  const ent = useAsync(() => getEnterpriseDashboard(), [])
  const nat = useAsync(() => getNationalDashboard(), [])
  const e = ent.data

  return (
    <div className="page">
      <h1>Enterprise &amp; National Compliance</h1>
      <p className="muted">
        Enterprise view decorates the portfolio rollup with business-unit / criticality cuts; the national
        view maps applications to regions. Backed by <code>/api/v1/insight/enterprise</code> and{' '}
        <code>/national</code>.
      </p>

      {ent.loading ? <Loading what="enterprise dashboard" /> : null}
      {ent.error ? <ErrorNote message={ent.error} /> : null}

      {e ? (
        <>
          <div className="stat-grid">
            <StatCard label="Applications" value={e.portfolio.applications} />
            <StatCard label="Compliance" value={`${e.portfolio.compliancePct}%`} />
            <StatCard label="Completeness" value={`${e.portfolio.completenessPct}%`} />
            <StatCard label="Open findings" value={Math.max(0, e.portfolio.expected - e.portfolio.compliant)} />
          </div>

          <Section title="By business unit">
            <DataTable
              rows={e.byBusinessUnit}
              rowKey={(g) => g.key}
              columns={[
                { header: 'Business unit', cell: (g) => g.key },
                { header: 'Apps', cell: (g) => g.applications },
                { header: 'Compliance %', cell: (g) => `${g.compliancePct}%` },
                { header: 'Completeness %', cell: (g) => `${g.completenessPct}%` },
              ]}
            />
          </Section>

          <Section title="By criticality">
            <DataTable
              rows={e.byCriticality}
              rowKey={(g) => g.key}
              columns={[
                { header: 'Criticality', cell: (g) => g.key },
                { header: 'Apps', cell: (g) => g.applications },
                { header: 'Compliance %', cell: (g) => `${g.compliancePct}%` },
              ]}
            />
          </Section>

          <Section title="Top risks">
            <DataTable
              rows={e.topRisks}
              rowKey={(p) => p.applicationSlug}
              columns={[
                { header: 'Application', cell: (p) => p.name },
                { header: 'Criticality', cell: (p) => <StatusPill status={p.criticality} /> },
                { header: 'Compliance %', cell: (p) => `${p.compliancePct}%` },
                { header: 'Non-compliant', cell: (p) => p.nonCompliant },
              ]}
            />
          </Section>
        </>
      ) : null}

      <Section title="National / pan-India">
        {nat.loading ? <Loading what="national dashboard" /> : null}
        {nat.data ? (
          <>
            <div className="stat-grid">
              <StatCard label="National compliance" value={`${nat.data.nationalCompliancePct}%`} />
              <StatCard label="National completeness" value={`${nat.data.nationalCompletenessPct}%`} />
              <StatCard label="Applications mapped" value={nat.data.applications} />
            </div>
            <DataTable
              rows={nat.data.regions}
              rowKey={(r) => r.region}
              columns={[
                { header: 'Region', cell: (r) => r.region },
                { header: 'Applications', cell: (r) => r.applications.join(', ') || '—' },
                { header: 'Compliance %', cell: (r) => `${r.compliancePct}%` },
                { header: 'RAG', cell: (r) => <StatusPill status={r.rag} /> },
              ]}
            />
          </>
        ) : null}
      </Section>
    </div>
  )
}
