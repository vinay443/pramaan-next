import { Fragment, useState } from 'react'
import { addEvidenceFramework, getReuseByControl } from '../api/endpoints'
import type { EvidenceView } from '../api/types'
import { useAsync } from '../hooks/useAsync'
import { ErrorNote, Loading, Section } from './ui'

const CANONICAL_KEYS = [
  ['application', 'Application'],
  ['technology', 'Technology'],
  ['control', 'Control'],
  ['evidenceType', 'Evidence type'],
  ['collectionMethod', 'Collection method'],
  ['version', 'Version'],
] as const

function mappedFrameworks(ev: EvidenceView): string[] {
  return (ev.tags.frameworks ?? ev.framework)
    .split(',')
    .map((f) => f.trim().toUpperCase())
    .filter(Boolean)
}

/**
 * UC03 metadata tagging — shows the canonical naming-convention tags for one
 * evidence record and lets a user map it to an additional framework its control
 * satisfies. Reuses the existing `POST /api/v1/evidence/{id}/frameworks` path
 * (EvidenceController → EvidenceIngestionService.addFrameworkMapping) — there is
 * no separate tagging endpoint.
 */
export function MetadataTaggingPanel({
  evidence,
  onUpdated,
}: {
  evidence: EvidenceView
  onUpdated: (ev: EvidenceView) => void
}) {
  const reuse = useAsync(() => getReuseByControl(evidence.controlId), [evidence.controlId])
  const [selected, setSelected] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string>()

  const current = mappedFrameworks(evidence)
  const available = (reuse.data?.frameworks ?? []).filter((f) => !current.includes(f.toUpperCase()))

  async function mapFramework() {
    if (!selected) return
    setBusy(true)
    setError(undefined)
    try {
      const updated = await addEvidenceFramework(evidence.evidenceId, selected)
      onUpdated(updated)
      setSelected('')
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e))
    } finally {
      setBusy(false)
    }
  }

  return (
    <Section title="Metadata tagging">
      <dl className="kv">
        {CANONICAL_KEYS.filter(([key]) => evidence.tags[key]).map(([key, label]) => (
          <Fragment key={key}>
            <dt>{label}</dt>
            <dd>
              <code>{evidence.tags[key]}</code>
            </dd>
          </Fragment>
        ))}
        <dt>Mapped frameworks</dt>
        <dd>
          {current.map((f) => (
            <span key={f} className="tag">
              {f}
            </span>
          ))}
        </dd>
      </dl>

      {reuse.loading ? <Loading what="control-framework mapping" /> : null}
      {reuse.error ? <ErrorNote message={reuse.error} /> : null}
      {error ? <ErrorNote message={error} /> : null}

      {reuse.data ? (
        available.length > 0 ? (
          <p className="row-actions">
            <select value={selected} onChange={(e) => setSelected(e.target.value)} disabled={busy}>
              <option value="">Map to additional framework…</option>
              {available.map((f) => (
                <option key={f} value={f}>
                  {f}
                </option>
              ))}
            </select>
            <button disabled={busy || !selected} onClick={mapFramework}>
              {busy ? 'Mapping…' : 'Map framework'}
            </button>
          </p>
        ) : (
          <p className="muted small">
            Already mapped to every framework control {evidence.controlId} satisfies.
          </p>
        )
      ) : null}
    </Section>
  )
}
