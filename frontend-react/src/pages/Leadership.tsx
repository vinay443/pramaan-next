import { Link } from 'react-router-dom'
import { getLeadershipDashboard } from '../api/endpoints'
import { useAsync } from '../hooks/useAsync'
import { DataTable, ErrorNote, Loading, Section, StatCard, StatusPill } from '../components/ui'

export function Leadership() {
  const board = useAsync(() => getLeadershipDashboard(), [])
  const d = board.data

  return (
    <div className="page">
      <h1>Leadership Compliance Dashboard</h1>
      <p className="muted">
        Portfolio rollup of the <em>actual</em> deterministic compliance and completeness results across
        every application. Backed by <code>GET /api/v1/insight/leadership</code>.
      </p>

      {board.loading ? <Loading what="leadership dashboard" /> : null}
      {board.error ? <ErrorNote message={board.error} /> : null}

      {d ? (
        <>
          <div className="stat-grid">
            <StatCard label="Applications" value={d.applications} />
            <StatCard label="Portfolio compliance" value={`${d.compliancePct}%`} hint={`${d.compliant}/${d.expected} controls`} />
            <StatCard label="Evidence completeness" value={`${d.completenessPct}%`} hint={`${d.covered}/${d.expected} covered`} />
            <StatCard label="Missing evidence" value={d.missing} />
            <StatCard label="Stale evidence" value={d.stale} />
          </div>

          <Section title="Check verdicts (actual results)">
            <div className="stat-grid">
              {Object.entries(d.checkVerdicts).map(([status, count]) => (
                <StatCard key={status} label={status} value={count} />
              ))}
            </div>
          </Section>

          <Section title={`By application — ${d.byApplication.length}`}>
            <DataTable
              rows={d.byApplication}
              rowKey={(a) => a.applicationSlug}
              columns={[
                {
                  header: 'Application',
                  cell: (a) => (
                    <Link to={`/compliance?applicationSlug=${encodeURIComponent(a.applicationSlug)}`}>
                      {a.name}
                    </Link>
                  ),
                },
                { header: 'Criticality', cell: (a) => <StatusPill status={a.criticality} /> },
                { header: 'Compliance %', cell: (a) => `${a.compliancePct}%`, align: 'right' },
                { header: 'Completeness %', cell: (a) => `${a.completenessPct}%`, align: 'right' },
                { header: 'Non-compliant', cell: (a) => a.nonCompliant, align: 'right' },
                { header: 'Missing evidence', cell: (a) => a.missingEvidence, align: 'right' },
              ]}
            />
          </Section>

          <Section title="By framework">
            <DataTable
              rows={d.byFramework}
              rowKey={(f) => f.framework}
              columns={[
                { header: 'Framework', cell: (f) => f.framework },
                { header: 'Expected', cell: (f) => f.expected, align: 'right' },
                { header: 'Compliant', cell: (f) => f.compliant, align: 'right' },
                { header: 'Non-compliant', cell: (f) => f.nonCompliant, align: 'right' },
                { header: 'Missing evidence', cell: (f) => f.missingEvidence, align: 'right' },
                { header: '%', cell: (f) => `${f.compliancePct}%`, align: 'right' },
              ]}
            />
          </Section>
        </>
      ) : null}
    </div>
  )
}
