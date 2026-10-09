import { useEffect, useState } from 'react'
import { ApiError } from '../api/client'
import { getRetentionPolicy, listRetentionRuns, runArchival, saveRetentionPolicy } from '../api/endpoints'
import type { ArchiveResult } from '../api/types'
import { useAsync } from '../hooks/useAsync'
import { currentPersona } from '../pages/PersonaLogin'
import { DataTable, Empty, ErrorNote, Loading, Section } from './ui'

const fmt = (iso?: string | null) => (iso ? new Date(iso).toLocaleString() : '—')

/**
 * Retention policy & archival. Everyone sees the policy and the run history; only Admin edits the policy and
 * archives. Archiving moves evidence to the ARCHIVED state - nothing is deleted and objects/hashes are untouched.
 */
export function RetentionPanel({ onArchived }: { onArchived?: () => void }) {
  const isAdmin = currentPersona()?.code === 'ADMIN'
  const policy = useAsync(() => getRetentionPolicy(), [])
  const runs = useAsync(() => listRetentionRuns(), [])
  const [days, setDays] = useState('')
  const [application, setApplication] = useState('')
  const [result, setResult] = useState<ArchiveResult>()
  const [confirming, setConfirming] = useState(false)
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState<string>()
  const [error, setError] = useState<string>()

  useEffect(() => {
    if (policy.data) setDays(String(policy.data.retentionDays))
  }, [policy.data])

  async function guard(fn: () => Promise<void>) {
    setBusy(true)
    setError(undefined)
    setMessage(undefined)
    try {
      await fn()
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e))
      if (e instanceof ApiError && e.status === 409) policy.reload() // someone else changed the policy first
    } finally {
      setBusy(false)
    }
  }

  const save = () =>
    guard(async () => {
      const n = Number(days)
      if (!Number.isInteger(n) || n < 0 || n > 3650) {
        throw new Error('Retention days must be a whole number between 0 and 3650')
      }
      await saveRetentionPolicy(n, policy.data?.version)
      setMessage(`Retention policy saved: ${n} days.`)
      setResult(undefined)
      policy.reload()
    })

  const preview = () =>
    guard(async () => {
      setConfirming(false)
      setResult(await runArchival('PREVIEW', application.trim() || undefined))
      runs.reload()
    })

  const execute = () =>
    guard(async () => {
      const done = await runArchival('EXECUTE', application.trim() || undefined)
      setConfirming(false)
      setResult(done)
      setMessage(`Archived ${done.archived} evidence record${done.archived === 1 ? '' : 's'}. Nothing was deleted.`)
      runs.reload()
      onArchived?.()
    })

  return (
    <Section title="Retention policy & archival">
      {policy.loading ? <Loading what="retention policy" /> : null}
      {policy.error ? <ErrorNote message={policy.error} /> : null}
      {error ? <ErrorNote message={error} /> : null}
      {message ? <p role="status">{message}</p> : null}

      <div className="filter-row">
        <label>
          Retention (days)
          <input
            aria-label="Retention days"
            type="number"
            min={0}
            max={3650}
            value={days}
            disabled={!isAdmin}
            onChange={(e) => setDays(e.target.value)}
          />
        </label>
        {isAdmin ? (
          <button type="button" className="primary" disabled={busy} onClick={save}>
            Save
          </button>
        ) : (
          <span className="muted">Read-only: only Admin changes the policy.</span>
        )}
        <label>
          Scope (application, optional)
          <input
            aria-label="Archival scope"
            value={application}
            placeholder="all applications"
            onChange={(e) => setApplication(e.target.value)}
          />
        </label>
        <button type="button" disabled={busy} onClick={preview}>
          Preview eligible
        </button>
        {isAdmin ? (
          <button type="button" disabled={busy || (result?.mode === 'PREVIEW' && result.eligible === 0)} onClick={() => setConfirming(true)}>
            Archive eligible evidence
          </button>
        ) : null}
      </div>
      <p className="muted small">
        Evidence is eligible once collected + retention days has passed. Archiving only changes its lifecycle state and
        adds an audit event; the stored object, SHA-256 and versions are never touched.
        {policy.data?.updatedBy ? ` Policy last set by ${policy.data.updatedBy} on ${fmt(policy.data.updatedAt)}.` : ''}
      </p>

      {confirming ? (
        <div className="banner banner-warn" role="alertdialog" aria-label="Confirm archival">
          <p>
            This archives {result?.mode === 'PREVIEW' ? result.eligible : 'all eligible'} evidence records. Nothing is
            deleted.
          </p>
          <button type="button" className="primary" disabled={busy} onClick={execute}>
            Confirm archive
          </button>{' '}
          <button type="button" disabled={busy} onClick={() => setConfirming(false)}>
            Cancel
          </button>
        </div>
      ) : null}

      {result ? (
        <>
          <h3>
            {result.mode === 'PREVIEW' ? 'Eligible for archival' : 'Archived'} — {result.mode === 'PREVIEW' ? result.eligible : result.archived}
          </h3>
          <p className="muted small">
            Retention {result.retentionDays}d{result.application ? ` · scope ${result.application}` : ' · all applications'} ·{' '}
            scanned {result.scanned} · skipped {result.skipped}
          </p>
          {result.items.length === 0 ? (
            <Empty message="Nothing is eligible: no evidence has passed the retention window." />
          ) : (
            <DataTable
              rows={result.items}
              rowKey={(i) => i.evidenceId}
              columns={[
                { header: 'Application', cell: (i) => i.applicationSlug },
                { header: 'Control', cell: (i) => i.controlId },
                { header: 'Framework', cell: (i) => i.framework },
                { header: 'Collected', cell: (i) => fmt(i.collectedAt) },
                { header: 'Expired', cell: (i) => fmt(i.expiresAt) },
              ]}
            />
          )}
        </>
      ) : null}

      <h3>Retention runs</h3>
      {runs.data && runs.data.length === 0 ? <Empty message="No retention runs yet." /> : null}
      {runs.data && runs.data.length > 0 ? (
        <DataTable
          rows={runs.data}
          rowKey={(r) => r.id}
          columns={[
            { header: 'Time', cell: (r) => fmt(r.startedAt) },
            { header: 'Mode', cell: (r) => r.mode },
            { header: 'By', cell: (r) => r.triggeredBy ?? '—' },
            { header: 'Retention', cell: (r) => `${r.retentionDays}d` },
            { header: 'Scope', cell: (r) => r.application ?? 'all' },
            { header: 'Scanned', cell: (r) => r.scanned },
            { header: 'Archived', cell: (r) => r.archived },
            { header: 'Skipped', cell: (r) => r.skipped },
          ]}
        />
      ) : null}
    </Section>
  )
}
