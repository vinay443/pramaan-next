import type { LifecycleEventView } from '../api/types'
import { DataTable, Empty } from './ui'

export function LifecycleHistoryTable({ history }: { history: LifecycleEventView[] }) {
  if (history.length === 0) return <Empty message="No execution history recorded." />
  return (
    <DataTable
      rows={history}
      rowKey={(h) => `${h.occurredAt}-${h.action}`}
      columns={[
        { header: 'When', cell: (h) => new Date(h.occurredAt).toLocaleString() },
        { header: 'Action', cell: (h) => h.action },
        { header: 'From→To', cell: (h) => `${h.fromState ?? '—'} → ${h.toState}` },
        { header: 'Actor', cell: (h) => h.actor ?? '—' },
        { header: 'Note', cell: (h) => h.note ?? '' },
      ]}
    />
  )
}
