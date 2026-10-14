import type { LifecycleEventView } from '../api/types'

export interface MetadataFieldChange {
  field: string
  old: string | null
  new: string | null
}

export interface MetadataEdit {
  when: string
  actor: string
  role?: string
  reason: string
  changes: MetadataFieldChange[]
}

/** Parses the JSON note the backend writes on a METADATA_EDITED lifecycle event; undefined when it is not one. */
export function parseMetadataEdit(e: LifecycleEventView): MetadataEdit | undefined {
  if (e.action !== 'METADATA_EDITED' || !e.note) return undefined
  try {
    const n = JSON.parse(e.note) as {
      reason?: string
      role?: string
      changes?: Record<string, { old?: string | null; new?: string | null }>
    }
    return {
      when: e.occurredAt,
      actor: e.actor ?? '—',
      role: n.role,
      reason: n.reason ?? '',
      changes: Object.entries(n.changes ?? {}).map(([field, v]) => ({ field, old: v.old ?? null, new: v.new ?? null })),
    }
  } catch {
    return undefined
  }
}

export function describeChanges(edit: MetadataEdit): string {
  return edit.changes.map((c) => `${c.field}: ${c.old ?? '—'} → ${c.new ?? '—'}`).join('; ')
}
