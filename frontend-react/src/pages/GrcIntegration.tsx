import { useState } from 'react'
import { getGrcStatus, triggerGrcSync } from '../api/endpoints'
import { useAsync } from '../hooks/useAsync'
import { ErrorNote, Loading, Section, StatCard, StatusPill } from '../components/ui'

/** Outbound GRC sync — pushes an evidence + control-status summary to the
 *  external GRC system (mock in Phase 1; see backend GrcSyncService). */
export function GrcIntegration() {
  const status = useAsync(() => getGrcStatus(), [])
  const [syncing, setSyncing] = useState(false)
  const [error, setError] = useState<string>()

  async function syncNow() {
    setSyncing(true)
    setError(undefined)
    try {
      await triggerGrcSync()
      status.reload()
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e))
    } finally {
      setSyncing(false)
    }
  }

  const s = status.data

  return (
    <div className="page">
      <h1>GRC Integration</h1>
      <p className="muted">Pushes an evidence + control-status summary to the external GRC system.</p>

      <Section
        title="Sync status"
        actions={
          <button className="primary" disabled={syncing} onClick={syncNow}>
            {syncing ? 'Syncing…' : 'Sync now'}
          </button>
        }
      >
        {status.loading ? <Loading what="sync status" /> : null}
        {status.error ? <ErrorNote message={status.error} /> : null}
        {error ? <ErrorNote message={error} /> : null}
        {s ? (
          <>
            <p>
              {s.everSynced ? (
                <StatusPill status={s.lastOutcome ?? 'UNKNOWN'} />
              ) : (
                <StatusPill status="NEVER SYNCED" />
              )}
              {s.mock ? <span className="muted small"> mock endpoint</span> : null}
              <span className="muted small">
                {' '}
                {s.endpoint}
                {s.lastSyncedAt ? ` · last synced ${new Date(s.lastSyncedAt).toLocaleString()}` : ''}
              </span>
            </p>
            <div className="stat-grid">
              <StatCard label="Evidence records" value={s.evidenceRecords} />
              <StatCard label="Control results" value={s.controlsEvaluated} />
              <StatCard label="Compliance" value={`${s.compliancePct}%`} />
              <StatCard label="Reference" value={s.externalReference ?? '—'} />
            </div>
            <p className="muted">{s.detail}</p>
          </>
        ) : null}
      </Section>
    </div>
  )
}
