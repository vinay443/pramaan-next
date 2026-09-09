import { useState } from 'react'
import { getEvidenceSummary } from '../api/endpoints'
import type { EvidenceSummary } from '../api/types'
import { ErrorNote, Section, StatusPill } from './ui'

/** AI evidence summary — grounded in stored content + check verdicts. Deterministic in mock mode. */
export function EvidenceSummaryPanel({ evidenceId }: { evidenceId: string }) {
  const [summary, setSummary] = useState<EvidenceSummary>()
  const [error, setError] = useState<string>()
  const [busy, setBusy] = useState(false)

  async function generate() {
    setBusy(true)
    setError(undefined)
    try {
      setSummary(await getEvidenceSummary(evidenceId))
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e))
    } finally {
      setBusy(false)
    }
  }

  return (
    <Section
      title="AI summary"
      actions={
        <button onClick={generate} disabled={busy}>
          {busy ? 'Generating…' : summary ? 'Regenerate' : 'Generate summary'}
        </button>
      }
    >
      {error ? <ErrorNote message={error} /> : null}
      {!summary && !error ? (
        <p className="muted">
          Generates a summary grounded strictly in this evidence — its metadata, tags and deterministic check
          verdicts. Nothing is inferred.
        </p>
      ) : null}
      {summary ? (
        <>
          <p>
            <span className="muted">model {summary.model}</span>{' '}
            {summary.simulated ? <StatusPill status="simulated" /> : <StatusPill status="live" />}
          </p>
          <p className="answer" style={{ whiteSpace: 'pre-wrap' }}>
            {summary.summary}
          </p>
          <details>
            <summary className="muted small">grounded on {summary.groundedOn.length} facts</summary>
            <ul>
              {summary.groundedOn.map((g, i) => (
                <li key={i} className="small">
                  {g}
                </li>
              ))}
            </ul>
          </details>
        </>
      ) : null}
    </Section>
  )
}
