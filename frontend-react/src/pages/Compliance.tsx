import { useState } from 'react'
import { getCompliance, listApplications } from '../api/endpoints'
import { useAsync } from '../hooks/useAsync'
import { DataTable, Empty, ErrorNote, Loading, Section, StatCard, StatusPill } from '../components/ui'

export function Compliance() {
  const apps = useAsync(() => listApplications(), [])
  const [slug, setSlug] = useState('')
  const effectiveSlug = slug || apps.data?.[0]?.slug || ''

  const report = useAsync(
    () => (effectiveSlug ? getCompliance(effectiveSlug) : Promise.resolve(undefined)),
    [effectiveSlug],
  )

  return (
    <div className="page">
      <h1>Compliance Dashboard</h1>
      <p className="muted">
        Deterministic check verdicts rolled up against the expected-control catalog. Backed by{' '}
        <code>GET /api/v1/insight/compliance</code>.
      </p>

      <Section title="Application">
        <div className="filter-row">
          <label>
            Application
            <select aria-label="Application" value={effectiveSlug} onChange={(e) => setSlug(e.target.value)}>
              {(apps.data ?? []).map((a) => (
                <option key={a.slug} value={a.slug}>
                  {a.name}
                </option>
              ))}
            </select>
          </label>
        </div>
      </Section>

      {report.loading ? <Loading what="compliance" /> : null}
      {report.error ? <ErrorNote message={report.error} /> : null}

      {report.data ? (
        <>
          <div className="stat-grid">
            <StatCard label="Expected controls" value={report.data.expected} />
            <StatCard label="Compliant" value={report.data.compliant} />
            <StatCard label="Compliance" value={`${report.data.compliancePct}%`} />
          </div>

          <Section title="By framework">
            {report.data.byFramework.length === 0 ? (
              <Empty message="No frameworks in scope." />
            ) : (
              <DataTable
                rows={report.data.byFramework}
                rowKey={(f) => f.framework}
                columns={[
                  { header: 'Framework', cell: (f) => f.framework },
                  { header: 'Expected', cell: (f) => f.expected },
                  { header: 'Compliant', cell: (f) => f.compliant },
                  { header: 'Partial', cell: (f) => f.partiallyCompliant },
                  { header: 'Non-compliant', cell: (f) => f.nonCompliant },
                  { header: 'Not assessed', cell: (f) => f.notAssessed },
                  { header: 'Missing evidence', cell: (f) => f.missingEvidence },
                  { header: '%', cell: (f) => `${f.compliancePct}%` },
                ]}
              />
            )}
          </Section>

          <Section title={`Controls — ${report.data.controls.length}`}>
            <DataTable
              rows={report.data.controls}
              rowKey={(c) => `${c.framework}|${c.controlId}`}
              columns={[
                { header: 'Status', cell: (c) => <StatusPill status={c.status} /> },
                { header: 'Framework', cell: (c) => c.framework },
                { header: 'Control', cell: (c) => c.controlId },
                { header: 'Detail', cell: (c) => c.detail },
              ]}
            />
          </Section>
        </>
      ) : null}
    </div>
  )
}
