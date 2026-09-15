import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { listApplications, listPredefinedQueries, runAllPredefinedQueries, runPredefinedQuery } from '../api/endpoints'
import type { PredefinedQueryRunResult, PredefinedQueryRunSummary } from '../api/types'
import { useAsync } from '../hooks/useAsync'
import { DataTable, ErrorNote, Loading, Section, StatCard, StatusPill } from '../components/ui'

export function PredefinedQueries() {
  const [technology, setTechnology] = useState('')
  const [framework, setFramework] = useState('')
  const [controlFamily, setControlFamily] = useState('')
  const [applicationSlug, setApplicationSlug] = useState('')

  const filters = useMemo(
    () => ({
      technology: technology || undefined,
      framework: framework || undefined,
      controlFamily: controlFamily || undefined,
    }),
    [technology, framework, controlFamily],
  )
  const catalog = useAsync(() => listPredefinedQueries(filters), [JSON.stringify(filters)])
  const apps = useAsync(() => listApplications(), [])
  const onboarded = (apps.data ?? []).filter((a) => a.active)

  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string>()
  const [summary, setSummary] = useState<PredefinedQueryRunSummary>()
  const [lastRun, setLastRun] = useState<PredefinedQueryRunResult>()

  async function runOne(controlId: string) {
    setBusy(true)
    setError(undefined)
    setSummary(undefined)
    try {
      setLastRun(await runPredefinedQuery(controlId, applicationSlug || undefined))
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e))
    } finally {
      setBusy(false)
    }
  }

  async function runAll() {
    setBusy(true)
    setError(undefined)
    setLastRun(undefined)
    try {
      setSummary(await runAllPredefinedQueries({ ...filters, applicationSlug: applicationSlug || undefined }))
      catalog.reload()
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e))
    } finally {
      setBusy(false)
    }
  }

  const count = catalog.data?.items.length ?? 0
  const commandFor = (controlId: string) => catalog.data?.items.find((i) => i.controlId === controlId)?.command

  return (
    <div className="page">
      <h1>Predefined Queries</h1>
      <p className="muted">
        Technical control catalogue (<code>GET /api/v1/predefined-queries</code>). Running a query executes
        it in <strong>simulated</strong> mode and produces an evidence record via the canonical ingestion
        path — tagged <code>collectionMethod=predefined-query</code>.
      </p>

      <Section
        title="Filters"
        actions={
          <button className="primary" onClick={runAll} disabled={busy || count === 0}>
            {busy ? 'Running…' : `Run all${count ? ` (${count})` : ''}`}
          </button>
        }
      >
        <div className="filter-row">
          <label>
            Technology
            <select aria-label="Technology" value={technology} onChange={(e) => setTechnology(e.target.value)}>
              <option value="">(any)</option>
              {(catalog.data?.technologies ?? []).map((t) => (
                <option key={t}>{t}</option>
              ))}
            </select>
          </label>
          <label>
            Framework
            <select aria-label="Framework" value={framework} onChange={(e) => setFramework(e.target.value)}>
              <option value="">(any)</option>
              {(catalog.data?.frameworks ?? []).map((f) => (
                <option key={f}>{f}</option>
              ))}
            </select>
          </label>
          <label>
            Control family
            <select
              aria-label="Control family"
              value={controlFamily}
              onChange={(e) => setControlFamily(e.target.value)}
            >
              <option value="">(any)</option>
              {(catalog.data?.controlFamilies ?? []).map((f) => (
                <option key={f}>{f}</option>
              ))}
            </select>
          </label>
          <label>
            Application
            <select
              aria-label="Application"
              value={applicationSlug}
              onChange={(e) => setApplicationSlug(e.target.value)}
            >
              <option value="">(first onboarded)</option>
              {onboarded.map((a) => (
                <option key={a.slug} value={a.slug}>
                  {a.name}
                </option>
              ))}
            </select>
          </label>
        </div>
        {error ? <ErrorNote message={error} /> : null}
        {lastRun ? (
          <div className="answer">
            <p>
              <StatusPill status={lastRun.outcome} /> {lastRun.controlId} →{' '}
              <Link to={`/predefined-queries/${lastRun.controlId}`}>view details</Link>
            </p>
            {lastRun.error ? <ErrorNote message={lastRun.error} /> : null}
          </div>
        ) : null}
        {summary ? (
          <>
            <div className="stat-grid">
              <StatCard label="Received" value={summary.received} />
              <StatCard label="Ingested" value={summary.ingested} />
              <StatCard label="Duplicates" value={summary.duplicates} />
              <StatCard label="Failed" value={summary.failed} />
            </div>
            <p className="muted small">
              {summary.message} — application {summary.applicationSlug}, mode {summary.mode}
            </p>
            <DataTable
              rows={summary.results}
              rowKey={(r) => r.controlId}
              columns={[
                { header: 'Control', cell: (r) => r.controlId },
                { header: 'Command', cell: (r) => <code>{commandFor(r.controlId)}</code> },
                { header: 'Outcome', cell: (r) => <StatusPill status={r.outcome} /> },
                { header: 'Error', cell: (r) => (r.error ? r.error : '') },
              ]}
            />
          </>
        ) : null}
      </Section>

      <Section title={`Catalogue${catalog.data ? ` — ${catalog.data.total}` : ''}`}>
        {catalog.loading ? <Loading what="predefined queries" /> : null}
        {catalog.error ? <ErrorNote message={catalog.error} /> : null}
        {catalog.data ? (
          <DataTable
            rows={catalog.data.items}
            rowKey={(q) => q.controlId}
            columns={[
              { header: 'Control', cell: (q) => q.controlId },
              { header: 'Technology', cell: (q) => q.technology },
              { header: 'Name', cell: (q) => <Link to={`/predefined-queries/${q.controlId}`}>{q.controlName}</Link> },
              { header: 'Command', cell: (q) => <code>{q.command}</code> },
              { header: 'Family', cell: (q) => q.controlFamily },
              { header: 'Frameworks', cell: (q) => q.frameworks.join(', ') },
              { header: 'Type', cell: (q) => q.evidenceType },
              {
                header: 'ECS status',
                cell: (q) => (
                  <span className="muted small">
                    {q.runtimeStatus}
                    {q.executableNow ? ' · executableNow' : ''}
                  </span>
                ),
              },
              {
                header: '',
                cell: (q) => (
                  <button disabled={busy} onClick={() => runOne(q.controlId)}>
                    Run
                  </button>
                ),
              },
            ]}
          />
        ) : null}
      </Section>
    </div>
  )
}
