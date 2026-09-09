import { useState } from 'react'
import { ingestBulk } from '../api/endpoints'
import type { BulkIngestResponse, IngestRequest } from '../api/types'
import { DataTable, ErrorNote, Section, StatCard, StatusPill } from '../components/ui'

interface Row {
  controlId: string
  title: string
  contentText: string
}

const EMPTY: Row = { controlId: '', title: '', contentText: '' }

export function BulkUpload() {
  const [applicationSlug, setApp] = useState('net-banking')
  const [framework, setFramework] = useState('PCI_DSS')
  const [sourceSystem, setSource] = useState('BULK_UPLOAD')
  const [technology, setTechnology] = useState('')
  const [collectedBy, setCollectedBy] = useState('ui-user')
  const [rows, setRows] = useState<Row[]>([{ ...EMPTY }])
  const [jsonMode, setJsonMode] = useState(false)
  const [jsonText, setJsonText] = useState('')
  const [result, setResult] = useState<BulkIngestResponse>()
  const [error, setError] = useState<string>()
  const [busy, setBusy] = useState(false)

  function setRow(i: number, patch: Partial<Row>) {
    setRows((rs) => rs.map((r, idx) => (idx === i ? { ...r, ...patch } : r)))
  }

  function buildItems(): IngestRequest[] {
    if (jsonMode) {
      const parsed = JSON.parse(jsonText)
      if (!Array.isArray(parsed)) throw new Error('JSON must be an array of IngestRequest objects')
      return parsed as IngestRequest[]
    }
    const tags: Record<string, string> = { 'ingest.channel': 'bulk-upload' }
    if (technology.trim()) tags.technology = technology.trim()
    return rows
      .filter((r) => r.controlId.trim() && r.contentText.trim())
      .map((r) => ({
        applicationSlug,
        controlId: r.controlId.trim(),
        framework,
        sourceSystem,
        title: r.title.trim() || undefined,
        contentText: r.contentText,
        collectedBy,
        collectedAt: new Date().toISOString(),
        tags,
      }))
  }

  async function submit() {
    setBusy(true)
    setError(undefined)
    setResult(undefined)
    try {
      const items = buildItems()
      if (items.length === 0) throw new Error('Add at least one item with a control and content.')
      setResult(await ingestBulk(items))
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="page">
      <h1>Bulk Upload</h1>
      <p className="muted">
        Submits <code>POST /api/v1/evidence/bulk</code> (a JSON array of <code>IngestRequest</code>). Partial success is
        allowed — each item reports its own outcome.
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
            Source system
            <input aria-label="Source system" value={sourceSystem} onChange={(e) => setSource(e.target.value)} />
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
            Collected by
            <input aria-label="Collected by" value={collectedBy} onChange={(e) => setCollectedBy(e.target.value)} />
          </label>
        </div>
        <label className="checkbox">
          <input type="checkbox" checked={jsonMode} onChange={(e) => setJsonMode(e.target.checked)} /> Paste raw JSON array
          instead
        </label>
      </Section>

      {jsonMode ? (
        <Section title="JSON array">
          <textarea
            aria-label="JSON array"
            rows={10}
            value={jsonText}
            onChange={(e) => setJsonText(e.target.value)}
            placeholder='[{"applicationSlug":"payments","controlId":"C-1","framework":"ITPP","sourceSystem":"BULK_UPLOAD","contentText":"..."}]'
          />
        </Section>
      ) : (
        <Section
          title="Items"
          actions={
            <button type="button" onClick={() => setRows((rs) => [...rs, { ...EMPTY }])}>
              + Add item
            </button>
          }
        >
          {rows.map((r, i) => (
            <div className="item-row" key={i}>
              <input
                aria-label={`Control ${i + 1}`}
                placeholder="controlId"
                value={r.controlId}
                onChange={(e) => setRow(i, { controlId: e.target.value })}
              />
              <input
                aria-label={`Title ${i + 1}`}
                placeholder="title (optional)"
                value={r.title}
                onChange={(e) => setRow(i, { title: e.target.value })}
              />
              <input
                aria-label={`Content ${i + 1}`}
                placeholder="evidence content (text)"
                value={r.contentText}
                onChange={(e) => setRow(i, { contentText: e.target.value })}
              />
              {rows.length > 1 ? (
                <button type="button" onClick={() => setRows((rs) => rs.filter((_, idx) => idx !== i))}>
                  ✕
                </button>
              ) : null}
            </div>
          ))}
        </Section>
      )}

      <button className="primary" onClick={submit} disabled={busy}>
        {busy ? 'Submitting…' : 'Submit bulk'}
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
