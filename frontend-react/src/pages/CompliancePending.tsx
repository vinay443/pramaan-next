import { useCallback, useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import {
  getAppOwnerEvidence,
  getAppOwnerTargetDateHistory,
  listCompliancePending,
  resubmitAppOwnerEvidence,
  shareAppOwnerTargetDate,
  uploadAppOwnerEvidence,
} from '../api/appOwnerEndpoints'
import { ApiError } from '../api/client'
import type { CompliancePendingRow, TargetDateHistoryView } from '../api/appOwnerTypes'
import type { EvidenceView } from '../api/types'

const SLA_LABEL: Record<string, string> = {
  NONE: 'No target date', ON_TRACK: 'On track', DUE_SOON: 'Due soon', OVERDUE: 'Overdue',
}
const SLA_PILL: Record<string, string> = {
  NONE: 'pill-muted', ON_TRACK: 'pill-ok', DUE_SOON: 'pill-warn', OVERDUE: 'pill-bad',
}

type ModalMode = 'upload' | 'resubmit' | 'td' | 'view' | null

export function CompliancePending() {
  const [params, setParams] = useSearchParams()
  const [rows, setRows] = useState<CompliancePendingRow[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [modal, setModal] = useState<ModalMode>(null)
  const [activeRow, setActiveRow] = useState<CompliancePendingRow | null>(null)
  const [busy, setBusy] = useState(false)
  const [actionError, setActionError] = useState<string | null>(null)

  const complianceStatus = params.get('complianceStatus') ?? ''
  const evidenceStatus = params.get('evidenceStatus') ?? ''
  const slaStatus = params.get('slaStatus') ?? ''
  const sortBy = params.get('sortBy') ?? 'lastUpdated'

  const load = useCallback(() => {
    setRows(null)
    listCompliancePending({ complianceStatus, evidenceStatus, slaStatus, sortBy })
      .then(setRows)
      .catch((e) => setError(e instanceof ApiError ? e.message : 'Failed to load compliance-pending list'))
  }, [complianceStatus, evidenceStatus, slaStatus, sortBy])

  useEffect(() => { load() }, [load])

  // Deep-link from the dashboard: open an action modal directly.
  useEffect(() => {
    if (!rows) return
    const focusId = params.get('focus')
    const action = params.get('action')
    const upload = params.get('upload')
    if (upload) {
      setActiveRow({
        evidenceId: null, framework: params.get('framework') ?? '', controlId: params.get('controlId') ?? '',
        applicationSlug: params.get('applicationSlug') ?? '', complianceStatus: 'MISSING_EVIDENCE',
        evidenceStatus: null, targetDate: null, slaStatus: 'NONE', auditorComment: null, lastUpdated: null,
        allowedActions: ['UPLOAD_EVIDENCE'],
      })
      setModal('upload')
    } else if (focusId) {
      const row = rows.find((r) => r.evidenceId === focusId)
      if (row) {
        setActiveRow(row)
        setModal(action === 'REVIEW_AND_RESUBMIT' ? 'resubmit' : action === 'SHARE_UPDATE_TD' ? 'td' : 'view')
      }
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [rows])

  function setFilter(key: string, value: string) {
    const next = new URLSearchParams(params)
    if (value) next.set(key, value); else next.delete(key)
    setParams(next, { replace: true })
  }

  function clearFilters() {
    setParams({}, { replace: true })
  }

  function closeModal() {
    setModal(null)
    setActiveRow(null)
    setActionError(null)
    const next = new URLSearchParams(params)
    next.delete('focus'); next.delete('action'); next.delete('upload')
    next.delete('applicationSlug'); next.delete('controlId'); next.delete('framework')
    setParams(next, { replace: true })
  }

  function openAction(row: CompliancePendingRow, mode: ModalMode) {
    setActiveRow(row)
    setModal(mode)
    setActionError(null)
  }

  const hasActiveFilters = Boolean(complianceStatus || evidenceStatus || slaStatus)

  return (
    <div className="page">
      <h1>Compliance Pending</h1>
      <p className="muted">Items requiring App Owner action, across your authorized applications.</p>

      <div className="card">
        <div className="filter-row">
          <label>
            Compliance status
            <select value={complianceStatus} onChange={(e) => setFilter('complianceStatus', e.target.value)}>
              <option value="">All</option>
              <option value="NON_COMPLIANT">Non-compliant</option>
              <option value="PARTIALLY_COMPLIANT">Partially compliant</option>
              <option value="NOT_ASSESSED">Not assessed</option>
              <option value="MISSING_EVIDENCE">Missing evidence</option>
            </select>
          </label>
          <label>
            Evidence status
            <select value={evidenceStatus} onChange={(e) => setFilter('evidenceStatus', e.target.value)}>
              <option value="">All</option>
              <option value="DRAFT">Draft</option>
              <option value="SUBMITTED">Submitted / Under review</option>
              <option value="REJECTED">Rejected</option>
            </select>
          </label>
          <label>
            TD / SLA status
            <select value={slaStatus} onChange={(e) => setFilter('slaStatus', e.target.value)}>
              <option value="">All</option>
              <option value="NONE">No target date</option>
              <option value="ON_TRACK">On track</option>
              <option value="DUE_SOON">Due soon</option>
              <option value="OVERDUE">Overdue</option>
            </select>
          </label>
          <label>
            Sort by
            <select value={sortBy} onChange={(e) => setFilter('sortBy', e.target.value)}>
              <option value="lastUpdated">Last updated</option>
              <option value="targetDate">Target date</option>
              <option value="status">Status</option>
              <option value="priority">Priority (SLA)</option>
            </select>
          </label>
          <div className="row-actions">
            <button type="button" onClick={clearFilters} disabled={!hasActiveFilters}>Clear filters</button>
          </div>
        </div>
        {hasActiveFilters ? (
          <p className="muted small" style={{ marginTop: 10 }}>
            Active filters:
            {complianceStatus ? ` compliance=${complianceStatus}` : ''}
            {evidenceStatus ? ` evidence=${evidenceStatus}` : ''}
            {slaStatus ? ` sla=${slaStatus}` : ''}
          </p>
        ) : null}
      </div>

      {error ? <div className="state state-error">{error}</div> : null}
      {!error && !rows ? <div className="state"><span className="state-spinner" />Loading…</div> : null}

      {rows ? (
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Framework</th><th>Control</th><th>Application</th><th>Compliance</th><th>Evidence</th>
                <th>Target Date</th><th>SLA</th><th>Auditor Comment</th><th>Last Updated</th><th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((r, i) => (
                <tr key={`${r.evidenceId ?? 'none'}-${r.framework}-${r.controlId}-${i}`}>
                  <td>{r.framework}</td>
                  <td>{r.controlId}</td>
                  <td>{r.applicationSlug}</td>
                  <td><span className="pill pill-muted">{r.complianceStatus.split('_').join(' ')}</span></td>
                  <td>{r.evidenceStatus ? r.evidenceStatus.split('_').join(' ') : '—'}</td>
                  <td>{r.targetDate ?? '—'}</td>
                  <td><span className={`pill ${SLA_PILL[r.slaStatus]}`}>{SLA_LABEL[r.slaStatus]}</span></td>
                  <td className="small">{r.auditorComment ?? '—'}</td>
                  <td>{r.lastUpdated ? new Date(r.lastUpdated).toLocaleString() : '—'}</td>
                  <td>
                    <div className="row-actions" style={{ marginTop: 0 }}>
                      {r.allowedActions.includes('UPLOAD_EVIDENCE') ? (
                        <button type="button" className="ghost" onClick={() => openAction(r, 'upload')}>Upload Evidence</button>
                      ) : null}
                      {r.allowedActions.includes('REVIEW_AND_RESUBMIT') ? (
                        <button type="button" className="ghost" onClick={() => openAction(r, 'resubmit')}>Review & Resubmit</button>
                      ) : null}
                      {r.allowedActions.includes('SHARE_UPDATE_TD') ? (
                        <button type="button" className="ghost" onClick={() => openAction(r, 'td')}>Share/Update TD</button>
                      ) : null}
                      {r.evidenceId ? (
                        <button type="button" className="ghost" onClick={() => openAction(r, 'view')}>Open</button>
                      ) : null}
                    </div>
                  </td>
                </tr>
              ))}
              {rows.length === 0 ? <tr><td colSpan={10} className="muted">Nothing pending.</td></tr> : null}
            </tbody>
          </table>
        </div>
      ) : null}

      {modal && activeRow ? (
        <ActionModal
          mode={modal} row={activeRow} busy={busy} error={actionError}
          onClose={closeModal}
          onBusy={setBusy}
          onError={setActionError}
          onDone={() => { closeModal(); load() }}
        />
      ) : null}
    </div>
  )
}

function ActionModal({ mode, row, busy, error, onClose, onBusy, onError, onDone }: {
  mode: Exclude<ModalMode, null>
  row: CompliancePendingRow
  busy: boolean
  error: string | null
  onClose: () => void
  onBusy: (v: boolean) => void
  onError: (v: string | null) => void
  onDone: () => void
}) {
  const title = mode === 'upload' ? 'Upload Evidence'
    : mode === 'resubmit' ? 'Review & Resubmit'
    : mode === 'td' ? 'Share / Update Target Date'
    : 'Item Detail'

  return (
    <div className="modal-overlay" role="dialog" aria-modal="true" aria-label={title}>
      <div className="modal">
        <div className="modal-head">
          <h2>{title}</h2>
          <button type="button" className="ghost" onClick={onClose} aria-label="Close">×</button>
        </div>
        <p className="muted small">
          {row.framework} / {row.controlId} — {row.applicationSlug}
        </p>
        {error ? <div className="state state-error" style={{ padding: '8px 0' }}>{error}</div> : null}

        {mode === 'upload' ? (
          <UploadForm row={row} busy={busy} onBusy={onBusy} onError={onError} onDone={onDone} />
        ) : mode === 'resubmit' ? (
          <ResubmitForm row={row} busy={busy} onBusy={onBusy} onError={onError} onDone={onDone} />
        ) : mode === 'td' ? (
          <TargetDateForm row={row} busy={busy} onBusy={onBusy} onError={onError} onDone={onDone} />
        ) : (
          <ViewDetail row={row} />
        )}

        <div className="modal-foot">
          <button type="button" onClick={onClose}>Close</button>
        </div>
      </div>
    </div>
  )
}

function UploadForm({ row, busy, onBusy, onError, onDone }: {
  row: CompliancePendingRow; busy: boolean
  onBusy: (v: boolean) => void; onError: (v: string | null) => void; onDone: () => void
}) {
  const [description, setDescription] = useState('')
  const [file, setFile] = useState<File | null>(null)

  async function submit() {
    if (!file) { onError('Attach a file.'); return }
    onError(null); onBusy(true)
    try {
      const contentBase64 = await fileToBase64(file)
      await uploadAppOwnerEvidence({
        applicationSlug: row.applicationSlug, controlId: row.controlId, framework: row.framework,
        title: file.name, contentType: file.type || 'application/octet-stream', contentBase64, description,
      })
      onDone()
    } catch (e) {
      onError(e instanceof ApiError ? e.message : 'Upload failed')
    } finally {
      onBusy(false)
    }
  }

  return (
    <div>
      <label>
        File
        <input type="file" onChange={(e) => setFile(e.target.files?.[0] ?? null)} />
      </label>
      <label style={{ marginTop: 10 }}>
        Description / remarks
        <textarea rows={3} value={description} onChange={(e) => setDescription(e.target.value)} />
      </label>
      <div className="modal-foot" style={{ marginTop: 14 }}>
        <button type="button" className="primary" disabled={busy} onClick={submit}>
          {busy ? 'Uploading…' : 'Upload'}
        </button>
      </div>
    </div>
  )
}

function ResubmitForm({ row, busy, onBusy, onError, onDone }: {
  row: CompliancePendingRow; busy: boolean
  onBusy: (v: boolean) => void; onError: (v: string | null) => void; onDone: () => void
}) {
  const [comment, setComment] = useState('')

  async function submit() {
    if (!row.evidenceId) return
    onError(null); onBusy(true)
    try {
      await resubmitAppOwnerEvidence(row.evidenceId, comment)
      onDone()
    } catch (e) {
      onError(e instanceof ApiError ? e.message : 'Resubmit failed')
    } finally {
      onBusy(false)
    }
  }

  return (
    <div>
      <div className="verify bad" style={{ marginBottom: 12 }}>
        <strong>Rejected</strong>
        {row.auditorComment ? <p style={{ margin: '6px 0 0' }}>Auditor's reason: {row.auditorComment}</p> : null}
      </div>
      <label>
        Response comment
        <textarea rows={3} value={comment} onChange={(e) => setComment(e.target.value)}
          placeholder="What changed since the last submission?" />
      </label>
      <div className="modal-foot" style={{ marginTop: 14 }}>
        <button type="button" className="primary" disabled={busy} onClick={submit}>
          {busy ? 'Resubmitting…' : 'Resubmit Evidence'}
        </button>
      </div>
    </div>
  )
}

function TargetDateForm({ row, busy, onBusy, onError, onDone }: {
  row: CompliancePendingRow; busy: boolean
  onBusy: (v: boolean) => void; onError: (v: string | null) => void; onDone: () => void
}) {
  const min = new Date(Date.now() + 24 * 3600 * 1000).toISOString().slice(0, 10)
  const max = new Date(Date.now() + 365 * 24 * 3600 * 1000).toISOString().slice(0, 10)
  const [date, setDate] = useState(row.targetDate ?? '')
  const [comment, setComment] = useState('')
  const [history, setHistory] = useState<TargetDateHistoryView[] | null>(null)

  useEffect(() => {
    if (row.evidenceId) {
      getAppOwnerTargetDateHistory(row.evidenceId).then(setHistory).catch(() => setHistory([]))
    }
  }, [row.evidenceId])

  async function submit() {
    if (!row.evidenceId) { onError('Upload evidence first before setting a target date.'); return }
    if (!date) { onError('Pick a target date.'); return }
    onError(null); onBusy(true)
    try {
      await shareAppOwnerTargetDate(row.evidenceId, date, comment)
      onDone()
    } catch (e) {
      onError(e instanceof ApiError ? e.message : 'Could not save target date')
    } finally {
      onBusy(false)
    }
  }

  return (
    <div>
      <p className="muted small">Current: {row.targetDate ?? 'not set'}</p>
      <label>
        Target date
        <input type="date" min={min} max={max} value={date} onChange={(e) => setDate(e.target.value)} />
      </label>
      <label style={{ marginTop: 10 }}>
        Comment {row.targetDate ? '(optional)' : '(required — first time sharing a target date)'}
        <textarea rows={3} value={comment} onChange={(e) => setComment(e.target.value)} />
      </label>
      {history && history.length > 0 ? (
        <div className="table-wrap" style={{ marginTop: 12 }}>
          <table>
            <thead><tr><th>When</th><th>Old</th><th>New</th><th>By</th><th>Reason</th></tr></thead>
            <tbody>
              {history.map((h, i) => (
                <tr key={i}>
                  <td>{new Date(h.createdAt).toLocaleString()}</td>
                  <td>{h.oldTargetDate ?? '—'}</td>
                  <td>{h.newTargetDate}</td>
                  <td>{h.actorUsername}</td>
                  <td className="small">{h.reason ?? '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : null}
      <div className="modal-foot" style={{ marginTop: 14 }}>
        <button type="button" className="primary" disabled={busy} onClick={submit}>
          {busy ? 'Saving…' : row.targetDate ? 'Update Target Date' : 'Share Target Date'}
        </button>
      </div>
    </div>
  )
}

function ViewDetail({ row }: { row: CompliancePendingRow }) {
  const [detail, setDetail] = useState<EvidenceView | null>(null)
  useEffect(() => {
    if (row.evidenceId) getAppOwnerEvidence(row.evidenceId).then(setDetail).catch(() => setDetail(null))
  }, [row.evidenceId])
  if (!detail) return <div className="state"><span className="state-spinner" />Loading…</div>
  return (
    <dl className="kv">
      <dt>Evidence status</dt><dd>{detail.lifecycleState}</dd>
      <dt>Current version</dt><dd>{detail.currentVersion}</dd>
      <dt>Title</dt><dd>{detail.title ?? '—'}</dd>
      <dt>Last updated</dt><dd>{new Date(detail.updatedAt).toLocaleString()}</dd>
      <dt>Integrity</dt><dd>{detail.integrityStatus ?? 'UNKNOWN'}</dd>
    </dl>
  )
}

function fileToBase64(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => {
      const result = String(reader.result)
      resolve(result.substring(result.indexOf(',') + 1))
    }
    reader.onerror = reject
    reader.readAsDataURL(file)
  })
}
