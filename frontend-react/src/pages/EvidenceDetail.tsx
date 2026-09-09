import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import {
  getEvidence,
  getEvidenceLifecycle,
  getEvidenceVersions,
  transitionEvidenceLifecycle,
  verifyEvidence,
} from '../api/endpoints'
import type { IntegrityReport, LifecycleAction } from '../api/types'
import { useAsync } from '../hooks/useAsync'
import { DataTable, ErrorNote, JsonBlock, Loading, Section, StatusPill } from '../components/ui'
import { EvidenceSummaryPanel } from '../components/EvidenceSummaryPanel'

export function EvidenceDetail() {
  const { id = '' } = useParams()
  const evidence = useAsync(() => getEvidence(id), [id])
  const versions = useAsync(() => getEvidenceVersions(id), [id])
  const lifecycle = useAsync(() => getEvidenceLifecycle(id), [id])
  const [report, setReport] = useState<IntegrityReport>()
  const [verifyErr, setVerifyErr] = useState<string>()
  const [verifying, setVerifying] = useState(false)
  const [lifeErr, setLifeErr] = useState<string>()
  const [transitioning, setTransitioning] = useState(false)

  async function transition(action: LifecycleAction) {
    setTransitioning(true)
    setLifeErr(undefined)
    try {
      await transitionEvidenceLifecycle(id, action, 'ui')
      lifecycle.reload()
      evidence.reload()
    } catch (e) {
      setLifeErr(e instanceof Error ? e.message : String(e))
    } finally {
      setTransitioning(false)
    }
  }

  const ACTIONS: Record<string, LifecycleAction[]> = {
    DRAFT: ['SUBMIT'],
    SUBMITTED: ['APPROVE', 'REJECT'],
    REJECTED: ['SUBMIT'],
    APPROVED: ['RETIRE'],
    EXPIRED: ['SUBMIT'],
    SUPERSEDED: [],
  }

  async function runVerify(version?: number) {
    setVerifying(true)
    setVerifyErr(undefined)
    try {
      setReport(await verifyEvidence(id, version))
    } catch (e) {
      setVerifyErr(e instanceof Error ? e.message : String(e))
    } finally {
      setVerifying(false)
    }
  }

  if (evidence.loading) return <Loading what="evidence" />
  if (evidence.error) return <ErrorNote message={evidence.error} />
  const ev = evidence.data
  if (!ev) return <ErrorNote message="Evidence not found." />

  return (
    <div className="page">
      <p>
        <Link to="/evidence">← Evidence Repository</Link>
      </p>
      <h1>
        {ev.controlId} <span className="muted">/ {ev.framework}</span>
      </h1>

      <Section title="Record">
        <dl className="kv">
          <dt>Name</dt>
          <dd><code>{ev.tags.name ?? ev.title ?? '—'}</code></dd>
          <dt>Frameworks</dt>
          <dd>
            {(ev.tags.frameworks ?? ev.framework).split(',').map((f) => (
              <span key={f} className="tag">{f}</span>
            ))}
          </dd>
          {ev.tags.evidenceType ? (
            <>
              <dt>Evidence type</dt>
              <dd>{ev.tags.evidenceType}</dd>
            </>
          ) : null}
          {ev.tags.sourceTitle ? (
            <>
              <dt>Source title</dt>
              <dd>{ev.tags.sourceTitle}</dd>
            </>
          ) : null}
          <dt>Evidence ID</dt>
          <dd>{ev.evidenceId}</dd>
          <dt>Key</dt>
          <dd>{ev.evidenceKey}</dd>
          <dt>Application</dt>
          <dd>{ev.applicationSlug}</dd>
          <dt>Source system</dt>
          <dd>{ev.sourceSystem}</dd>
          <dt>Current version</dt>
          <dd>{ev.currentVersion}</dd>
          <dt>Lifecycle</dt>
          <dd>{ev.lifecycleState ?? '—'}</dd>
          <dt>Updated</dt>
          <dd>{new Date(ev.updatedAt).toLocaleString()}</dd>
          <dt>Tags</dt>
          <dd>
            {Object.entries(ev.tags).length === 0
              ? '—'
              : Object.entries(ev.tags).map(([k, v]) => (
                  <span key={k} className="tag">
                    {k}={v}
                  </span>
                ))}
          </dd>
        </dl>
      </Section>

      <Section
        title="Lifecycle"
        actions={
          lifecycle.data ? (
            <span className="row-actions">
              {(ACTIONS[lifecycle.data.effectiveState] ?? []).map((a) => (
                <button key={a} disabled={transitioning} onClick={() => transition(a)}>
                  {a}
                </button>
              ))}
            </span>
          ) : null
        }
      >
        {lifecycle.loading ? <Loading what="lifecycle" /> : null}
        {lifeErr ? <ErrorNote message={lifeErr} /> : null}
        {lifecycle.data ? (
          <>
            <p>
              <StatusPill status={lifecycle.data.effectiveState} />
              {lifecycle.data.expired ? <StatusPill status="past retention" /> : null}
              {lifecycle.data.reviewedBy ? (
                <span className="muted"> reviewed by {lifecycle.data.reviewedBy}</span>
              ) : null}
              <span className="muted small">
                {' '}
                retention {lifecycle.data.retentionDays}d
                {lifecycle.data.ageDays != null ? ` · age ${lifecycle.data.ageDays}d` : ''}
              </span>
            </p>
            <DataTable
              rows={lifecycle.data.history}
              rowKey={(h) => `${h.occurredAt}-${h.action}`}
              columns={[
                { header: 'When', cell: (h) => new Date(h.occurredAt).toLocaleString() },
                { header: 'Action', cell: (h) => h.action },
                { header: 'From→To', cell: (h) => `${h.fromState ?? '—'} → ${h.toState}` },
                { header: 'Actor', cell: (h) => h.actor ?? '—' },
                { header: 'Note', cell: (h) => h.note ?? '' },
              ]}
            />
          </>
        ) : null}
      </Section>

      <Section
        title="Integrity"
        actions={
          <button disabled={verifying} onClick={() => runVerify()}>
            {verifying ? 'Verifying…' : 'Verify current version (SHA-256)'}
          </button>
        }
      >
        {verifyErr ? <ErrorNote message={verifyErr} /> : null}
        {report ? (
          <div className={`verify ${report.intact ? 'ok' : 'bad'}`}>
            <StatusPill status={report.intact ? 'PASS' : 'FAIL'} /> version {report.version} — {report.detail}
            <div className="muted small">
              expected {report.expectedSha256.slice(0, 16)}… / actual {(report.actualSha256 ?? '—').slice(0, 16)}…
            </div>
          </div>
        ) : (
          <p className="muted">Recomputes the hash from object storage and compares it to the stored value.</p>
        )}
      </Section>

      <Section title="Versions">
        {versions.loading ? <Loading what="versions" /> : null}
        {versions.error ? <ErrorNote message={versions.error} /> : null}
        {versions.data ? (
          <DataTable
            rows={versions.data}
            rowKey={(v) => String(v.version)}
            columns={[
              { header: 'Ver', cell: (v) => v.version },
              { header: 'SHA-256', cell: (v) => <code>{v.sha256.slice(0, 20)}…</code> },
              { header: 'Type', cell: (v) => v.contentType },
              { header: 'Size', cell: (v) => `${v.sizeBytes} B` },
              { header: 'Collected', cell: (v) => new Date(v.collectedAt).toLocaleString() },
              { header: '', cell: (v) => <button onClick={() => runVerify(v.version)}>Verify</button> },
            ]}
          />
        ) : null}
      </Section>

      <EvidenceSummaryPanel evidenceId={ev.evidenceId} />

      <Section title="Latest version metadata">
        <JsonBlock value={ev.latest?.metadata ?? {}} />
      </Section>
    </div>
  )
}
