import type { LifecycleEventView } from '../api/types'
import { DataTable, Empty } from './ui'
import { describeChanges, parseMetadataEdit } from './metadataAudit'

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
        { header: 'Note', cell: (h) => noteOf(h) },
      ]}
    />
  )
}

/** METADATA_EDITED notes are JSON: show old → new and the reason instead of the raw payload. */
function noteOf(h: LifecycleEventView): string {
  const edit = parseMetadataEdit(h)
  return edit ? `${describeChanges(edit)} — reason: ${edit.reason}` : (h.note ?? '')
}
