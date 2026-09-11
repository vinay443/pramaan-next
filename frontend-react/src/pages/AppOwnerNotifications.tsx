import { useEffect, useState } from 'react'
import { listAppOwnerNotifications } from '../api/appOwnerEndpoints'
import { ApiError } from '../api/client'
import type { NotificationView } from '../api/appOwnerTypes'

const TYPE_LABEL: Record<string, string> = {
  EVIDENCE_REJECTED: 'Evidence rejected',
  EVIDENCE_APPROVED: 'Evidence approved',
  TD_REQUIRED: 'Target date required',
  TD_APPROACHING: 'Target date approaching',
  ITEM_OVERDUE: 'Item overdue',
}

/** View-only — nothing on this page writes back (no "mark read", no dismiss). */
export function AppOwnerNotifications() {
  const [items, setItems] = useState<NotificationView[] | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    listAppOwnerNotifications()
      .then(setItems)
      .catch((e) => setError(e instanceof ApiError ? e.message : 'Failed to load notifications'))
  }, [])

  return (
    <div className="page">
      <h1>Notifications</h1>
      <p className="muted">View-only.</p>
      {error ? <div className="state state-error">{error}</div> : null}
      {!error && !items ? <div className="state"><span className="state-spinner" />Loading…</div> : null}
      {items ? (
        <div className="table-wrap">
          <table>
            <thead><tr><th>Type</th><th>Message</th><th>Application</th><th>When</th></tr></thead>
            <tbody>
              {items.map((n, i) => (
                <tr key={n.id ?? i}>
                  <td><span className="pill pill-muted">{TYPE_LABEL[n.type] ?? n.type}</span></td>
                  <td>{n.message}</td>
                  <td>{n.applicationSlug ?? '—'}</td>
                  <td>{new Date(n.createdAt).toLocaleString()}</td>
                </tr>
              ))}
              {items.length === 0 ? <tr><td colSpan={4} className="muted">No notifications.</td></tr> : null}
            </tbody>
          </table>
        </div>
      ) : null}
    </div>
  )
}
