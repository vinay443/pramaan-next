import { useEffect, useRef, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { ingestBulkUpload } from '../api/endpoints'
import type { BulkIngestResponse } from '../api/types'
import { DataTable, ErrorNote, Section, StatCard, StatusPill } from '../components/ui'

function formatSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

function isZip(file: File): boolean {
  return file.name.toLowerCase().endsWith('.zip') || file.type === 'application/zip'
}

export function BulkUpload() {
  // Compliance-tab "Upload evidence" links prefill these via query params.
  const [searchParams] = useSearchParams()
  const [applicationSlug, setApp] = useState(searchParams.get('applicationSlug') ?? 'net-banking')
  const [framework, setFramework] = useState(searchParams.get('framework') ?? 'PCI_DSS')
  const [controlId, setControlId] = useState(searchParams.get('controlId') ?? '')
  const [technology, setTechnology] = useState('')
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
  const [error, setError] = useState<string>()
  const [attempted, setAttempted] = useState(false)
  const [busy, setBusy] = useState(false)
  const inputRef = useRef<HTMLInputElement>(null)
  const errorRef = useRef<HTMLDivElement>(null)

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

  async function submit() {
    setBusy(true)
    setError(undefined)
    setResult(undefined)
    setAttempted(true)
    try {
      if (!applicationSlug.trim() || !framework.trim() || !controlId.trim()) {
        throw new Error('Application, framework and control are required.')
      }
      if (files.length === 0) {
        throw new Error('Select at least one file, or a single .zip archive.')
      }
      setResult(
        await ingestBulkUpload(files, {
          applicationSlug: applicationSlug.trim(),
          framework: framework.trim(),
          controlId: controlId.trim(),
          technology: technology.trim() || undefined,
        }),
      )
      setFiles([])
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e))
    } finally {
      setBusy(false)
    }
  }

  const hasZip = files.some(isZip)

  return (
    <div className="page">
      <h1>Bulk Upload</h1>
      <p className="muted">
        Submits <code>POST /api/v1/evidence/bulk/upload</code> (multipart). Upload multiple files, or a single{' '}
        <code>.zip</code> archive whose contents are unzipped and ingested as separate evidence items. Partial
        success is allowed — each file reports its own outcome.
      </p>

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
          <p className="muted">Multiple individual files, or a single .zip archive.</p>
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

      <button className="primary" onClick={submit} disabled={busy}>
        {busy ? 'Uploading…' : 'Submit bulk'}
      </button>

      {error ? (
        <div ref={errorRef}>
          <ErrorNote message={error} />
        </div>
      ) : null}

      {result
        ? (() => {
            const succeeded = result.results.filter((r) => r.outcome !== 'DUPLICATE')
            const duplicates = result.results.filter((r) => r.outcome === 'DUPLICATE')
            return (
              <Section title="Result">
                <p className="muted small">
                  {succeeded.length} added successfully
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
                          <strong>{r.sourceObjectId ?? r.controlId}</strong> was not added — duplicate of existing
                          evidence (
                          <Link to={`/evidence/${r.evidenceId}`}>{r.evidenceId.slice(0, 8)}</Link>, version {r.version}
                          ).
                        </li>
                      ))}
                    </ul>
                  </div>
                ) : null}

                <DataTable
                  rows={result.results}
                  rowKey={(r) => r.evidenceId}
                  columns={[
                    { header: 'File', cell: (r) => r.sourceObjectId ?? '—' },
                    { header: 'Control', cell: (r) => r.controlId },
                    { header: 'Outcome', cell: (r) => <StatusPill status={r.outcome} /> },
                    { header: 'Version', cell: (r) => r.version, align: 'right' },
                    { header: 'SHA-256', cell: (r) => <code>{r.sha256.slice(0, 16)}…</code> },
                  ]}
                />

                {result.errors.length > 0 ? (
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
          })()
        : null}
    </div>
  )
}
