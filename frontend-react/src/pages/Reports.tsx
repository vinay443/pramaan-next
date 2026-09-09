import { useState } from 'react'
import { listReports, reportUrl } from '../api/endpoints'
import { getDataSource } from '../api/dataSource'
import { useAsync } from '../hooks/useAsync'
import { DataTable, ErrorNote, Loading, Section } from '../components/ui'

export function Reports() {
  const reports = useAsync(() => listReports(), [])
  const [applicationSlug, setApp] = useState('')
  const [framework, setFramework] = useState('')

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
    </div>
  )
}
