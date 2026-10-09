import { useState } from 'react'
import { closeObservation, createObservation, listEvidenceObservations } from '../api/endpoints'
import type { ObservationView } from '../api/types'
import { useAsync } from '../hooks/useAsync'
import { OBSERVATION_WRITE_ROLES, currentPersona } from '../pages/PersonaLogin'
import { Empty, ErrorNote, Loading, StatusPill } from './ui'

/** One observation, rendered as plain text with its control / framework next to it. */
export function ObservationItem({ o, onClose }: { o: ObservationView; onClose?: (o: ObservationView) => void }) {
  return (
    <li className="notif-item">
      <div>
        <StatusPill status={o.status} tone={o.status === 'OPEN' ? 'warn' : 'muted'} /> <strong>{o.controlId}</strong>{' '}
        <span className="muted">({o.framework})</span>
      </div>
      <div>{o.text}</div>
      <div className="muted">
        Raised by {o.raisedByRole}
        {o.raisedBy ? ` / ${o.raisedBy}` : ''} · {new Date(o.createdAt).toLocaleString()}
        {o.closedAt ? ` · closed ${new Date(o.closedAt).toLocaleString()}` : ''}
        {onClose && o.status === 'OPEN' ? (
          <>
            {' · '}
            <button type="button" className="ghost" onClick={() => onClose(o)}>
              Close
            </button>
          </>
        ) : null}
      </div>
    </li>
  )
}

/** Audit observations for one evidence record. Auditor / Admin can add and close; everyone else reads. */
export function ObservationsPanel({ evidenceId }: { evidenceId: string }) {
  const list = useAsync(() => listEvidenceObservations(evidenceId), [evidenceId])
  const persona = currentPersona()
  const canWrite = !!persona && OBSERVATION_WRITE_ROLES.includes(persona.code)
  const [text, setText] = useState('')
  const [error, setError] = useState<string>()
  const [busy, setBusy] = useState(false)

  async function add(e: React.FormEvent) {
    e.preventDefault()
    setError(undefined)
    if (!text.trim()) {
      setError('Observation text is required')
      return
    }
    setBusy(true)
    try {
      await createObservation(evidenceId, text.trim())
      setText('')
      list.reload()
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err))
    } finally {
      setBusy(false)
    }
  }

  async function close(o: ObservationView) {
    setError(undefined)
    try {
      await closeObservation(o.id)
      list.reload()
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err))
    }
  }

  return (
    <>
      {list.loading ? <Loading what="observations" /> : null}
      {list.error ? <ErrorNote message={list.error} /> : null}
      {error ? <ErrorNote message={error} /> : null}
      {list.data && list.data.length === 0 ? <Empty message="No audit observations." /> : null}
      <ul className="notif-list">
        {(list.data ?? []).map((o) => (
          <ObservationItem key={o.id} o={o} onClose={canWrite ? close : undefined} />
        ))}
      </ul>
      {canWrite ? (
        <form onSubmit={add} noValidate>
          <label>
            Observation
            <textarea
              rows={3}
              maxLength={1000}
              value={text}
              onChange={(e) => setText(e.target.value)}
              aria-label="Observation text"
            />
          </label>
          <p>
            <button className="primary" type="submit" disabled={busy}>
              Add observation
            </button>
          </p>
        </form>
      ) : (
        <p className="muted">Only Auditor and Admin can add or close observations.</p>
      )}
    </>
  )
}
