import { useEffect, useRef, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { ingestBulkUpload, listBulkBatches } from '../api/endpoints'
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
  const batches = useAsync(() => listBulkBatches(10), [])

  useEffect(() => {
    if (error) errorRef.current?.scrollIntoView?.({ behavior: 'smooth', block: 'center' })
  }, [error])

  function addFiles(list: FileList | null) {
    if (!list || list.length === 0) return
    setFiles((fs) => {
      // Dedup is scoped to the currently-selected files only (`fs`, the live state),
      // never a separately-tracked "ever seen" set — so a file removed via removeFile
      // is no longer in `fs` and is free to be re-added.
      const existing = new Set(fs.map((f) => `${f.name}|${f.size}`))
      const next = Array.from(list).filter((f) => !existing.has(`${f.name}|${f.size}`))
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
      const checksums: Record<string, string> = {}
      for (const f of toSend) {
        if (isZip(f)) continue // a zip is expanded server-side; its entries are hashed there
        const h = await sha256Hex(f)
        if (h) checksums[f.name] = h
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
    if (!applicationSlug.trim() || !framework.trim() || !controlId.trim()) {
      setError('Application, framework and control are required.')
      return
    }
    if (files.length === 0) {
      setError('Select at least one file, or a single .zip archive.')
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
        <ErrorNote message={`Your role (${persona?.name}) can view batches but cannot upload evidence. Sign in as an App Owner or Admin to upload.`} />
      ) : null}

      <Section title="Common fields">
        <div className="filter-row">
          <label>
            Application *
            <input
              aria-label="Application"
              aria-required="true"
              aria-invalid={attempted && !applicationSlug.trim()}
              className={attempted && !applicationSlug.trim() ? 'input-invalid' : undefined}
              value={applicationSlug}
              onChange={(e) => setApp(e.target.value)}
            />
          </label>
          <label>
            Framework *
            <input
              aria-label="Framework"
              aria-required="true"
              aria-invalid={attempted && !framework.trim()}
              className={attempted && !framework.trim() ? 'input-invalid' : undefined}
              value={framework}
              onChange={(e) => setFramework(e.target.value)}
            />
          </label>
          <label>
            Control ID *
            <input
              aria-label="Control ID"
              aria-required="true"
              aria-invalid={attempted && !controlId.trim()}
              className={attempted && !controlId.trim() ? 'input-invalid' : undefined}
              value={controlId}
              onChange={(e) => setControlId(e.target.value)}
              placeholder="e.g. PCI-DSS-6.2"
            />
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
          onDragOver={(e) => {
            e.preventDefault()
            setDragOver(true)
          }}
          onDragLeave={() => setDragOver(false)}
          onDrop={(e) => {
            e.preventDefault()
            setDragOver(false)
            addFiles(e.dataTransfer.files)
          }}
          role="button"
          tabIndex={0}
          aria-label="File drop zone"
        >
          <p>Drag and drop files here, or click to browse.</p>
          <p className="muted">
            Multiple individual files, or a single .zip archive. Accepted: pdf, txt, csv, json, xml, yaml, log, conf, xlsx,
            docx, pptx, png, jpg (up to 10 MB each).
          </p>
          <input
            key={inputKey}
            ref={inputRef}
            type="file"
            multiple
            aria-label="Choose files"
            className="visually-hidden"
            onChange={(e) => {
              addFiles(e.target.files)
              e.target.value = ''
            }}
          />
        </div>

        {hasZip && files.length > 1 ? (
          <p className="muted">Note: a .zip archive is expanded server-side; other selected files are uploaded as-is.</p>
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
          <DataTable
            rows={batches.data.items}
            rowKey={(b) => b.runId}
            columns={[
              { header: 'Batch', cell: (b) => <code title={b.runId}>{b.runId.slice(0, 8)}</code> },
              { header: 'Status', cell: (b) => <StatusPill status={b.status} /> },
              { header: 'When', cell: (b) => (b.createdAt ? new Date(b.createdAt).toLocaleString() : '—') },
              { header: 'By', cell: (b) => b.triggeredBy ?? b.requestedBy ?? '—' },
              { header: 'Files', cell: (b) => b.received, align: 'right' },
              { header: 'Added', cell: (b) => b.ingested, align: 'right' },
              { header: 'Dup.', cell: (b) => b.duplicates, align: 'right' },
              { header: 'Not stored', cell: (b) => b.failed, align: 'right' },
              {
                header: '',
                cell: (b) => (
                  <button type="button" onClick={() => setOpenBatch(openBatch === b.runId ? undefined : b.runId)}>
                    {openBatch === b.runId ? 'Hide' : 'Details'}
                  </button>
                ),
              },
            ]}
          />
        ) : null}
        {openBatch && batches.data ? <BatchDetail batch={batches.data.items.find((b) => b.runId === openBatch)} /> : null}
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
