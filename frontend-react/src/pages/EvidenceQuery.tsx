import { useState } from 'react'
import { runEvidenceQuery } from '../api/endpoints'
import type { DeterministicQueryResult } from '../api/types'
import { DataTable, ErrorNote, JsonBlock, Section } from '../components/ui'

const QUERIES = ['source-breakdown', 'stale-evidence', 'latest-per-control', 'duplicates', 'freshness'] as const

export function EvidenceQuery() {
  const [name, setName] = useState<string>(QUERIES[0])
  const [applicationSlug, setApp] = useState('')
  const [framework, setFramework] = useState('')
  const [result, setResult] = useState<DeterministicQueryResult>()
  const [error, setError] = useState<string>()
  const [running, setRunning] = useState(false)

  async function run() {
    setRunning(true)
    setError(undefined)
    try {
      setResult(
        await runEvidenceQuery(name, {
          applicationSlug: applicationSlug || undefined,
          framework: framework || undefined,
        }),
      )
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e))
    } finally {
      setRunning(false)
    }
  }

  const columns = result && result.rows.length > 0 ? Object.keys(result.rows[0]) : []

  return (
    <div className="page">
      <h1>Evidence Query</h1>
      <p className="muted">Deterministic repository queries (no LLM). Backed by <code>GET /api/v1/evidence/query/&#123;name&#125;</code>.</p>

      <Section
        title="Run a query"
        actions={
          <button onClick={run} disabled={running}>
            {running ? 'Running…' : 'Run'}
          </button>
        }
      >
        <div className="filter-row">
          <label>
            Query
            <select aria-label="Query" value={name} onChange={(e) => setName(e.target.value)}>
              {QUERIES.map((q) => (
                <option key={q} value={q}>
                  {q}
                </option>
              ))}
            </select>
          </label>
          <label>
            Application
            <input aria-label="Application" value={applicationSlug} onChange={(e) => setApp(e.target.value)} placeholder="(all)" />
          </label>
          <label>
            Framework
            <input aria-label="Framework" value={framework} onChange={(e) => setFramework(e.target.value)} placeholder="(all)" />
          </label>
        </div>
      </Section>

      {error ? <ErrorNote message={error} /> : null}

      {result ? (
        <>
          <Section title="Answer">
            <p className="answer">{result.answerText}</p>
            <p className="muted small">generated {new Date(result.generatedAt).toLocaleString()}</p>
            <JsonBlock value={result.counts} />
          </Section>
          <Section title={`Rows — ${result.rows.length}`}>
            {result.rows.length === 0 ? (
              <p className="muted">No rows.</p>
            ) : (
              <DataTable<Record<string, unknown>>
                rows={result.rows}
                rowKey={(r) => String(result.rows.indexOf(r))}
                columns={columns.map((c) => ({
                  header: c,
                  cell: (row: Record<string, unknown>) => String(row[c] ?? ''),
                }))}
              />
            )}
          </Section>
        </>
      ) : null}
    </div>
  )
}
