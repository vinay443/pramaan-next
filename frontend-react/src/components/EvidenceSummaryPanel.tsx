import { useState } from 'react'
import { getEvidenceSummary } from '../api/endpoints'
import type { EvidenceSummary } from '../api/types'
import { useAsync } from '../hooks/useAsync'
import { DataTable, ErrorNote, Loading, Section, StatusPill } from './ui'

const FACTS_SHOWN = 12

type Tone = 'ok' | 'bad' | 'muted' | 'warn'

/** Text label first (never colour alone); tone only reinforces it. */
function Pill({ label, tone }: { label: string; tone: Tone }) {
  return <StatusPill status={label.replace(/_/g, ' ')} tone={tone} />
}

const VERDICT_TONE: Record<string, Tone> = {
  PASS: 'ok',
  WARNING: 'warn',
  FAIL: 'bad',
  NOT_APPLICABLE: 'muted',
  NOT_ASSESSED: 'muted',
  INFO: 'muted',
}

const tone = (s: string): Tone => VERDICT_TONE[s] ?? 'muted'

/**
 * Evidence summary — built automatically by the backend from fixed rules over the stored content,
 * its check verdicts and its metadata. No AI model is involved; nothing here is generated on request.
 */
export function EvidenceSummaryPanel({ evidenceId, version }: { evidenceId: string; version?: number }) {
  const { data: s, loading, error, reload } = useAsync(() => getEvidenceSummary(evidenceId, version), [evidenceId, version])
  const [showAll, setShowAll] = useState(false)

  return (
    <Section
      title="Evidence summary"
      actions={
        <button type="button" onClick={reload} disabled={loading}>
          {loading && s ? 'Refreshing…' : 'Refresh'}
        </button>
      }
    >
      {error ? <ErrorNote message={error} /> : null}
      {loading && !s ? <Loading what="evidence summary" /> : null}
      {s ? <SummaryBody s={s} showAll={showAll} onToggle={() => setShowAll((v) => !v)} /> : null}
    </Section>
  )
}

function SummaryBody({ s, showAll, onToggle }: { s: EvidenceSummary; showAll: boolean; onToggle: () => void }) {
  const { control, health } = s
  const facts = showAll ? s.facts : s.facts.slice(0, FACTS_SHOWN)
  const collected = health.collectedAt ? new Date(health.collectedAt).toLocaleDateString() : 'unknown date'

  return (
    <>
      <p>
        <Pill label={s.verdict} tone={tone(s.verdict)} /> {s.simulated ? <StatusPill status="Simulated data" tone="muted" /> : null}
      </p>
      <p className="answer">{s.headline}</p>

      <h3>Control</h3>
      <p>
        <strong>{control.name}</strong>{' '}
        <span className="muted small">
          <code>{control.controlId}</code> · {control.framework}
        </span>
      </p>
      {control.description && control.description !== control.name ? (
        <p className="small">{control.description}</p>
      ) : null}
      {control.alsoSatisfies.length > 0 ? (
        <p className="small">
          <span className="muted">Also relevant to</span>{' '}
          <span className="chip-row" style={{ display: 'inline-flex' }}>
            {control.alsoSatisfies.map((a) => (
              <span key={a.framework} className="tag" title={`${a.controlId} — ${a.name}`}>
                {a.framework}
              </span>
            ))}
          </span>
        </p>
      ) : null}

      <h3>Key facts</h3>
      {s.facts.length === 0 ? (
        <p className="muted">No extractable facts.</p>
      ) : (
        <>
          <DataTable
            rows={facts}
            rowKey={(f) => `${f.label}|${f.pointer}|${f.value}`}
            columns={[
              { header: 'Fact', cell: (f) => f.label },
              { header: 'Value', cell: (f) => <code>{f.value}</code> },
              { header: 'Found at', cell: (f) => <code>{f.pointer}</code> },
            ]}
          />
          {s.facts.length > FACTS_SHOWN ? (
            <p>
              <button type="button" className="ghost" onClick={onToggle}>
                {showAll ? 'Show fewer facts' : `Show all ${s.facts.length} facts`}
              </button>
            </p>
          ) : null}
        </>
      )}

      {s.checks.length > 0 ? (
        <>
          <h3>Automated checks</h3>
          <DataTable
            rows={s.checks}
            rowKey={(c) => c.checkId}
            columns={[
              { header: 'Check', cell: (c) => <code>{c.checkId}</code> },
              { header: 'Status', cell: (c) => <Pill label={c.status} tone={tone(c.status)} /> },
              { header: 'Observed', cell: (c) => c.observed ?? '—' },
              { header: 'Expected', cell: (c) => c.expected ?? '—' },
              { header: 'Detail', cell: (c) => c.detail ?? '—' },
            ]}
          />
        </>
      ) : null}

      {s.findings.length > 0 ? (
        <>
          <h3>Findings</h3>
          <ul aria-label="Findings">
            {s.findings.map((f, i) => (
              <li key={`${f.pointer}|${i}`} className="small">
                <Pill label={f.severity} tone={tone(f.severity)} /> {f.message}{' '}
                <span className="muted">
                  (<code>{f.pointer}</code>)
                </span>
              </li>
            ))}
          </ul>
        </>
      ) : null}

      <h3>Evidence health</h3>
      <p className="chip-row small" aria-label="Evidence health">
        <span>
          Integrity{' '}
          <Pill
            label={health.integrity}
            tone={health.integrity === 'INTACT' ? 'ok' : health.integrity === 'MISMATCH' ? 'bad' : 'muted'}
          />
        </span>
        <span>Collected {collected}</span>
        {health.ageDays != null ? <span>{health.ageDays} days old</span> : null}
        <span>
          <Pill label={health.freshness} tone={health.freshness === 'CURRENT' ? 'ok' : 'warn'} />
        </span>
        <span>State {health.lifecycleState}</span>
        <span>
          version {s.version} of {health.versionCount}
        </span>
        {health.missingTags.length > 0 ? <span>Missing tags: {health.missingTags.join(', ')}</span> : null}
      </p>

      {s.limits.length > 0 ? (
        <>
          <h3 className="muted small">What this summary could not see</h3>
          <ul className="muted small" aria-label="Limits">
            {s.limits.map((l) => (
              <li key={l}>{l}</li>
            ))}
          </ul>
        </>
      ) : null}

      <p className="muted small">
        Generated automatically by fixed rules from the stored content of version {s.version}. No AI model is involved:
        the same evidence always produces the same summary.
      </p>
    </>
  )
}
