import { Fragment, useEffect, useRef, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { BULK_UPLOAD_EXTENSIONS } from '../api/bulkUploadTypes'
import { ingestBulkUpload, listApplications, listBulkBatches, listReuseControls } from '../api/endpoints'
import type { BulkFileOutcome, BulkIngestResponse, RunView } from '../api/types'
import { DataTable, Empty, ErrorNote, Section, StatCard, StatusPill } from '../components/ui'
import { useAsync } from '../hooks/useAsync'
import { BULK_UPLOAD_ROLES, currentPersona } from './PersonaLogin'

function formatSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

function isZip(file: File): boolean {
  return file.name.toLowerCase().endsWith('.zip') || file.type === 'application/zip'
}

const ENVIRONMENTS = ['PROD', 'UAT', 'DR', 'DEV']
const BATCH_PAGE = 10

/** Options for a catalogue-backed select, keeping a prefilled value selectable even when the catalogue lacks it. */
function withCurrent(options: string[], current: string): string[] {
  return current && !options.includes(current) ? [current, ...options] : options
}

/** SHA-256 of the file as the browser sees it; the server rejects the file if the bytes it receives hash differently.
 *  Best effort: where the Web Crypto API (or Blob.arrayBuffer) is unavailable the check is simply skipped. */
async function sha256Hex(file: File): Promise<string | undefined> {
  try {
    if (typeof file.arrayBuffer !== 'function' || !globalThis.crypto?.subtle) return undefined
    const digest = await globalThis.crypto.subtle.digest('SHA-256', await file.arrayBuffer())
    return Array.from(new Uint8Array(digest))
      .map((b) => b.toString(16).padStart(2, '0'))
      .join('')
  } catch {
    return undefined
  }
}

interface SubmittedMeta {
  applicationSlug: string
  framework: string
  controlId: string
  technology?: string
  environment?: string
}

const OK_STATUSES = new Set(['CREATED', 'NEW_VERSION'])
const NOT_STORED = new Set(['REJECTED', 'FAILED'])

export function BulkUpload() {
  // Compliance-tab "Upload evidence" links prefill these via query params.
  const [searchParams] = useSearchParams()
  const persona = currentPersona()
  // No persona (tests / demo without login) is not blocked in the UI; the backend still enforces the role header.
  const canUpload = !persona || BULK_UPLOAD_ROLES.includes(persona.code)
  const [applicationSlug, setApp] = useState(searchParams.get('applicationSlug') ?? 'net-banking')
  const [framework, setFramework] = useState(searchParams.get('framework') ?? 'PCI_DSS')
  const [controlId, setControlId] = useState(searchParams.get('controlId') ?? '')
  const [technology, setTechnology] = useState('')
  const [environment, setEnvironment] = useState('')
  const [files, setFiles] = useState<File[]>([])
  // Bumped on every add/remove to force the file input below to remount. Removing a
  // file only updates this `files` state — the native <input> element is untouched,
  // and browsers can fail to fire `change` when the same file(s) are reselected in a
  // later dialog session on that same persisted element (regardless of the value=''
  // reset in its onChange). A fresh DOM node has no native memory of a prior
  // selection, so re-adding a removed file is never blocked at the browser level.
  const [inputKey, setInputKey] = useState(0)
  const [dragOver, setDragOver] = useState(false)
  const [result, setResult] = useState<BulkIngestResponse>()
  // What the displayed result was submitted with, so "Retry failed files" re-sends the same metadata + File objects.
  const [submitted, setSubmitted] = useState<{ files: File[]; meta: SubmittedMeta }>()
  const [error, setError] = useState<string>()
  const [attempted, setAttempted] = useState(false)
  const [busy, setBusy] = useState(false)
  const [openBatch, setOpenBatch] = useState<string>()
  const inputRef = useRef<HTMLInputElement>(null)
  const errorRef = useRef<HTMLDivElement>(null)
  const [batchLimit, setBatchLimit] = useState(BATCH_PAGE)
  const batches = useAsync(() => listBulkBatches(batchLimit), [batchLimit])
  // The selects are fed by the existing application list and control catalogue, so only real values can be chosen.
  const apps = useAsync(() => listApplications(), [])
  const catalog = useAsync(() => listReuseControls(), [])
  const appList = Array.isArray(apps.data) ? apps.data : []
  const controlList = Array.isArray(catalog.data) ? catalog.data : []
  const appOptions = withCurrent(appList.map((a) => a.slug).sort(), applicationSlug)
  const frameworkOptions = withCurrent(
    Array.from(new Set(controlList.flatMap((c) => c.frameworks))).sort(),
    framework,
  )
  const controlOptions = withCurrent(
    controlList.filter((c) => c.frameworks.includes(framework)).map((c) => c.controlId).sort(),
    controlId,
  )

  function chooseFramework(next: string) {
    setFramework(next)
    // a control only belongs to the frameworks it maps to; drop a selection that no longer fits
    const stillValid = controlList.some((c) => c.controlId === controlId && c.frameworks.includes(next))
    if (!stillValid) setControlId('')
  }

  useEffect(() => {
    if (error) errorRef.current?.scrollIntoView?.({ behavior: 'smooth', block: 'center' })
  }, [error])

  /** The single path for adding files (browse and drop). Takes an already-copied File[]: a FileList is live and is
   *  emptied when the input is reset, so it must never be read inside the state updater. */
  function addFiles(picked: File[]) {
    if (picked.length === 0) return
    setFiles((fs) => {
      // Dedup is scoped to the currently-selected files only (`fs`, the live state),
      // never a separately-tracked "ever seen" set — so a file removed via removeFile
      // is no longer in `fs` and is free to be re-added.
      const existing = new Set(fs.map((f) => `${f.name}|${f.size}`))
      const next = picked.filter((f) => !existing.has(`${f.name}|${f.size}`))
      return [...fs, ...next]
    })
    setInputKey((k) => k + 1)
  }

  function removeFile(i: number) {
    setFiles((fs) => fs.filter((_, idx) => idx !== i))
    setInputKey((k) => k + 1)
  }

  async function send(toSend: File[], meta: SubmittedMeta, retryOfBatchId?: string) {
    setBusy(true)
    setError(undefined)
    setResult(undefined)
    try {
      // keyed by position in this submission ("#0", "#1", ...), not by name, so two files that share a name each
      // carry their own hash
      const checksums: Record<string, string> = {}
      for (let i = 0; i < toSend.length; i++) {
        if (isZip(toSend[i])) continue // a zip is expanded server-side; its entries are hashed there
        const h = await sha256Hex(toSend[i])
        if (h) checksums[`#${i}`] = h
      }
      const res = await ingestBulkUpload(toSend, {
        ...meta,
        collectedBy: persona?.name,
        retryOfBatchId,
        checksums,
      })
      setResult(res)
      setSubmitted({ files: toSend, meta })
      batches.reload()
      return true
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e))
      return false
    } finally {
      setBusy(false)
    }
  }

  async function submit() {
    setAttempted(true)
    setError(undefined)
    setResult(undefined)
    const missing = [
      !applicationSlug.trim() && 'Application',
      !framework.trim() && 'Framework',
      !controlId.trim() && 'Control ID',
    ].filter((m): m is string => !!m)
    if (missing.length > 0) {
      const list = missing.length > 1 ? `${missing.slice(0, -1).join(', ')} and ${missing[missing.length - 1]}` : missing[0]
      setError(`${list} ${missing.length > 1 ? 'are' : 'is'} required.`)
      return
    }
    if (files.length === 0) {
      setError('Select at least one file, or a single .zip archive.')
      return
    }
    if (files.length > 1 && files.some(isZip)) {
      setError('Upload either individual files or a single .zip archive, not a .zip together with other files.')
      return
    }
    const ok = await send(files, {
      applicationSlug: applicationSlug.trim(),
      framework: framework.trim(),
      controlId: controlId.trim(),
      technology: technology.trim() || undefined,
      environment: environment || undefined,
    })
    if (ok) setFiles([])
  }

  /** Re-send only the files whose processing failed (e.g. a temporary storage outage), linked to the original batch. */
  async function retryFailed() {
    if (!result?.batchId || !submitted) return
    const failed = (result.files ?? []).filter((f) => f.status === 'FAILED' && f.retryable)
    const names = new Set(failed.flatMap((f) => [f.filename, f.archive ?? '']))
    const toRetry = submitted.files.filter((f) => names.has(f.name))
    if (toRetry.length === 0) {
      setError('The failed files are no longer selected — add them again and submit a new batch.')
      return
    }
    await send(toRetry, submitted.meta, result.batchId)
  }

  const hasZip = files.some(isZip)
  const retryable = (result?.files ?? []).filter((f) => f.status === 'FAILED' && f.retryable)

  return (
    <div className="page">
      <h1>Bulk Upload</h1>
      <p className="muted">
        Submits <code>POST /api/v1/evidence/bulk/upload</code> (multipart). Upload multiple files, or a single{' '}
        <code>.zip</code> archive whose contents are unzipped and ingested as separate evidence items. Each upload is a
        batch with its own id; every file reports its own outcome and the rest of the batch is still processed.
      </p>
      {persona ? (
        <p className="muted small">
          Uploading as <strong>{persona.name}</strong> ({persona.code}).
        </p>
      ) : null}
      {!canUpload ? (
        <ErrorNote message={`Your role cannot upload evidence (${persona?.name} can view batches only). Sign in as an App Owner or Admin to upload.`} />
      ) : null}

      {/* a role that cannot upload sees the whole form, but disabled (the backend also answers 403) */}
      <fieldset className="upload-form" disabled={!canUpload}>
      <Section title="Common fields">
        <div className="filter-row">
          <label>
            Application *
            <select
              aria-label="Application"
              aria-required="true"
              aria-invalid={attempted && !applicationSlug.trim()}
              className={attempted && !applicationSlug.trim() ? 'input-invalid' : undefined}
              value={applicationSlug}
              onChange={(e) => setApp(e.target.value)}
            >
              <option value="">Select application</option>
              {appOptions.map((slug) => (
                <option key={slug} value={slug}>
                  {slug}
                </option>
              ))}
            </select>
          </label>
          <label>
            Framework *
            <select
              aria-label="Framework"
              aria-required="true"
              aria-invalid={attempted && !framework.trim()}
              className={attempted && !framework.trim() ? 'input-invalid' : undefined}
              value={framework}
              onChange={(e) => chooseFramework(e.target.value)}
            >
              <option value="">Select framework</option>
              {frameworkOptions.map((fw) => (
                <option key={fw} value={fw}>
                  {fw}
                </option>
              ))}
            </select>
          </label>
          <label>
            Control ID *
            <select
              aria-label="Control ID"
              aria-required="true"
              aria-invalid={attempted && !controlId.trim()}
              className={attempted && !controlId.trim() ? 'input-invalid' : undefined}
              value={controlId}
              onChange={(e) => setControlId(e.target.value)}
            >
              <option value="">Select control</option>
              {controlOptions.map((id) => (
                <option key={id} value={id}>
                  {id}
                </option>
              ))}
            </select>
          </label>
          <label>
            Technology
            <input
              aria-label="Technology"
              value={technology}
              onChange={(e) => setTechnology(e.target.value)}
              placeholder="postgresql / nginx / … (optional)"
            />
          </label>
          <label>
            Environment
            <select aria-label="Environment" value={environment} onChange={(e) => setEnvironment(e.target.value)}>
              <option value="">Not specified</option>
              {ENVIRONMENTS.map((env) => (
                <option key={env} value={env}>
                  {env}
                </option>
              ))}
            </select>
          </label>
        </div>
      </Section>

      <Section title="Files">
        <div
          className={`dropzone${dragOver ? ' dropzone-active' : ''}`}
          onClick={() => inputRef.current?.click()}
          onKeyDown={(e) => {
            if (e.target === e.currentTarget && (e.key === 'Enter' || e.key === ' ')) {
              e.preventDefault()
              inputRef.current?.click()
            }
          }}
          onDragOver={(e) => {
            e.preventDefault()
            setDragOver(true)
          }}
          onDragLeave={() => setDragOver(false)}
          onDrop={(e) => {
            e.preventDefault()
            setDragOver(false)
            addFiles(Array.from(e.dataTransfer.files))
          }}
          role="button"
          tabIndex={0}
          aria-label="File drop zone"
        >
          <p>Drag and drop files here, or click to browse.</p>
          <p className="muted">
            Multiple individual files, or a single .zip archive. Accepted: {BULK_UPLOAD_EXTENSIONS.join(', ')} (up to 10
            MB each).
          </p>
          <input
            key={inputKey}
            ref={inputRef}
            type="file"
            multiple
            aria-label="Choose files"
            className="visually-hidden"
            onClick={(e) => e.stopPropagation()}
            onChange={(e) => {
              const picked = Array.from(e.target.files ?? []) // copy first: resetting the input empties the live FileList
              e.target.value = ''
              addFiles(picked)
            }}
          />
        </div>

        {hasZip && files.length > 1 ? (
          <p className="muted">A .zip archive must be uploaded on its own: remove the other files or the archive.</p>
        ) : null}

        {files.length > 0 ? (
          <ul className="file-list">
            {files.map((f, i) => (
              <li key={`${f.name}|${f.size}|${i}`}>
                <span className="file-list-name">{f.name}</span>
                <span className="file-list-size">{formatSize(f.size)}</span>
                <button type="button" onClick={() => removeFile(i)} aria-label={`Remove ${f.name}`}>
                  ✕
                </button>
              </li>
            ))}
          </ul>
        ) : (
          <p className="muted">No files selected.</p>
        )}
      </Section>

      <button className="primary" onClick={submit} disabled={busy || !canUpload}>
        {busy ? 'Uploading…' : 'Submit bulk'}
      </button>
      </fieldset>

      {error ? (
        <div ref={errorRef}>
          <ErrorNote message={error} />
        </div>
      ) : null}

      {result ? <ResultPanel result={result} controlId={submitted?.meta.controlId ?? ''} retryable={retryable} busy={busy} onRetry={retryFailed} /> : null}

      <Section
        title="Recent batches"
        actions={
          <button type="button" onClick={() => batches.reload()}>
            Refresh
          </button>
        }
      >
        {batches.error ? <ErrorNote message={batches.error} /> : null}
        {batches.data && batches.data.items.length === 0 ? <Empty message="No bulk-upload batches yet." /> : null}
        {batches.data && batches.data.items.length > 0 ? (
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Batch</th>
                  <th>Status</th>
                  <th>When</th>
                  <th>By</th>
                  <th className="num">Files</th>
                  <th className="num">Added</th>
                  <th className="num">Dup.</th>
                  <th className="num">Not stored</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {batches.data.items.map((b) => (
                  <Fragment key={b.runId}>
                    <tr>
                      <td>
                        <code title={b.runId}>{b.runId.slice(0, 8)}</code>
                      </td>
                      <td>
                        <StatusPill status={b.status} />
                      </td>
                      <td>{b.createdAt ? new Date(b.createdAt).toLocaleString() : '—'}</td>
                      <td>{b.triggeredBy ?? b.requestedBy ?? '—'}</td>
                      <td className="num">{b.received}</td>
                      <td className="num">{b.ingested}</td>
                      <td className="num">{b.duplicates}</td>
                      <td className="num">{b.failed}</td>
                      <td>
                        <button
                          type="button"
                          aria-expanded={openBatch === b.runId}
                          onClick={() => setOpenBatch(openBatch === b.runId ? undefined : b.runId)}
                        >
                          {openBatch === b.runId ? 'Hide' : 'Details'}
                        </button>
                      </td>
                    </tr>
                    {/* the per-file detail opens directly under its own batch row */}
                    {openBatch === b.runId ? (
                      <tr className="batch-detail-row">
                        <td colSpan={9}>
                          <BatchDetail batch={b} />
                        </td>
                      </tr>
                    ) : null}
                  </Fragment>
                ))}
              </tbody>
            </table>
          </div>
        ) : null}
        {batches.data && batches.data.totalItems > batches.data.items.length ? (
          <p>
            <button type="button" disabled={batches.loading} onClick={() => setBatchLimit((n) => n + BATCH_PAGE)}>
              Show more ({batches.data.items.length} of {batches.data.totalItems})
            </button>
          </p>
        ) : null}
      </Section>
    </div>
  )
}

function ResultPanel({
  result,
  controlId,
  retryable,
  busy,
  onRetry,
}: {
  result: BulkIngestResponse
  controlId: string
  retryable: BulkFileOutcome[]
  busy: boolean
  onRetry: () => void
}) {
  const files = result.files
  const added = files ? files.filter((f) => OK_STATUSES.has(f.status)).length : result.results.filter((r) => r.outcome !== 'DUPLICATE').length
  const duplicates = result.results.filter((r) => r.outcome === 'DUPLICATE')
  const notStored = files ? files.filter((f) => NOT_STORED.has(f.status)) : []
  return (
    <Section title="Result">
      {result.batchId ? (
        <p className="small">
          Batch <code>{result.batchId}</code> {result.status ? <StatusPill status={result.status} /> : null}{' '}
          {result.uploadedBy ? <span className="muted">by {result.uploadedBy}</span> : null}
          {' · '}
          <Link to={`/evidence?tag=${encodeURIComponent(`batchId:${result.batchId}`)}`}>Open this batch in the Repository</Link>
          {' · '}
          <Link to={`/scheduler?run=${result.batchId}`}>Open run audit</Link>
        </p>
      ) : null}
      <p className="muted small">
        {added} added successfully
        {duplicates.length > 0 ? `, ${duplicates.length} skipped as duplicate` : ''}
        {result.failed > 0 ? `, ${result.failed} failed` : ''}.
      </p>
      <div className="stat-grid">
        <StatCard label="Received" value={result.received} />
        <StatCard label="Created" value={result.created} />
        <StatCard label="New versions" value={result.newVersions} />
        <StatCard label="Duplicates" value={result.duplicates} />
        <StatCard label="Failed" value={result.failed} />
      </div>

      {duplicates.length > 0 ? (
        <div className="duplicate-note" role="status">
          <p>
            <strong>Skipped as duplicate ({duplicates.length}):</strong>
          </p>
          <ul>
            {duplicates.map((r, i) => (
              <li key={`${r.evidenceId}-${i}`}>
                <strong>{r.sourceObjectId ?? r.controlId}</strong> was not added — duplicate of existing evidence (
                <Link to={`/evidence/${r.evidenceId}`}>{r.evidenceId.slice(0, 8)}</Link>, version {r.version}).
              </li>
            ))}
          </ul>
        </div>
      ) : null}

      <DataTable
        rows={files ?? result.results.map((r, index) => ({ index, filename: r.sourceObjectId ?? '—', status: String(r.outcome), evidenceId: r.evidenceId, version: r.version, sha256: r.sha256, sizeBytes: r.sizeBytes }) as BulkFileOutcome)}
        rowKey={(f) => `${f.index}-${f.filename}`}
        columns={[
          {
            header: 'File',
            cell: (f) => (f.evidenceId ? <Link to={`/evidence/${f.evidenceId}`}>{f.filename}</Link> : f.filename),
          },
          { header: 'Control', cell: () => controlId || '—' },
          { header: 'Outcome', cell: (f) => <StatusPill status={f.status} /> },
          { header: 'Version', cell: (f) => f.version ?? '—', align: 'right' },
          { header: 'SHA-256', cell: (f) => (f.sha256 ? <code className="hash-full" title={f.sha256}>{f.sha256}</code> : '—') },
          { header: 'Note', cell: (f) => f.message ?? '' },
        ]}
      />

      {notStored.length > 0 ? (
        <>
          <p className="muted small">
            <strong>Not stored ({notStored.length}):</strong> the rest of the batch was kept.
            {retryable.length > 0 ? ' Failed files can be retried without re-uploading the ones that succeeded.' : ''}
          </p>
          <ul>
            {notStored.map((f) => (
              <li key={`${f.index}-${f.filename}`}>
                <strong>{f.archive ? `${f.archive} → ${f.filename}` : f.filename}</strong> — {f.message}
                {f.code ? <span className="muted"> ({f.code})</span> : null}
              </li>
            ))}
          </ul>
          {retryable.length > 0 ? (
            <button type="button" onClick={onRetry} disabled={busy}>
              Retry {retryable.length} failed file{retryable.length === 1 ? '' : 's'}
            </button>
          ) : null}
        </>
      ) : null}

      {!files && result.errors.length > 0 ? (
        <>
          <p className="muted small">
            <strong>Failed ({result.errors.length}):</strong>
          </p>
          <DataTable
            rows={result.errors}
            rowKey={(e) => String(e.index)}
            columns={[
              { header: 'Index', cell: (e) => e.index, align: 'right' },
              { header: 'Error', cell: (e) => e.message },
            ]}
          />
        </>
      ) : null}
    </Section>
  )
}

/** Per-file statuses of a stored batch (from the run's per-source summary), so a batch can be reviewed after a reload. */
function BatchDetail({ batch }: { batch?: RunView }) {
  if (!batch) return null
  const rows = Object.entries(batch.perSource ?? {}).filter(([k]) => !k.startsWith('_'))
  return (
    <div className="batch-detail">
      <p className="small">
        Batch <code>{batch.runId}</code> · {batch.status}
        {batch.retryOfRunId ? <> · retry of <code>{batch.retryOfRunId.slice(0, 8)}</code></> : null} ·{' '}
        <Link to={`/evidence?tag=${encodeURIComponent(`batchId:${batch.runId}`)}`}>Open in Repository</Link> ·{' '}
        <Link to={`/scheduler?run=${batch.runId}`}>Open run audit</Link>
      </p>
      <DataTable
        rows={rows.map(([file, text]) => ({ file, text }))}
        rowKey={(r) => r.file}
        columns={[
          { header: 'File', cell: (r) => r.file },
          { header: 'Detail', cell: (r) => <span className="small">{r.text}</span> },
        ]}
      />
    </div>
  )
}
