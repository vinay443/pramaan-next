import { Fragment, useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { listEvidence } from '../api/endpoints'
import { onDataEvent } from '../api/events'
import type { EvidenceQueryParams, EvidenceView } from '../api/types'
import { useAsync } from '../hooks/useAsync'
import { Empty, ErrorNote, Loading, Section, StatusPill } from '../components/ui'
import { MetadataEditPanel } from '../components/MetadataEditPanel'

const PAGE_SIZE = 20

export function EvidenceRepository() {
  const navigate = useNavigate()
  // Deep links (e.g. from the dashboard) seed the filters so both pages describe the same set.
  const [searchParams] = useSearchParams()
  const [filters, setFilters] = useState<EvidenceQueryParams>(() => {
    const seed: EvidenceQueryParams = {}
    for (const k of ['applicationSlug', 'framework', 'collectionMethod', 'collectedAfter', 'collectedBefore', 'tag'] as const) {
      const v = searchParams.get(k)
      if (v) seed[k] = v
    }
    return seed
  })
  const [page, setPage] = useState(0)
  const [expanded, setExpanded] = useState<string | null>(null)

  const query = useMemo<EvidenceQueryParams>(
    () => ({ ...clean(filters), page, size: PAGE_SIZE }),
    [filters, page],
  )
  const { data, loading, error, reload } = useAsync(() => listEvidence(query), [JSON.stringify(query)])

  // Refresh when another page (e.g. Scheduler) reports a collection run pulled
  // new evidence, so it appears even if this page was already open.
  useEffect(() => onDataEvent('evidence-changed', reload), [reload])

  function update(field: keyof EvidenceQueryParams, value: string) {
    setPage(0)
    setFilters((f) => ({ ...f, [field]: value || undefined }))
  }

  return (
    <div className="page">
      <h1>Evidence Repository</h1>

      <Section title="Filters">
        <div className="filter-row">
          <label>
            Application
            <input
              aria-label="Application"
              value={filters.applicationSlug ?? ''}
              onChange={(e) => update('applicationSlug', e.target.value)}
              placeholder="net-banking"
            />
          </label>
          <label>
            Framework
            <input
              aria-label="Framework"
              value={filters.framework ?? ''}
              onChange={(e) => update('framework', e.target.value)}
              placeholder="PCI_DSS"
            />
          </label>
          <label>
            Control
            <input
              aria-label="Control"
              value={filters.controlId ?? ''}
              onChange={(e) => update('controlId', e.target.value)}
              placeholder="OS-SSH-ROOT-LOGIN"
            />
          </label>
          <label>
            Source system
            <input
              aria-label="Source system"
              value={filters.sourceSystem ?? ''}
              onChange={(e) => update('sourceSystem', e.target.value)}
              placeholder="AGENT_OS_LINUX"
            />
          </label>
          <label>
            Technology
            <input
              aria-label="Technology"
              value={filters.technology ?? ''}
              onChange={(e) => update('technology', e.target.value)}
              placeholder="postgresql"
            />
          </label>
          <label>
            Collection method
            <select
              aria-label="Collection method"
              value={filters.collectionMethod ?? ''}
              onChange={(e) => update('collectionMethod', e.target.value)}
            >
              <option value="">(any)</option>
              <option value="scheduled">scheduled</option>
              <option value="manual">manual</option>
              <option value="bulk">bulk</option>
              <option value="on-demand">on-demand</option>
              <option value="predefined-query">predefined-query</option>
            </select>
          </label>
          <label>
            Tag (key or key:value)
            <input aria-label="Tag" value={filters.tag ?? ''} onChange={(e) => update('tag', e.target.value)} />
          </label>
        </div>
      </Section>

      <Section
        title={`Results${data ? ` — ${data.totalItems}` : ''}`}
        actions={
          data && data.totalPages > 1 ? (
            <div className="pager">
              <button disabled={page === 0} onClick={() => setPage((p) => p - 1)}>
                Prev
              </button>
              <span>
                Page {page + 1} / {data.totalPages}
              </span>
              <button disabled={page + 1 >= data.totalPages} onClick={() => setPage((p) => p + 1)}>
                Next
              </button>
            </div>
          ) : undefined
        }
      >
        {loading ? <Loading what="evidence" /> : null}
        {error ? <ErrorNote message={error} /> : null}
        {data && data.items.length === 0 ? <Empty message="No evidence matches these filters." /> : null}
        {data && data.items.length > 0 ? (
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th aria-hidden="true" />
                  <th>Name</th>
                  <th>File</th>
                  <th>Control</th>
                  <th>Frameworks</th>
                  <th>Collected</th>
                  <th>Integrity</th>
                </tr>
              </thead>
              <tbody>
                {data.items.map((r) => {
                  const name = r.tags.name ?? r.title ?? '—'
                  const isExpanded = expanded === r.evidenceId
                  return (
                    <Fragment key={r.evidenceId}>
                      <tr className="clickable" onClick={() => navigate(`/evidence/${r.evidenceId}`)}>
                        <td>
                          <button
                            type="button"
                            className="row-expand-btn"
                            aria-expanded={isExpanded}
                            aria-label={isExpanded ? 'Hide details' : 'Show details'}
                            onClick={(e) => {
                              e.stopPropagation()
                              setExpanded((cur) => (cur === r.evidenceId ? null : r.evidenceId))
                            }}
                          >
                            {isExpanded ? '▾' : '▸'}
                          </button>
                        </td>
                        <td className="cell-truncate" title={name}>
                          <code className="small">{name}</code>
                        </td>
                        <td className="cell-truncate" title={fileName(r)}>
                          {fileName(r)}
                        </td>
                        <td>{r.controlId}</td>
                        <td>
                          <div className="tag-stack">
                            {(r.tags.frameworks ?? r.framework).split(',').map((f) => (
                              <span key={f} className="tag">
                                {f}
                              </span>
                            ))}
                          </div>
                        </td>
                        <td title={r.latest ? formatDateTime(r.latest.collectedAt) : undefined}>
                          {r.latest ? new Date(r.latest.collectedAt).toLocaleDateString() : '—'}
                        </td>
                        <td>
                          <StatusPill status={r.integrityStatus ?? 'UNKNOWN'} />
                        </td>
                      </tr>
                      {isExpanded ? (
                        <tr>
                          <td colSpan={7} className="nested-cell">
                            <RowDetails row={r} onSaved={reload} />
                          </td>
                        </tr>
                      ) : null}
                    </Fragment>
                  )
                })}
              </tbody>
            </table>
          </div>
        ) : null}
      </Section>
    </div>
  )
}

/** Original file name: the source title tag, else the last segment of the evidence key (the source object id). */
function fileName(r: EvidenceView): string {
  return r.tags.sourceTitle || r.evidenceKey.split('|').pop() || '—'
}

function formatDateTime(iso?: string | null): string {
  if (!iso) return '—'
  const d = new Date(iso)
  return Number.isNaN(d.getTime()) ? iso : d.toLocaleString()
}

function formatSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

/** The full SHA-256 with a copy button; a failed clipboard write just leaves the text selectable. */
function HashWithCopy({ hash }: { hash: string }) {
  const [copied, setCopied] = useState(false)
  const timer = useRef<ReturnType<typeof setTimeout>>()
  useEffect(() => () => clearTimeout(timer.current), [])
  async function copy() {
    try {
      await navigator.clipboard.writeText(hash)
      setCopied(true)
      clearTimeout(timer.current)
      timer.current = setTimeout(() => setCopied(false), 1500)
    } catch {
      setCopied(false)
    }
  }
  return (
    <span>
      <code className="small hash-full">{hash}</code>{' '}
      <button type="button" aria-label="Copy SHA-256" onClick={copy}>
        {copied ? 'Copied' : 'Copy'}
      </button>
    </span>
  )
}

/** The columns moved out of the default set (step 2): shown on expand only. */
function RowDetails({ row, onSaved }: { row: EvidenceView; onSaved: () => void }) {
  const latest = row.latest
  return (
    <>
    <dl className="kv">
      <dt>Application</dt>
      <dd>{row.applicationSlug}</dd>
      <dt>File</dt>
      <dd>{fileName(row)}</dd>
      <dt>Type</dt>
      <dd>{row.tags.evidenceType ?? '—'}</dd>
      <dt>Source</dt>
      <dd>{row.sourceSystem}</dd>
      <dt>Technology</dt>
      <dd>{row.tags.technology ?? '—'}</dd>
      <dt>Environment</dt>
      <dd>{row.tags.environment ?? '—'}</dd>
      <dt>Framework(s)</dt>
      <dd>{row.tags.frameworks ?? row.framework}</dd>
      <dt>Method</dt>
      <dd>{row.tags.collectionMethod ?? '—'}</dd>
      <dt>Uploaded by</dt>
      <dd>{row.tags.uploadedBy ?? latest?.collectedBy ?? '—'}</dd>
      <dt>Uploaded at</dt>
      <dd>{formatDateTime(latest?.collectedAt ?? row.createdAt)}</dd>
      <dt>Batch ID</dt>
      <dd>{row.tags.batchId ? <code className="small">{row.tags.batchId}</code> : '—'}</dd>
      <dt>Ver</dt>
      <dd>{row.currentVersion}</dd>
      {latest ? (
        <>
          <dt>SHA-256</dt>
          <dd>
            <HashWithCopy hash={latest.sha256} />
          </dd>
          <dt>Size</dt>
          <dd>{formatSize(latest.sizeBytes)}</dd>
          <dt>Content type</dt>
          <dd>{latest.contentType}</dd>
          <dt>Collected at</dt>
          <dd>{formatDateTime(latest.collectedAt)}</dd>
        </>
      ) : null}
    </dl>
    <MetadataEditPanel row={row} onSaved={onSaved} />
    </>
  )
}

function clean(f: EvidenceQueryParams): EvidenceQueryParams {
  const out: EvidenceQueryParams = {}
  for (const [k, v] of Object.entries(f)) {
    if (v !== undefined && v !== '') (out as Record<string, unknown>)[k] = v
  }
  return out
}
