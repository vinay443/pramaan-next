import { useState } from 'react'
import { getRegulatoryFiling, listReports, reportUrl } from '../api/endpoints'
import { getDataSource } from '../api/dataSource'
import { useAsync } from '../hooks/useAsync'
import { DataTable, ErrorNote, Loading, Section, StatCard } from '../components/ui'

type ReportsTab = 'catalogue' | 'regulatory'

export function Reports() {
  const [applicationSlug, setApp] = useState('')
  const [framework, setFramework] = useState('')
  const [tab, setTab] = useState<ReportsTab>('catalogue')

  return (
    <div className="page">
      <h1>Regulatory Reporting</h1>
      <p className="muted">
        Each report is a view over existing insight / evidence data, downloadable as JSON or CSV. Backed by{' '}
        <code>GET /api/v1/reports/&#123;name&#125;</code>.
      </p>

      <Section title="Filters (applied where the report supports them)">
        <div className="filter-row">
          <label>
            Application
            <input aria-label="Application" value={applicationSlug} placeholder="(all)" onChange={(e) => setApp(e.target.value)} />
          </label>
          <label>
            Framework
            <input aria-label="Framework" value={framework} placeholder="(all)" onChange={(e) => setFramework(e.target.value)} />
          </label>
        </div>
        {getDataSource() === 'mock' ? (
          <p className="muted small">Download links require a live backend on <code>localhost:8080</code>.</p>
        ) : null}
      </Section>

      <div className="row-actions" aria-label="Reports view">
        <button
          aria-pressed={tab === 'catalogue'}
          className={tab === 'catalogue' ? 'primary' : undefined}
          onClick={() => setTab('catalogue')}
        >
          Report catalogue
        </button>
        <button
          aria-pressed={tab === 'regulatory'}
          className={tab === 'regulatory' ? 'primary' : undefined}
          onClick={() => setTab('regulatory')}
        >
          Regulatory Report
        </button>
      </div>

      {tab === 'catalogue' ? (
        <ReportCatalogue applicationSlug={applicationSlug} framework={framework} />
      ) : (
        <RegulatoryReport applicationSlug={applicationSlug} framework={framework} />
      )}
    </div>
  )
}

function ReportCatalogue({ applicationSlug, framework }: { applicationSlug: string; framework: string }) {
  const reports = useAsync(() => listReports(), [])

  return (
    <>
      {reports.loading ? <Loading what="report catalogue" /> : null}
      {reports.error ? <ErrorNote message={reports.error} /> : null}

      {reports.data ? (
        <Section title={`Reports — ${reports.data.length}`}>
          <DataTable
            rows={reports.data}
            rowKey={(r) => r.name}
            columns={[
              { header: 'Report', cell: (r) => r.title },
              { header: 'Name', cell: (r) => <code>{r.name}</code> },
              {
                header: 'Download',
                cell: (r) => (
                  <span className="row-actions">
                    {r.formats.map((f) => (
                      <a
                        key={f}
                        href={reportUrl(r.name, f as 'json' | 'csv', applicationSlug || undefined, framework || undefined)}
                        target="_blank"
                        rel="noreferrer"
                      >
                        {f.toUpperCase()}
                      </a>
                    ))}
                  </span>
                ),
              },
            ]}
          />
        </Section>
      ) : null}
    </>
  )
}

/** UC17 — regulator-ready filing: a fixed cover-page + per-framework schema over
 *  the same compliance data, distinct from the generic per-report views above. */
function RegulatoryReport({ applicationSlug, framework }: { applicationSlug: string; framework: string }) {
  const filing = useAsync(
    () => getRegulatoryFiling(applicationSlug || undefined, framework || undefined),
    [applicationSlug, framework],
  )

  return (
    <>
      {filing.loading ? <Loading what="regulatory filing" /> : null}
      {filing.error ? <ErrorNote message={filing.error} /> : null}
      {filing.data ? (
        <>
          <Section
            title={filing.data.title}
            actions={
              <span className="row-actions">
                <a
                  href={reportUrl('regulatory-filing', 'json', applicationSlug || undefined, framework || undefined)}
                  target="_blank"
                  rel="noreferrer"
                >
                  JSON
                </a>
                <a
                  href={reportUrl('regulatory-filing', 'csv', applicationSlug || undefined, framework || undefined)}
                  target="_blank"
                  rel="noreferrer"
                >
                  CSV
                </a>
              </span>
            }
          >
            <dl className="kv">
              <dt>Report ID</dt>
              <dd><code>{filing.data.reportId}</code></dd>
              <dt>Regulator</dt>
              <dd>{filing.data.regulator}</dd>
              <dt>Scope</dt>
              <dd>{filing.data.scope}</dd>
              <dt>Framework</dt>
              <dd>{filing.data.framework}</dd>
              <dt>Reporting period</dt>
              <dd>
                {new Date(filing.data.periodStart).toLocaleDateString()} –{' '}
                {new Date(filing.data.periodEnd).toLocaleDateString()}
              </dd>
              <dt>Generated</dt>
              <dd>{new Date(filing.data.generatedAt).toLocaleString()}</dd>
              <dt>Prepared by</dt>
              <dd>{filing.data.preparedBy}</dd>
            </dl>

            <div className="stat-grid">
              <StatCard label="Applications in scope" value={filing.data.applicationsInScope} />
              <StatCard label="Controls expected" value={filing.data.controlsExpected} />
              <StatCard label="Controls compliant" value={filing.data.controlsCompliant} />
              <StatCard label="Compliance" value={`${filing.data.compliancePct}%`} />
              <StatCard label="Evidence records" value={filing.data.evidenceRecords} />
              <StatCard label="Open gaps" value={filing.data.openGaps} />
            </div>
          </Section>

          <Section title="Per-framework filing">
            <DataTable
              rows={filing.data.frameworks}
              rowKey={(f) => f.framework}
              columns={[
                { header: 'Framework', cell: (f) => f.framework },
                { header: 'Regulator', cell: (f) => f.regulator },
                { header: 'Expected', cell: (f) => f.expected, align: 'right' },
                { header: 'Compliant', cell: (f) => f.compliant, align: 'right' },
                { header: 'Non-compliant', cell: (f) => f.nonCompliant, align: 'right' },
                { header: 'Missing evidence', cell: (f) => f.missingEvidence, align: 'right' },
                { header: 'Compliance', cell: (f) => `${f.compliancePct}%`, align: 'right' },
              ]}
            />
          </Section>

          <Section title="Attestation">
            <p className="muted">{filing.data.attestation}</p>
          </Section>
        </>
      ) : null}
    </>
  )
}
