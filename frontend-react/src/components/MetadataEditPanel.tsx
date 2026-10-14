import { useState } from 'react'
import { getEvidenceLifecycle, patchEvidenceMetadata } from '../api/endpoints'
import type { EvidenceView } from '../api/types'
import { useAsync } from '../hooks/useAsync'
import { METADATA_EDIT_ROLES, currentPersona } from '../pages/PersonaLogin'
import { describeChanges, parseMetadataEdit } from './metadataAudit'
import type { MetadataEdit } from './metadataAudit'

const ENVIRONMENTS = ['PROD', 'UAT', 'DR', 'DEV']

interface Form {
  title: string
  environment: string
  technology: string
  evidenceType: string
  frameworks: string
  reason: string
}

function initialForm(row: EvidenceView): Form {
  return {
    title: row.tags.sourceTitle ?? '',
    environment: row.tags.environment ?? '',
    technology: row.tags.technology ?? '',
    evidenceType: row.tags.evidenceType ?? '',
    frameworks: row.tags.frameworks ?? row.framework,
    reason: '',
  }
}

/**
 * FT006 - edit an evidence record's metadata with a mandatory reason (APP and ADMIN only) and the read-only
 * "Metadata history" from GET /evidence/{id}/lifecycle (METADATA_EDITED events: old to new, user, time, reason).
 */
export function MetadataEditPanel({ row, onSaved }: { row: EvidenceView; onSaved: () => void }) {
  const persona = currentPersona()
  const canEdit = !!persona && METADATA_EDIT_ROLES.includes(persona.code)
  const [editing, setEditing] = useState(false)
  const [form, setForm] = useState<Form>(() => initialForm(row))
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string>()
  const [notice, setNotice] = useState<string>()
  const history = useAsync(() => getEvidenceLifecycle(row.evidenceId), [row.evidenceId, row.updatedAt])
  const edits = (history.data?.history ?? []).map(parseMetadataEdit).filter((e): e is MetadataEdit => e !== undefined)

  function set<K extends keyof Form>(key: K, value: Form[K]) {
    setForm((f) => ({ ...f, [key]: value }))
  }

  async function save() {
    const reason = form.reason.trim()
    if (!reason) {
      setError('A reason is required to edit metadata.')
      return
    }
    const before = initialForm(row)
    const changes: Record<string, unknown> = {}
    if (form.title.trim() !== before.title) changes.title = form.title.trim()
    if (form.environment !== before.environment) changes.environment = form.environment
    if (form.technology.trim() !== before.technology) changes.technology = form.technology.trim()
    if (form.evidenceType.trim() !== before.evidenceType) changes.evidenceType = form.evidenceType.trim()
    if (form.frameworks.trim() !== before.frameworks) {
      changes.frameworks = form.frameworks
        .split(',')
        .map((f) => f.trim())
        .filter(Boolean)
    }
    if (Object.keys(changes).length === 0) {
      setError('Nothing changed.')
      return
    }
    setBusy(true)
    setError(undefined)
    try {
      const updated = await patchEvidenceMetadata(row.evidenceId, changes, reason)
      setNotice(`Saved. Lifecycle state: ${updated.lifecycleState}.`)
      setEditing(false)
      history.reload()
      onSaved()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not save the metadata.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="metadata-edit">
      {canEdit && !editing ? (
        <button
          type="button"
          onClick={() => {
            setForm(initialForm(row))
            setNotice(undefined)
            setError(undefined)
            setEditing(true)
          }}
        >
          Edit metadata
        </button>
      ) : null}
      {notice ? (
        <p className="muted small" role="status">
          {notice}
        </p>
      ) : null}

      {editing ? (
        <form
          className="filter-row"
          onSubmit={(e) => {
            e.preventDefault()
            void save()
          }}
        >
          <label>
            Title
            <input aria-label="Edit title" value={form.title} onChange={(e) => set('title', e.target.value)} />
          </label>
          <label>
            Environment
            <select aria-label="Edit environment" value={form.environment} onChange={(e) => set('environment', e.target.value)}>
              <option value="">Not specified</option>
              {ENVIRONMENTS.map((env) => (
                <option key={env} value={env}>
                  {env}
                </option>
              ))}
            </select>
          </label>
          <label>
            Technology
            <input aria-label="Edit technology" value={form.technology} onChange={(e) => set('technology', e.target.value)} />
          </label>
          <label>
            Evidence type
            <input aria-label="Edit evidence type" value={form.evidenceType} onChange={(e) => set('evidenceType', e.target.value)} />
          </label>
          <label>
            Frameworks (comma separated)
            <input aria-label="Edit frameworks" value={form.frameworks} onChange={(e) => set('frameworks', e.target.value)} />
          </label>
          <label>
            Reason for the change *
            <textarea
              aria-label="Reason for the change"
              aria-required="true"
              rows={2}
              value={form.reason}
              onChange={(e) => set('reason', e.target.value)}
            />
          </label>
          <div>
            <button type="submit" className="primary" disabled={busy}>
              {busy ? 'Saving…' : 'Save changes'}
            </button>{' '}
            <button type="button" disabled={busy} onClick={() => setEditing(false)}>
              Cancel
            </button>
            <p className="muted small">Approved or submitted evidence returns to Draft after an edit.</p>
          </div>
        </form>
      ) : null}
      {error ? (
        <div className="state state-error" role="alert">
          <span>{error}</span>
        </div>
      ) : null}

      <h4>Metadata history</h4>
      {edits.length === 0 ? (
        <p className="muted small">No metadata edits.</p>
      ) : (
        <ul className="metadata-history">
          {edits.map((e) => (
            <li key={`${e.when}-${e.actor}`}>
              <strong>{describeChanges(e)}</strong>
              <div className="muted small">
                {e.actor}
                {e.role ? ` (${e.role})` : ''} · {new Date(e.when).toLocaleString()} · reason: {e.reason}
              </div>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
