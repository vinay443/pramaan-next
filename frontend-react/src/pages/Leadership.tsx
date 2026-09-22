import { Link } from 'react-router-dom'
import { getLeadershipDashboard } from '../api/endpoints'
import { useAsync } from '../hooks/useAsync'
import { DataTable, ErrorNote, Loading, Section, StatCard, StatusPill } from '../components/ui'

export function Leadership() {
  return (
    <div className="page">
      <h1>Leadership Compliance Dashboard</h1>
      <p className="muted">
        Portfolio rollup of the <em>actual</em> deterministic compliance and completeness results across
        every application. Backed by <code>GET /api/v1/insight/leadership</code>.
      </p>
      <LeadershipView />
    </div>
  )
}

/** Leadership rollup body. Shared by the /leadership page (whole portfolio) and the
 *  Functional Head dashboard tab. With `slugs` it is scoped to those applications: totals
 *  are recomputed from the in-scope apps, the app table is ranked worst-compliance-first,
 *  and the verdict/framework sections are hidden (the endpoint only reports those
 *  portfolio-wide, so they cannot be scoped honestly). */
export function LeadershipView({ slugs }: { slugs?: Set<string> }) {
  const board = useAsync(() => getLeadershipDashboard(), [])
  const d = board.data
  const scoped = slugs !== undefined

  const apps = d
    ? scoped
      ? d.byApplication.filter((a) => slugs.has(a.applicationSlug)).sort((a, b) => a.compliancePct - b.compliancePct)
      : d.byApplication
    : []
  const sum = (pick: (a: (typeof apps)[number]) => number) => apps.reduce((s, a) => s + pick(a), 0)
  const expected = scoped ? sum((a) => a.expected) : (d?.expected ?? 0)
  const compliant = scoped ? sum((a) => a.compliant) : (d?.compliant ?? 0)
  const covered = scoped ? sum((a) => a.covered) : (d?.covered ?? 0)
  const missing = scoped ? sum((a) => a.missing) : (d?.missing ?? 0)
  const pct = (n: number) => (expected === 0 ? 0 : Math.round((1000 * n) / expected) / 10)

  return (
    <>
      {board.loading ? <Loading what="leadership dashboard" /> : null}
      {board.error ? <ErrorNote message={board.error} /> : null}

      {d ? (
        <>
          <div className="stat-grid">
            <StatCard label="Applications" value={scoped ? apps.length : d.applications} />
            <StatCard
              label={scoped ? 'Function compliance' : 'Portfolio compliance'}
              value={`${scoped ? pct(compliant) : d.compliancePct}%`}
              hint={`${compliant}/${expected} controls`}
            />
            <StatCard
              label="Evidence completeness"
              value={`${scoped ? pct(covered) : d.completenessPct}%`}
              hint={`${covered}/${expected} covered`}
            />
            <StatCard label="Missing evidence" value={missing} />
            {scoped ? null : <StatCard label="Stale evidence" value={d.stale} />}
          </div>

          {scoped ? null : (
            <Section title="Check verdicts (actual results)">
              <div className="stat-grid">
                {Object.entries(d.checkVerdicts).map(([status, count]) => (
                  <StatCard key={status} label={status} value={count} />
                ))}
              </div>
            </Section>
          )}

          <Section title={scoped ? `Applications by compliance, lowest first — ${apps.length}` : `By application — ${apps.length}`}>
            <DataTable
              rows={apps}
              rowKey={(a) => a.applicationSlug}
              columns={[
                ...(scoped ? [{ header: '#', cell: (a: (typeof apps)[number]) => apps.indexOf(a) + 1 }] : []),
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

          {scoped ? null : (
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
          )}
        </>
      ) : null}
    </>
  )
}
