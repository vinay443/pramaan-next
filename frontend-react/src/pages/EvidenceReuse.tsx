import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { getEvidenceReuse, searchEvidenceReuse } from '../api/endpoints'
import type { ReuseResult } from '../api/types'
import { DataTable, Empty, ErrorNote, Section, StatCard, StatusPill } from '../components/ui'

export function EvidenceReuse() {
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
    <div className="page">
      <h1>Evidence Reuse</h1>
      <p className="muted">
        Find evidence similar to a record or a description — reuse candidates for the same control across
        applications. Backed by <code>/api/v1/insight/reuse</code> (embedding search).
      </p>

      <Section
        title="Find similar evidence"
        actions={
          <button onClick={run} disabled={busy || (mode === 'evidence' ? !evidenceId.trim() : !text.trim())}>
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
                  { header: 'Score', cell: (m) => m.score.toFixed(3) },
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
    </div>
  )
}
