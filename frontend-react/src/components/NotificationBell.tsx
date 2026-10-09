import { useState } from 'react'
import { Link } from 'react-router-dom'
import { listNotifications, markAllNotificationsRead, markNotificationRead } from '../api/endpoints'
import { useAsync } from '../hooks/useAsync'
import { Empty, ErrorNote, Modal, StatusPill } from './ui'

const POLL_MS = 15_000
const CHANNEL_LABEL: Record<string, string> = { IN_APP: 'In-app', EMAIL_SIMULATED: 'Email (simulated)' }
const SEVERITY_TONE: Record<string, 'ok' | 'bad' | 'warn'> = { INFO: 'ok', WARNING: 'warn', ERROR: 'bad' }

/** Bell with an unread badge; polls so new run notifications appear without a page reload. */
export function NotificationBell() {
  const list = useAsync(() => listNotifications(), [], { pollWhile: () => true, pollMs: POLL_MS })
  const [open, setOpen] = useState(false)
  const [error, setError] = useState<string>()
  const unread = list.data?.unreadCount ?? 0

  async function act(fn: () => Promise<unknown>) {
    setError(undefined)
    try {
      await fn()
      list.reload()
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e))
    }
  }

  return (
    <>
      <button
        type="button"
        className="ghost notif-bell"
        aria-label={`Notifications, ${unread} unread`}
        onClick={() => setOpen(true)}
      >
        🔔{unread > 0 ? <span className="notif-badge">{unread}</span> : null}
      </button>
      {open ? (
        <Modal title="Notifications" onClose={() => setOpen(false)} size="lg">
          <div className="notif-actions">
            <button type="button" disabled={unread === 0} onClick={() => act(() => markAllNotificationsRead())}>
              Mark all read
            </button>
          </div>
          {error ? <ErrorNote message={error} /> : null}
          {list.error ? <ErrorNote message={list.error} /> : null}
          {list.data && list.data.items.length === 0 ? <Empty message="No notifications." /> : null}
          <ul className="notif-list">
            {(list.data?.items ?? []).map((n) => (
              <li key={n.id} className={n.readAt ? 'notif-item' : 'notif-item notif-unread'}>
                <div>
                  <StatusPill status={n.severity} tone={SEVERITY_TONE[n.severity] ?? 'warn'} />{' '}
                  <strong>{n.title}</strong>
                </div>
                <div className="muted">{n.message}</div>
                <div className="muted">
                  {new Date(n.createdAt).toLocaleString()} · {CHANNEL_LABEL[n.channel] ?? n.channel}
                  {n.runId ? (
                    <>
                      {' · '}
                      <Link to={`/scheduler?run=${n.runId}`} onClick={() => setOpen(false)}>
                        Open run
                      </Link>
                    </>
                  ) : null}
                  {!n.readAt ? (
                    <>
                      {' · '}
                      <button type="button" className="ghost" onClick={() => act(() => markNotificationRead(n.id))}>
                        Mark read
                      </button>
                    </>
                  ) : null}
                </div>
              </li>
            ))}
          </ul>
        </Modal>
      ) : null}
    </>
  )
}
