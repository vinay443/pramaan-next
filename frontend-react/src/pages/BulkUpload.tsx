import { useRef, useState } from 'react'
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
  const [applicationSlug, setApp] = useState('net-banking')
  const [framework, setFramework] = useState('PCI_DSS')
  const [controlId, setControlId] = useState('')
  const [technology, setTechnology] = useState('')
  const [files, setFiles] = useState<File[]>([])
  const [dragOver, setDragOver] = useState(false)
  const [result, setResult] = useState<BulkIngestResponse>()
  const [error, setError] = useState<string>()
  const [busy, setBusy] = useState(false)
  const inputRef = useRef<HTMLInputElement>(null)

  function addFiles(list: FileList | null) {
    if (!list || list.length === 0) return
    setFiles((fs) => {
      const existing = new Set(fs.map((f) => `${f.name}|${f.size}`))
      const next = Array.from(list).filter((f) => !existing.has(`${f.name}|${f.size}`))
      return [...fs, ...next]
    })
  }

  function removeFile(i: number) {
    setFiles((fs) => fs.filter((_, idx) => idx !== i))
  }

  async function submit() {
    setBusy(true)
    setError(undefined)
    setResult(undefined)
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
            Application
            <input aria-label="Application" value={applicationSlug} onChange={(e) => setApp(e.target.value)} />
          </label>
          <label>
            Framework
            <input aria-label="Framework" value={framework} onChange={(e) => setFramework(e.target.value)} />
          </label>
          <label>
            Control ID
            <input
              aria-label="Control ID"
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

      {error ? <ErrorNote message={error} /> : null}

      {result ? (
        <Section title="Result">
          <div className="stat-grid">
            <StatCard label="Received" value={result.received} />
            <StatCard label="Created" value={result.created} />
            <StatCard label="New versions" value={result.newVersions} />
            <StatCard label="Duplicates" value={result.duplicates} />
            <StatCard label="Failed" value={result.failed} />
          </div>
          <DataTable
            rows={result.results}
            rowKey={(r) => r.evidenceId}
            columns={[
              { header: 'Control', cell: (r) => r.controlId },
              { header: 'Outcome', cell: (r) => <StatusPill status={r.outcome} /> },
              { header: 'Version', cell: (r) => r.version, align: 'right' },
              { header: 'SHA-256', cell: (r) => <code>{r.sha256.slice(0, 16)}…</code> },
            ]}
          />
          {result.errors.length > 0 ? (
            <DataTable
              rows={result.errors}
              rowKey={(e) => String(e.index)}
              columns={[
                { header: 'Index', cell: (e) => e.index, align: 'right' },
                { header: 'Error', cell: (e) => e.message },
              ]}
            />
          ) : null}
        </Section>
      ) : null}
    </div>
  )
}
