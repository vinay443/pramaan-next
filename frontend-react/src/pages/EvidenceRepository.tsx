import { useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { listEvidence } from '../api/endpoints'
import { onDataEvent } from '../api/events'
import type { EvidenceQueryParams } from '../api/types'
import { useAsync } from '../hooks/useAsync'
import { DataTable, Empty, ErrorNote, Loading, Section } from '../components/ui'

const PAGE_SIZE = 20

export function EvidenceRepository() {
  const navigate = useNavigate()
  const [filters, setFilters] = useState<EvidenceQueryParams>({})
  const [page, setPage] = useState(0)

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
          <DataTable
            rows={data.items}
            rowKey={(r) => r.evidenceId}
            onRowClick={(r) => navigate(`/evidence/${r.evidenceId}`)}
            columns={[
              { header: 'Name', cell: (r) => <code className="small">{r.tags.name ?? r.title ?? '—'}</code> },
              { header: 'Application', cell: (r) => r.applicationSlug },
              { header: 'Control', cell: (r) => r.controlId },
              {
                header: 'Frameworks',
                cell: (r) =>
                  (r.tags.frameworks ?? r.framework)
                    .split(',')
                    .map((f) => <span key={f} className="tag">{f}</span>),
              },
              { header: 'Type', cell: (r) => r.tags.evidenceType ?? '—' },
              { header: 'Source', cell: (r) => r.sourceSystem },
              { header: 'Technology', cell: (r) => r.tags.technology ?? '—' },
              { header: 'Method', cell: (r) => r.tags.collectionMethod ?? '—' },
              { header: 'Ver', cell: (r) => r.currentVersion, align: 'right' },
              { header: 'Collected', cell: (r) => (r.latest ? new Date(r.latest.collectedAt).toLocaleDateString() : '—') },
            ]}
          />
        ) : null}
      </Section>
    </div>
  )
}

function clean(f: EvidenceQueryParams): EvidenceQueryParams {
  const out: EvidenceQueryParams = {}
  for (const [k, v] of Object.entries(f)) {
    if (v !== undefined && v !== '') (out as Record<string, unknown>)[k] = v
  }
  return out
}
