import { Fragment, useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { listEvidence } from '../api/endpoints'
import { onDataEvent } from '../api/events'
import type { EvidenceQueryParams, EvidenceView } from '../api/types'
import { useAsync } from '../hooks/useAsync'
import { Empty, ErrorNote, Loading, Section, StatusPill } from '../components/ui'

const PAGE_SIZE = 20

export function EvidenceRepository() {
  const navigate = useNavigate()
  const [filters, setFilters] = useState<EvidenceQueryParams>({})
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
                        <td>{r.latest ? new Date(r.latest.collectedAt).toLocaleDateString() : '—'}</td>
                        <td>
                          <StatusPill status={r.integrityStatus ?? 'UNKNOWN'} />
                        </td>
                      </tr>
                      {isExpanded ? (
                        <tr>
                          <td colSpan={6} className="nested-cell">
                            <RowDetails row={r} />
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

/** The columns moved out of the default set (step 2): shown on expand only. */
function RowDetails({ row }: { row: EvidenceView }) {
  return (
    <dl className="kv">
      <dt>Application</dt>
      <dd>{row.applicationSlug}</dd>
      <dt>Type</dt>
      <dd>{row.tags.evidenceType ?? '—'}</dd>
      <dt>Source</dt>
      <dd>{row.sourceSystem}</dd>
      <dt>Technology</dt>
      <dd>{row.tags.technology ?? '—'}</dd>
      <dt>Method</dt>
      <dd>{row.tags.collectionMethod ?? '—'}</dd>
      <dt>Ver</dt>
      <dd>{row.currentVersion}</dd>
    </dl>
  )
}

function clean(f: EvidenceQueryParams): EvidenceQueryParams {
  const out: EvidenceQueryParams = {}
  for (const [k, v] of Object.entries(f)) {
    if (v !== undefined && v !== '') (out as Record<string, unknown>)[k] = v
  }
  return out
}
