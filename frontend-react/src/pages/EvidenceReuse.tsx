import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { addEvidenceFramework, getReuseByControl, isMockSourced, listReuseControls } from '../api/endpoints'
import { suppressDataSourceBanner } from '../api/dataSource'
import type { ReuseEvidenceDetail, ReuseResult, SimilarEvidence } from '../api/types'
import { useAsync } from '../hooks/useAsync'
import { DataTable, Empty, ErrorNote, Loading, Modal, Section, StatCard, StatusPill } from '../components/ui'

type Tab = 'similar' | 'control'

export function EvidenceReuse() {
  const [tab, setTab] = useState<Tab>('similar')

  return (
    <div className="page reuse-page">
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

// ---- embedding-based similarity search --------------------------------------

const RESULT_LIMITS = [5, 10, 20] as const

/** Match strictness → minimum similarity score. Broad/Standard anchor on the previous
 *  UI default (0.1) and the backend default (0.3); Strict adds a tier above them. */
const STRICTNESS = {
  broad: { label: 'Broad', minScore: 0.1 },
  standard: { label: 'Standard', minScore: 0.3 },
  strict: { label: 'Strict', minScore: 0.6 },
} as const
type Strictness = keyof typeof STRICTNESS

/** Evidence-type presets → the similarity-search query. The keys are the canonical `evidenceType`
 *  tag values (EvidenceNaming.resolveEvidenceType in the backend). Each query pairs the type's own
 *  words with the technology / collector / control words that evidence of that type typically
 *  carries in its metadata. Bare category labels ("TLS configuration") share too few tokens with
 *  a record to clear the Broad threshold in mock mode; these longer phrases do. */
const EVIDENCE_TYPE_QUERIES = {
  'HOST-CONFIG': 'host config linux os agent',
  'DB-CONFIG': 'db config database postgresql agent',
  'MIDDLEWARE-CONFIG': 'middleware config nginx agent tls',
  'TLS-SCAN': 'tls scan cert expiry agent',
  'CHANGE-TICKET': 'change ticket chg jira itpp',
  'CODE-REVIEW': 'code review sdlc github dpsc',
  'AGENT-SCAN': 'agent scan network firewall rules',
  GENERAL: 'general policy document access review',
} as const
type EvidenceType = keyof typeof EVIDENCE_TYPE_QUERIES
const EVIDENCE_TYPES = Object.keys(EVIDENCE_TYPE_QUERIES) as EvidenceType[]

function evidenceLabel(e: ReuseEvidenceDetail): string {
  return `${e.controlId} — ${e.fileName}`
}

/** This tab is intentionally self-contained: it reads the local demo corpus in mocks.ts directly and
 *  never calls the backend (no Ollama / pgvector dependency). Loaded lazily, like endpoints.ts does. */
const loadReuseMocks = () => import('../api/mocks')

function FlagPills({ m }: { m: SimilarEvidence }) {
  const flags = [
    m.exactDuplicate ? <StatusPill key="dup" status="Exact duplicate" tone="ok" /> : null,
    m.sameControl ? <StatusPill key="ctl" status="Same control" tone="warn" /> : null,
    m.crossApplication ? <StatusPill key="app" status="Cross-application" tone="muted" /> : null,
  ].filter(Boolean)
  return flags.length > 0 ? <span className="row-actions">{flags}</span> : <span className="muted small">—</span>
}

function FindSimilar() {
  const [mode, setMode] = useState<'evidence' | 'type'>('type')
  const [applicationSlug, setApplicationSlug] = useState('')
  const [evidenceId, setEvidenceId] = useState('')
  const [evidenceType, setEvidenceType] = useState<EvidenceType | ''>('')
  const [limit, setLimit] = useState<number>(RESULT_LIMITS[0])
  // Broad (0.1) is the default so the page keeps returning what it did before strictness was exposed.
  const [strictness, setStrictness] = useState<Strictness>('broad')
  const [result, setResult] = useState<ReuseResult>()
  const [error, setError] = useState<string>()
  const [busy, setBusy] = useState(false)
  const [selected, setSelected] = useState<SimilarEvidence>()
  const latest = useRef(0)

  // Always-mock tab: hide the shell's "Backend unavailable" banner while it is mounted.
  useEffect(() => suppressDataSourceBanner(), [])

  const apps = useAsync(async () => (await loadReuseMocks()).mockReuseApplications(), [])
  const evidenceList = useAsync(
    async () => (applicationSlug ? (await loadReuseMocks()).mockReuseEvidenceFor(applicationSlug) : undefined),
    [applicationSlug],
  )
  const evidenceOptions = applicationSlug ? evidenceList.data ?? [] : []

  const minScore = STRICTNESS[strictness].minScore

  async function run(kind: 'evidence' | 'type' = mode) {
    const ticket = ++latest.current
    setBusy(true)
    setError(undefined)
    try {
      const mocks = await loadReuseMocks()
      const out =
        kind === 'evidence'
          ? mocks.mockSimilarByEvidence(evidenceId, limit, minScore)
          : mocks.mockSimilarByText(EVIDENCE_TYPE_QUERIES[evidenceType as EvidenceType], limit, minScore)
      if (ticket === latest.current) setResult(out)
    } catch (e) {
      if (ticket === latest.current) setError(e instanceof Error ? e.message : String(e))
    } finally {
      if (ticket === latest.current) setBusy(false)
    }
  }

  // Neither mode has a Search button: picking an item / type (or changing the result
  // count / strictness while one is picked) runs the search.
  useEffect(() => {
    if (mode === 'evidence' && evidenceId) void run('evidence')
    else if (mode === 'type' && evidenceType) void run('type')
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [mode, evidenceId, evidenceType, limit, minScore])

  function clearResult() {
    latest.current++
    setResult(undefined)
    setError(undefined)
    setBusy(false)
    setSelected(undefined)
  }

  function changeMode(next: 'evidence' | 'type') {
    setMode(next)
    clearResult()
  }

  function changeApplication(slug: string) {
    setApplicationSlug(slug)
    setEvidenceId('')
    clearResult()
  }

  function changeEvidenceType(t: EvidenceType | '') {
    setEvidenceType(t)
    if (!t) clearResult()
  }

  function changeEvidence(id: string) {
    setEvidenceId(id)
    if (!id) clearResult()
  }

  const columns = [
    { header: 'Score', cell: (m: SimilarEvidence) => m.score.toFixed(3), align: 'right' as const },
    { header: 'Application', cell: (m: SimilarEvidence) => m.applicationSlug },
    { header: 'Framework', cell: (m: SimilarEvidence) => m.framework },
    { header: 'Control', cell: (m: SimilarEvidence) => m.controlId },
    { header: 'Flags', cell: (m: SimilarEvidence) => <FlagPills m={m} /> },
    { header: 'Hint', cell: (m: SimilarEvidence) => m.reuseHint },
  ]

  const hasQuery = mode === 'evidence' ? !!evidenceId : !!evidenceType

  return (
    <>
      <Section title="Find similar evidence">
        <div className="filter-row">
          <label>
            Mode
            <select aria-label="Mode" value={mode} onChange={(e) => changeMode(e.target.value as 'evidence' | 'type')}>
              <option value="type">By evidence type</option>
              <option value="evidence">By evidence ID</option>
            </select>
          </label>
          {mode === 'evidence' ? (
            <>
              <label>
                Application
                <select aria-label="Application" value={applicationSlug} onChange={(e) => changeApplication(e.target.value)}>
                  <option value="">(select an application)</option>
                  {(apps.data ?? []).map((a) => (
                    <option key={a.slug} value={a.slug}>
                      {a.name}
                    </option>
                  ))}
                </select>
              </label>
              <label>
                Evidence
                <select
                  aria-label="Evidence"
                  value={evidenceId}
                  disabled={!applicationSlug || evidenceList.loading}
                  onChange={(e) => changeEvidence(e.target.value)}
                >
                  <option value="">{applicationSlug ? '(select evidence)' : '(select an application first)'}</option>
                  {evidenceOptions.map((e) => (
                    <option key={e.evidenceId} value={e.evidenceId}>
                      {evidenceLabel(e)}
                    </option>
                  ))}
                </select>
              </label>
            </>
          ) : (
            <label>
              Evidence type
              <select
                aria-label="Evidence type"
                value={evidenceType}
                onChange={(e) => changeEvidenceType(e.target.value as EvidenceType | '')}
              >
                <option value="">(select a type)</option>
                {EVIDENCE_TYPES.map((t) => (
                  <option key={t} value={t}>
                    {t}
                  </option>
                ))}
              </select>
            </label>
          )}
          <label>
            Number of results
            <select aria-label="Number of results" value={limit} onChange={(e) => setLimit(Number(e.target.value))}>
              {RESULT_LIMITS.map((n) => (
                <option key={n} value={n}>
                  {n}
                </option>
              ))}
            </select>
          </label>
          <label>
            Match strictness
            <select
              aria-label="Match strictness"
              value={strictness}
              onChange={(e) => setStrictness(e.target.value as Strictness)}
            >
              {(Object.keys(STRICTNESS) as Strictness[]).map((k) => (
                <option key={k} value={k}>
                  {STRICTNESS[k].label} (≥ {STRICTNESS[k].minScore})
                </option>
              ))}
            </select>
          </label>
        </div>
        {mode === 'evidence' && applicationSlug && !evidenceList.loading && evidenceOptions.length === 0 ? (
          <p className="muted small">No evidence is held for this application yet.</p>
        ) : null}
        {evidenceList.error ? <ErrorNote message={evidenceList.error} /> : null}
        {apps.error ? <ErrorNote message={apps.error} /> : null}
      </Section>

      {error ? <ErrorNote message={error} /> : null}
      {busy && !result ? <Loading what="similar evidence" /> : null}

      {!result && !busy && !error && !hasQuery ? (
        <Section title="Matches">
          <Empty
            message={
              mode === 'evidence'
                ? 'Select an application and an evidence item to find similar evidence'
                : 'Select an evidence type to find similar evidence'
            }
          />
        </Section>
      ) : null}

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
                onRowClick={setSelected}
                columns={columns.filter((c) => c.header !== 'Score')}
              />
            </Section>
          ) : null}

          <Section title="Matches">
            {result.matches.length === 0 ? (
              <Empty message="No evidence above the similarity threshold. Try a broader match strictness." />
            ) : (
              <DataTable
                rows={result.matches}
                rowKey={(m) => m.evidenceId}
                onRowClick={setSelected}
                columns={columns}
              />
            )}
          </Section>
        </>
      ) : null}

      {selected ? <ReuseDetailModal match={selected} onClose={() => setSelected(undefined)} /> : null}
    </>
  )
}

/** Detail view for one result row. All fields come from the local mock corpus. */
function ReuseDetailModal({ match, onClose }: { match: SimilarEvidence; onClose: () => void }) {
  const detail = useAsync(async () => (await loadReuseMocks()).mockReuseDetail(match.evidenceId), [match.evidenceId])
  const d = detail.data

  return (
    <Modal title="Evidence detail" onClose={onClose} size="lg">
      {detail.loading && !d ? <Loading what="evidence detail" /> : null}
      {!detail.loading && !d ? <Empty message="No detail is available for this evidence." /> : null}
      {d ? (
        <>
          <dl className="kv">
            <dt>Evidence ID</dt>
            <dd>
              <code>{d.evidenceId}</code>
            </dd>
            <dt>Application</dt>
            <dd>{d.applicationSlug}</dd>
            <dt>Framework</dt>
            <dd>{d.framework}</dd>
            <dt>Control</dt>
            <dd>{d.controlId}</dd>
            <dt>Similarity score</dt>
            <dd>
              {match.score.toFixed(3)} <FlagPills m={match} />
            </dd>
            <dt>Reuse hint</dt>
            <dd>{match.reuseHint}</dd>
            <dt>Evidence type</dt>
            <dd>{d.evidenceType}</dd>
            <dt>File name</dt>
            <dd>
              <code>{d.fileName}</code>
            </dd>
            <dt>File type</dt>
            <dd>
              {d.contentType} · {formatBytes(d.sizeBytes)}
            </dd>
            <dt>Uploaded</dt>
            <dd>{new Date(d.uploadedAt).toLocaleString()}</dd>
            <dt>Collected by</dt>
            <dd>
              {d.collectedBy} via {d.sourceSystem} (v{d.version})
            </dd>
            <dt>SHA-256</dt>
            <dd>
              <code>{d.sha256}</code>
            </dd>
          </dl>
          <h3>Content preview</h3>
          <pre className="json">{d.preview}</pre>
        </>
      ) : null}
      <div className="modal-foot">
        <button onClick={onClose}>Close</button>
      </div>
    </Modal>
  )
}

function formatBytes(n: number): string {
  return n >= 1024 ? `${(n / 1024).toFixed(1)} KB` : `${n} B`
}

/** Shown when the data on screen came from the built-in mock fixtures, not the backend. */
function MockBadge({ show }: { show: boolean }) {
  if (!show) return null
  return (
    <span title="The backend was unreachable or returned an error; this data comes from built-in sample fixtures.">
      <StatusPill status="Sample data (mock fallback)" tone="warn" />
    </span>
  )
}

// ---- cross-framework reuse: browse the evidence held for a control ---------

function BrowseByControl() {
  const navigate = useNavigate()
  const controls = useAsync(() => listReuseControls(), [])
  const [controlId, setControlId] = useState('')

  const result = useAsync(
    () => (controlId ? getReuseByControl(controlId) : Promise.resolve(undefined)),
    [controlId],
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

  function pick(id: string) {
    setControlId(id)
    setActionError(undefined)
  }

  // useAsync keeps the previous data while a new fetch is in flight — only trust it
  // when it belongs to the control currently selected.
  const data = controlId && result.data?.controlId.toUpperCase() === controlId.toUpperCase() ? result.data : undefined
  const mockSourced = isMockSourced(data) || isMockSourced(controls.data)

  const satisfied = data ? data.frameworks.filter((f) => data.evidence.some((e) => e.mappedFrameworks.includes(f))) : []
  const unmapped = data ? data.frameworks.filter((f) => !satisfied.includes(f)) : []

  return (
    <>
      <Section
        title="Pick a control"
        actions={<MockBadge show={mockSourced} />}
      >
        <div className="filter-row">
          <label>
            Control
            <select aria-label="Control" value={controlId} onChange={(e) => pick(e.target.value)}>
              <option value="">(select a control)</option>
              {(controls.data ?? []).map((c) => (
                <option key={c.controlId} value={c.controlId}>
                  {c.frameworks.length > 0 ? `${c.controlId} — ${c.frameworks.join(', ')}` : c.controlId}
                </option>
              ))}
            </select>
          </label>
        </div>
        {controls.error ? <ErrorNote message={controls.error} /> : null}
        <p className="muted small">
          A control required by several frameworks can reuse the evidence already held for it — no
          re-collection. Catalogue from <code>GET /api/v1/insight/reuse/controls</code> (UC03 mapping).
        </p>
      </Section>

      {!controlId ? (
        <Section title="Reusable evidence">
          <Empty message="Select a control to see reusable evidence" />
        </Section>
      ) : null}

      {controlId && result.loading && !data ? <Loading what="evidence for this control" /> : null}
      {controlId && result.error ? <ErrorNote message={result.error} /> : null}
      {actionError ? <ErrorNote message={actionError} /> : null}

      {data ? (
        <>
          <div className="stat-grid">
            <StatCard label="Evidence matched" value={data.evidence.length} />
            <StatCard label="Frameworks satisfied" value={satisfied.length} hint={`of ${data.frameworks.length} required`} />
            <StatCard label="Frameworks unmapped" value={unmapped.length} hint="no evidence tagged yet" />
          </div>

          <Section title={`Frameworks requiring ${data.controlId}`}>
            {data.frameworks.length === 0 ? (
              <Empty message="This control is not mapped to any framework in the UC03 catalogue." />
            ) : (
              <div className="row-actions">
                {satisfied.map((f) => (
                  <span key={f} title="Existing evidence is already tagged to this framework">
                    <StatusPill status={f} tone="ok" />
                  </span>
                ))}
                {unmapped.map((f) => (
                  <span key={f} title="No held evidence is tagged to this framework yet">
                    <StatusPill status={f} tone="muted" />
                  </span>
                ))}
              </div>
            )}
            {data.frameworks.length > 0 ? (
              <p className="muted small">Green = satisfied by existing evidence · grey = still unmapped.</p>
            ) : null}
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
                    cell: (e) => e.mappedFrameworks.map((f) => <StatusPill key={f} status={f} tone="ok" />),
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
