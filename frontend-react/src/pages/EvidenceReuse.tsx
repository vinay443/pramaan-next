import { useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  addEvidenceFramework,
  getEvidenceReuse,
  getReuseByControl,
  listReuseControls,
  searchEvidenceReuse,
} from '../api/endpoints'
import type { ReuseResult } from '../api/types'
import { useAsync } from '../hooks/useAsync'
import { DataTable, Empty, ErrorNote, Loading, Section, StatCard, StatusPill } from '../components/ui'

type Tab = 'similar' | 'control'

export function EvidenceReuse() {
  const [tab, setTab] = useState<Tab>('similar')

  return (
    <div className="page">
      <h1>Evidence Reuse</h1>
      <p className="muted">
        Reuse evidence already held instead of re-collecting it — by content similarity, or across the
        frameworks a single control satisfies. Backed by <code>/api/v1/insight/reuse</code>.
      </p>

      <div className="row-actions" aria-label="Reuse view">
        <button
          aria-pressed={tab === 'similar'}
          className={tab === 'similar' ? 'primary' : undefined}
          onClick={() => setTab('similar')}
        >
          Find similar evidence
        </button>
        <button
          aria-pressed={tab === 'control'}
          className={tab === 'control' ? 'primary' : undefined}
          onClick={() => setTab('control')}
        >
          Browse by control
        </button>
      </div>

      {tab === 'similar' ? <FindSimilar /> : <BrowseByControl />}
    </div>
  )
}

// ---- existing embedding-based similarity search (unchanged behaviour) -------

function FindSimilar() {
  const navigate = useNavigate()
  const [mode, setMode] = useState<'evidence' | 'text'>('text')
  const [evidenceId, setEvidenceId] = useState('')
  const [text, setText] = useState('SSH root login disabled on a linux host')
  const [result, setResult] = useState<ReuseResult>()
  const [error, setError] = useState<string>()
  const [busy, setBusy] = useState(false)

  async function run() {
    setBusy(true)
    setError(undefined)
    try {
      setResult(
        mode === 'evidence'
          ? await getEvidenceReuse(evidenceId.trim(), 5, 0.1)
          : await searchEvidenceReuse(text.trim(), 5, 0.1),
      )
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e))
    } finally {
      setBusy(false)
    }
  }

  return (
    <>
      <Section
        title="Find similar evidence"
        actions={
          <button
            className="primary"
            onClick={run}
            disabled={busy || (mode === 'evidence' ? !evidenceId.trim() : !text.trim())}
          >
            {busy ? 'Searching…' : 'Search'}
          </button>
        }
      >
        <div className="filter-row">
          <label>
            Mode
            <select aria-label="Mode" value={mode} onChange={(e) => setMode(e.target.value as 'evidence' | 'text')}>
              <option value="text">Free text</option>
              <option value="evidence">By evidence ID</option>
            </select>
          </label>
          {mode === 'evidence' ? (
            <label>
              Evidence ID
              <input aria-label="Evidence ID" value={evidenceId} onChange={(e) => setEvidenceId(e.target.value)} />
            </label>
          ) : (
            <label>
              Text
              <input aria-label="Text" value={text} onChange={(e) => setText(e.target.value)} />
            </label>
          )}
        </div>
      </Section>

      {error ? <ErrorNote message={error} /> : null}

      {result ? (
        <>
          <div className="stat-grid">
            <StatCard label="Indexed" value={result.indexed} />
            <StatCard label="Exact duplicates" value={result.exactDuplicates.length} hint="same SHA-256" />
            <StatCard label="Matches" value={result.matches.length} />
            <StatCard label="Model" value={result.embeddingModel} />
            <StatCard label="Vector store" value={result.vectorStore} />
          </div>

          {result.exactDuplicates.length > 0 ? (
            <Section title={`Exact duplicates — ${result.exactDuplicates.length}`}>
              <p className="muted">
                Byte-identical evidence (SHA-256 <code>{result.querySha256?.slice(0, 12)}…</code>) already held —
                reuse instead of re-collecting.
              </p>
              <DataTable
                rows={result.exactDuplicates}
                rowKey={(m) => m.evidenceId}
                onRowClick={(m) => navigate(`/evidence/${m.evidenceId}`)}
                columns={[
                  { header: 'Application', cell: (m) => m.applicationSlug },
                  { header: 'Framework', cell: (m) => m.framework },
                  { header: 'Control', cell: (m) => m.controlId },
                  { header: 'Hint', cell: (m) => m.reuseHint },
                ]}
              />
            </Section>
          ) : null}

          <Section title="Matches">
            {result.matches.length === 0 ? (
              <Empty message="No evidence above the similarity threshold." />
            ) : (
              <DataTable
                rows={result.matches}
                rowKey={(m) => m.evidenceId}
                onRowClick={(m) => navigate(`/evidence/${m.evidenceId}`)}
                columns={[
                  { header: 'Score', cell: (m) => m.score.toFixed(3), align: 'right' },
                  { header: 'Application', cell: (m) => m.applicationSlug },
                  { header: 'Framework', cell: (m) => m.framework },
                  { header: 'Control', cell: (m) => m.controlId },
                  {
                    header: 'Flags',
                    cell: (m) => (
                      <>
                        {m.exactDuplicate ? <StatusPill status="exact dup" /> : null}
                        {m.sameControl ? <StatusPill status="same control" /> : null}
                        {m.crossApplication ? <StatusPill status="cross-app" /> : null}
                      </>
                    ),
                  },
                  { header: 'Hint', cell: (m) => m.reuseHint },
                ]}
              />
            )}
          </Section>
        </>
      ) : null}
    </>
  )
}

// ---- cross-framework reuse: browse the evidence held for a control ---------

function BrowseByControl() {
  const navigate = useNavigate()
  const controls = useAsync(() => listReuseControls(), [])
  const [controlId, setControlId] = useState('')

  const trimmed = controlId.trim().toUpperCase()
  const known = useMemo(
    () => (controls.data ?? []).some((c) => c.controlId.toUpperCase() === trimmed),
    [controls.data, trimmed],
  )
  const result = useAsync(
    () => (known ? getReuseByControl(trimmed) : Promise.resolve(undefined)),
    [known ? trimmed : ''],
  )

  const [acting, setActing] = useState<string>()
  const [actionError, setActionError] = useState<string>()

  async function reuseFor(evidenceId: string, framework: string) {
    setActing(`${evidenceId}:${framework}`)
    setActionError(undefined)
    try {
      await addEvidenceFramework(evidenceId, framework)
      result.reload()
    } catch (e) {
      setActionError(e instanceof Error ? e.message : String(e))
    } finally {
      setActing(undefined)
    }
  }

  const data = result.data

  return (
    <>
      <Section title="Pick a control">
        <div className="filter-row">
          <label>
            Control
            <input
              aria-label="Control"
              list="reuse-control-list"
              value={controlId}
              onChange={(e) => setControlId(e.target.value)}
              placeholder="OS-SSH-ROOT-LOGIN"
            />
            <datalist id="reuse-control-list">
              {(controls.data ?? []).map((c) => (
                <option key={c.controlId} value={c.controlId}>
                  {c.frameworks.join(', ')}
                </option>
              ))}
            </datalist>
          </label>
        </div>
        {controls.error ? <ErrorNote message={controls.error} /> : null}
        <p className="muted small">
          A control required by several frameworks can reuse the evidence already held for it — no
          re-collection. Catalogue from <code>GET /api/v1/insight/reuse/controls</code> (UC03 mapping).
        </p>
      </Section>

      {controlId && !known && !controls.loading ? (
        <Empty message={`"${controlId}" is not a known control. Pick one from the list.`} />
      ) : null}

      {result.loading ? <Loading what="evidence for this control" /> : null}
      {result.error ? <ErrorNote message={result.error} /> : null}
      {actionError ? <ErrorNote message={actionError} /> : null}

      {data ? (
        <>
          <div className="stat-grid">
            <StatCard label="Frameworks satisfied" value={data.frameworks.length} />
            <StatCard label="Evidence records" value={data.evidence.length} />
            <StatCard
              label="Applications covered"
              value={new Set(data.evidence.map((e) => e.applicationSlug)).size}
            />
          </div>

          <Section title={`Frameworks requiring ${data.controlId}`}>
            {data.frameworks.length === 0 ? (
              <Empty message="This control is not mapped to any framework in the UC03 catalogue." />
            ) : (
              <p>
                {data.frameworks.map((f) => (
                  <StatusPill key={f} status={f} />
                ))}
              </p>
            )}
          </Section>

          <Section title={`Existing evidence — ${data.evidence.length}`}>
            {data.evidence.length === 0 ? (
              <Empty message="No evidence held for this control yet — it must be collected at least once." />
            ) : (
              <DataTable
                rows={data.evidence}
                rowKey={(e) => e.evidenceId}
                columns={[
                  { header: 'Application', cell: (e) => e.applicationSlug },
                  { header: 'Source', cell: (e) => e.sourceSystem },
                  { header: 'Method', cell: (e) => e.collectionMethod ?? '—' },
                  {
                    header: 'Collected',
                    cell: (e) => (e.collectedAt ? new Date(e.collectedAt).toLocaleDateString() : '—'),
                  },
                  { header: 'SHA-256', cell: (e) => <code>{(e.sha256 ?? '—').slice(0, 16)}…</code> },
                  {
                    header: 'Mapped frameworks',
                    cell: (e) => e.mappedFrameworks.map((f) => <StatusPill key={f} status={f} />),
                  },
                  {
                    header: 'Reuse',
                    cell: (e) => {
                      const missing = data.frameworks.filter((f) => !e.mappedFrameworks.includes(f))
                      if (missing.length === 0) return <span className="muted small">all frameworks mapped</span>
                      return (
                        <span className="row-actions">
                          {missing.map((f) => (
                            <button
                              key={f}
                              disabled={acting === `${e.evidenceId}:${f}`}
                              onClick={(ev) => {
                                ev.stopPropagation()
                                reuseFor(e.evidenceId, f)
                              }}
                            >
                              {acting === `${e.evidenceId}:${f}` ? 'Adding…' : `Reuse for ${f}`}
                            </button>
                          ))}
                        </span>
                      )
                    },
                  },
                  {
                    header: '',
                    cell: (e) => (
                      <button className="ghost" onClick={() => navigate(`/evidence/${e.evidenceId}`)}>
                        Open
                      </button>
                    ),
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
