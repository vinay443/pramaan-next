import { useState } from 'react'
import { Link } from 'react-router-dom'
import { askNlQuery, listApplications } from '../api/endpoints'
import type { NlQueryResult } from '../api/types'
import { useAsync } from '../hooks/useAsync'
import { ErrorNote, JsonBlock, Section, StatusPill } from '../components/ui'

const EXAMPLES = [
  'Which controls are missing evidence?',
  'What is our compliance posture?',
  'What evidence do we have for SSH root login?',
  'What evidence is stale?',
  'Where does our evidence come from?',
]

export function NlQuery() {
  const apps = useAsync(() => listApplications(), [])
  const [question, setQuestion] = useState(EXAMPLES[0])
  const [slug, setSlug] = useState('')
  const [result, setResult] = useState<NlQueryResult>()
  const [error, setError] = useState<string>()
  const [busy, setBusy] = useState(false)

  async function ask(q = question) {
    setQuestion(q)
    setBusy(true)
    setError(undefined)
    try {
      setResult(await askNlQuery(q, slug || undefined))
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="page">
      <h1>Natural-language Queries</h1>
      <p className="muted">
        Ask about the evidence repository in plain English. Routing is deterministic; the model only phrases
        the answer. Backed by <code>POST /api/v1/insight/nl-query</code>.
      </p>

      <Section
        title="Ask"
        actions={
          <button onClick={() => ask()} disabled={busy || !question.trim()}>
            {busy ? 'Asking…' : 'Ask'}
          </button>
        }
      >
        <div className="filter-row">
          <label style={{ flex: 1 }}>
            Question
            <input aria-label="Question" value={question} onChange={(e) => setQuestion(e.target.value)} />
          </label>
          <label>
            Application
            <select aria-label="Application" value={slug} onChange={(e) => setSlug(e.target.value)}>
              <option value="">(all)</option>
              {(apps.data ?? []).map((a) => (
                <option key={a.slug} value={a.slug}>
                  {a.name}
                </option>
              ))}
            </select>
          </label>
        </div>
        <div className="filter-row">
          {EXAMPLES.map((ex) => (
            <button key={ex} type="button" className="pill pill-muted" onClick={() => ask(ex)}>
              {ex}
            </button>
          ))}
        </div>
      </Section>

      {error ? <ErrorNote message={error} /> : null}

      {result ? (
        <>
          <Section title="Answer">
            <p>
              <span className="muted">Interpreted as:</span> {result.interpretedAs}{' '}
              <StatusPill status={result.matchedQuery} />
              {result.simulated ? <StatusPill status="simulated" /> : null}
            </p>
            <p className="answer">{result.narrative}</p>
            <p className="muted small">model {result.model}</p>
            {Array.isArray((result.answer as Record<string, unknown>).evidenceCitations) ? (
              <p className="muted small">
                grounded in{' '}
                {((result.answer as Record<string, unknown>).evidenceCitations as string[]).length === 0
                  ? 'no evidence (repository returned nothing)'
                  : ((result.answer as Record<string, unknown>).evidenceCitations as string[]).map((id, i) => (
                      <span key={id}>
                        {i > 0 ? ', ' : ''}
                        <Link to={`/evidence/${id}`}>{id.slice(0, 8)}</Link>
                      </span>
                    ))}
              </p>
            ) : null}
          </Section>
          <Section title="Structured result">
            <JsonBlock value={result.answer} />
          </Section>
        </>
      ) : null}
    </div>
  )
}
