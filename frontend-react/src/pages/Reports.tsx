import { useState } from 'react'
import { Link } from 'react-router-dom'
import { getEvidenceSummaryReport, getRegulatoryFiling, listReports, reportUrl } from '../api/endpoints'
import type { SummaryVerdict } from '../api/types'
import { getDataSource } from '../api/dataSource'
import { useAsync } from '../hooks/useAsync'
import { DataTable, ErrorNote, Loading, Section, StatCard, StatusPill } from '../components/ui'

type ReportsTab = 'catalogue' | 'regulatory' | 'summary'

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
        <button
          aria-pressed={tab === 'summary'}
          className={tab === 'summary' ? 'primary' : undefined}
          onClick={() => setTab('summary')}
        >
          Evidence summary
        </button>
      </div>

      {tab === 'catalogue' ? (
        <ReportCatalogue applicationSlug={applicationSlug} framework={framework} />
      ) : tab === 'regulatory' ? (
        <RegulatoryReport applicationSlug={applicationSlug} framework={framework} />
      ) : (
        <EvidenceSummaryReportView applicationSlug={applicationSlug} framework={framework} />
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

const VERDICT_TONE: Record<SummaryVerdict, 'ok' | 'bad' | 'muted' | 'warn'> = {
  PASS: 'ok',
  WARNING: 'warn',
  FAIL: 'bad',
  NOT_APPLICABLE: 'muted',
  NOT_ASSESSED: 'muted',
}

/** Text label first; the tone only reinforces it. */
function VerdictPill({ verdict }: { verdict: SummaryVerdict | null }) {
  if (verdict == null) return <span className="muted">no evidence</span>
  return <StatusPill status={verdict.replace(/_/g, ' ')} tone={VERDICT_TONE[verdict]} />
}

/** Per-control roll-up of the deterministic evidence summaries ("audit documentation"). Controls with no
 *  evidence are listed explicitly as MISSING. Backed by GET /api/v1/reports/evidence-summary. */
function EvidenceSummaryReportView({ applicationSlug, framework }: { applicationSlug: string; framework: string }) {
  const report = useAsync(
    () => getEvidenceSummaryReport(applicationSlug || undefined, framework || undefined),
    [applicationSlug, framework],
  )
  const d = report.data

  return (
    <>
      {report.loading ? <Loading what="evidence summary report" /> : null}
      {report.error ? <ErrorNote message={report.error} /> : null}
      {d ? (
        <>
          <Section
            title="Evidence summary — audit documentation"
            actions={
              <span className="row-actions">
                <a
                  href={reportUrl('evidence-summary', 'json', applicationSlug || undefined, framework || undefined)}
                  target="_blank"
                  rel="noreferrer"
                >
                  JSON
                </a>
                <a
                  href={reportUrl('evidence-summary', 'csv', applicationSlug || undefined, framework || undefined)}
                  target="_blank"
                  rel="noreferrer"
                >
                  CSV
                </a>
              </span>
            }
          >
            <p className="muted small">
              Generated {new Date(d.generatedAt).toLocaleString()} from fixed rules over the stored evidence — no AI
              model is involved. A verdict describes automated checks only; it does not prove a control is met.
            </p>
            <div className="stat-grid">
              <StatCard label="Controls expected" value={d.totals.controlsExpected} />
              <StatCard label="With evidence" value={d.totals.controlsWithEvidence} />
              <StatCard label="Missing" value={d.totals.controlsMissing} />
              <StatCard label="Evidence FAIL" value={d.totals.byVerdict.FAIL} />
              <StatCard label="Evidence WARNING" value={d.totals.byVerdict.WARNING} />
              <StatCard label="Stale evidence" value={d.totals.stale} />
            </div>
            {d.truncated ? d.limits.map((l) => <p key={l} className="muted small">{l}</p>) : null}
          </Section>

          <Section title={`Controls — ${d.controls.length}`}>
            {d.controls.length === 0 ? (
              <p className="muted">No expected controls match these filters.</p>
            ) : (
              <DataTable
                rows={d.controls}
                rowKey={(c) => `${c.application}|${c.framework}|${c.controlId}`}
                columns={[
                  {
                    header: 'Control',
                    cell: (c) => (
                      <details>
                        <summary>
                          <code>{c.controlId}</code> {c.controlName}
                        </summary>
                        {c.description ? <p className="small muted">{c.description}</p> : null}
                        <p className="small muted">Application: {c.application}</p>
                        {c.evidence.length === 0 ? (
                          <p className="small">
                            {c.status === 'MISSING' ? 'No evidence held for this control.' : 'No evidence entries listed.'}
                          </p>
                        ) : (
                          <ul aria-label={`Evidence for ${c.controlId}`}>
                            {c.evidence.map((e) => (
                              <li key={e.evidenceId} className="small">
                                <VerdictPill verdict={e.verdict} />{' '}
                                <Link to={`/evidence/${e.evidenceId}`}>{e.headline}</Link>{' '}
                                <span className="muted">
                                  (v{e.version}, integrity {e.integrity}, {e.freshness})
                                </span>
                              </li>
                            ))}
                          </ul>
                        )}
                      </details>
                    ),
                  },
                  { header: 'Framework', cell: (c) => c.framework },
                  { header: 'Status', cell: (c) => <StatusPill status={c.status} /> },
                  { header: 'Verdict', cell: (c) => <VerdictPill verdict={c.verdict} /> },
                  { header: 'Evidence', cell: (c) => c.evidenceCount, align: 'right' },
                  {
                    header: 'Latest collected',
                    cell: (c) => (c.latestCollectedAt ? new Date(c.latestCollectedAt).toLocaleDateString() : '—'),
                  },
                ]}
              />
            )}
          </Section>
        </>
      ) : null}
    </>
  )
}
