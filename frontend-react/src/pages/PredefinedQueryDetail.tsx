import { useMemo, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import {
  getEvidenceLifecycle,
  listApplications,
  listEvidence,
  listPredefinedQueries,
  runPredefinedQuery,
} from '../api/endpoints'
import type { PredefinedQueryRunResult } from '../api/types'
import { useAsync } from '../hooks/useAsync'
import { DataTable, Empty, ErrorNote, JsonBlock, Loading, Section, StatusPill } from '../components/ui'
import { LifecycleHistoryTable } from '../components/LifecycleHistoryTable'

const TABS = ['Summary', 'Query', 'Result', 'Evidence', 'Audit Trail'] as const
type Tab = (typeof TABS)[number]

export function PredefinedQueryDetail() {
  const { controlId = '' } = useParams()
  const navigate = useNavigate()
  const [tab, setTab] = useState<Tab>('Summary')
  const [applicationSlug, setApplicationSlug] = useState('')

  const catalog = useAsync(() => listPredefinedQueries({}), [])
  const item = catalog.data?.items.find((q) => q.controlId === controlId)

  const apps = useAsync(() => listApplications(), [])
  const onboarded = (apps.data ?? []).filter((a) => a.active)
  const effectiveSlug = applicationSlug || onboarded[0]?.slug

  const [busy, setBusy] = useState(false)
  const [runError, setRunError] = useState<string>()
  const [runResult, setRunResult] = useState<PredefinedQueryRunResult>()

  const evidenceQuery = useMemo(
    () => ({
      controlId,
      collectionMethod: 'predefined-query',
      applicationSlug: effectiveSlug || undefined,
      size: 50,
    }),
    [controlId, effectiveSlug],
  )
  const evidence = useAsync(() => listEvidence(evidenceQuery), [JSON.stringify(evidenceQuery)])
  const latestEvidence = evidence.data?.items[0]

  const lifecycle = useAsync(
    () => (latestEvidence ? getEvidenceLifecycle(latestEvidence.evidenceId) : Promise.resolve(undefined)),
    [latestEvidence?.evidenceId],
  )

  async function run() {
    setBusy(true)
    setRunError(undefined)
    try {
      setRunResult(await runPredefinedQuery(controlId, applicationSlug || undefined))
      evidence.reload()
    } catch (e) {
      setRunError(e instanceof Error ? e.message : String(e))
    } finally {
      setBusy(false)
    }
  }

  if (catalog.loading || apps.loading) return <Loading what="control" />
  if (catalog.error) return <ErrorNote message={catalog.error} />
  if (!item) return <ErrorNote message={`Unknown control: ${controlId}`} />

  return (
    <div className="page">
      <p>
        <button type="button" className="ghost" onClick={() => navigate(-1)}>
          ← Predefined Queries
        </button>
      </p>
      <h1>
        {item.controlName} <span className="muted">/ {item.controlId}</span>
      </h1>

      <div className="row-actions">
        {TABS.map((t) => (
          <button key={t} className={t === tab ? 'primary' : undefined} onClick={() => setTab(t)}>
            {t}
          </button>
        ))}
      </div>

      {tab === 'Summary' ? (
        <Section
          title="Summary"
          actions={
            <span className="row-actions">
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
              <button className="primary" disabled={busy} onClick={run}>
                {busy ? 'Running…' : 'Run Query'}
              </button>
            </span>
          }
        >
          <dl className="kv">
            <dt>Control name</dt>
            <dd>{item.controlName}</dd>
            <dt>Control family</dt>
            <dd>{item.controlFamily}</dd>
            <dt>Frameworks</dt>
            <dd>
              {item.frameworks.map((f) => (
                <span key={f} className="tag">
                  {f}
                </span>
              ))}
            </dd>
            <dt>Technology</dt>
            <dd>{item.technology}</dd>
            <dt>Evidence type</dt>
            <dd>{item.evidenceType}</dd>
            <dt>Runtime status</dt>
            <dd>
              <StatusPill status={item.runtimeStatus} />
              {item.executableNow ? <span className="muted small"> · executableNow</span> : null}
            </dd>
          </dl>
          {runError ? <ErrorNote message={runError} /> : null}
          {runResult ? (
            <p className="answer">
              <StatusPill status={runResult.outcome} /> mode <code>{runResult.mode}</code> —{' '}
              <button className="ghost" onClick={() => setTab('Result')}>
                view result →
              </button>
            </p>
          ) : null}
        </Section>
      ) : null}

      {tab === 'Query' ? (
        <Section title="Query">
          <p>
            <span className="tag">{item.technology}</span> <span className="pill pill-muted">Read-only</span>
          </p>
          <pre className="json">{item.command}</pre>
        </Section>
      ) : null}

      {tab === 'Result' ? (
        <Section title="Result">
          {runResult ? (
            <>
              <p>
                <StatusPill status={runResult.outcome} /> {runResult.applicationSlug} ({runResult.mode})
              </p>
              {runResult.error ? <ErrorNote message={runResult.error} /> : null}
              {runResult.outputPreview ? <pre className="json">{runResult.outputPreview}</pre> : null}
              {runResult.evidenceId ? (
                <p>
                  <Link to={`/evidence/${runResult.evidenceId}`}>View evidence →</Link>
                </p>
              ) : null}
            </>
          ) : evidence.loading ? (
            <Loading what="result" />
          ) : latestEvidence ? (
            <>
              <p className="muted small">Most recent result for this control (from a previous run).</p>
              <p>
                <StatusPill status={latestEvidence.lifecycleState ?? 'UNKNOWN'} /> {latestEvidence.applicationSlug} —{' '}
                <Link to={`/evidence/${latestEvidence.evidenceId}`}>{latestEvidence.evidenceId}</Link>
              </p>
              <p className="muted small">Updated {new Date(latestEvidence.updatedAt).toLocaleString()}</p>
              {latestEvidence.latest?.metadata ? <JsonBlock value={latestEvidence.latest.metadata} /> : null}
            </>
          ) : (
            <Empty message="No execution results yet. Run the query from the Summary tab to generate a result." />
          )}
        </Section>
      ) : null}

      {tab === 'Evidence' ? (
        <Section title="Evidence">
          {evidence.loading ? <Loading what="evidence" /> : null}
          {evidence.error ? <ErrorNote message={evidence.error} /> : null}
          {evidence.data && evidence.data.items.length === 0 ? (
            <Empty message="No evidence has been generated for this control yet." />
          ) : null}
          {evidence.data && evidence.data.items.length > 0 ? (
            <DataTable
              rows={evidence.data.items}
              rowKey={(r) => r.evidenceId}
              columns={[
                {
                  header: 'Evidence ID',
                  cell: (r) => <Link to={`/evidence/${r.evidenceId}`}>{r.evidenceId}</Link>,
                },
                { header: 'Framework', cell: (r) => r.framework },
                { header: 'Evidence Type', cell: (r) => r.tags.evidenceType ?? '—' },
                { header: 'Updated', cell: (r) => new Date(r.updatedAt).toLocaleString() },
              ]}
            />
          ) : null}
        </Section>
      ) : null}

      {tab === 'Audit Trail' ? (
        <Section title="Audit Trail">
          {!latestEvidence ? (
            <Empty message="No execution history recorded." />
          ) : lifecycle.loading ? (
            <Loading what="audit trail" />
          ) : lifecycle.error ? (
            <ErrorNote message={lifecycle.error} />
          ) : (
            <LifecycleHistoryTable history={lifecycle.data?.history ?? []} />
          )}
        </Section>
      ) : null}
    </div>
  )
}
