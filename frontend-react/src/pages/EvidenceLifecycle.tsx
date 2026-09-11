import { useMemo, useState } from 'react'
import { getEvidenceLifecycle, getEvidenceVersions, listEvidence, transitionEvidenceLifecycle } from '../api/endpoints'
import type { EvidenceLifecycleView, EvidenceQueryParams, EvidenceVersionView } from '../api/types'
import { useAsync } from '../hooks/useAsync'
import { DataTable, Empty, ErrorNote, Loading, Section, StatusPill } from '../components/ui'

const PAGE_SIZE = 20

/** Use Case 13 — portfolio-wide retention/archival/version-control view, as opposed
 *  to EvidenceDetail's single-record lifecycle panel. */
export function EvidenceLifecycle() {
  const [filters, setFilters] = useState<Pick<EvidenceQueryParams, 'applicationSlug' | 'framework' | 'controlId'>>({})
  const [page, setPage] = useState(0)
  const [selectedId, setSelectedId] = useState<string>()
  const [acting, setActing] = useState(false)
  const [actionError, setActionError] = useState<string>()

  const query = useMemo<EvidenceQueryParams>(
    () => ({ ...clean(filters), page, size: PAGE_SIZE }),
    [filters, page],
  )
  const list = useAsync(() => listEvidence(query), [JSON.stringify(query)])

  const detail = useAsync<{ lifecycle: EvidenceLifecycleView; versions: EvidenceVersionView[] } | undefined>(
    () =>
      selectedId
        ? Promise.all([getEvidenceLifecycle(selectedId), getEvidenceVersions(selectedId)]).then(
            ([lifecycle, versions]) => ({ lifecycle, versions }),
          )
        : Promise.resolve(undefined),
    [selectedId],
  )

  function update(field: keyof typeof filters, value: string) {
    setPage(0)
    setFilters((f) => ({ ...f, [field]: value || undefined }))
  }

  function select(id: string) {
    setActionError(undefined)
    setSelectedId(id)
  }

  async function archive() {
    if (!selectedId) return
    setActing(true)
    setActionError(undefined)
    try {
      await transitionEvidenceLifecycle(selectedId, 'RETIRE', 'ui', 'archived from Evidence Lifecycle')
      detail.reload()
      list.reload()
    } catch (e) {
      setActionError(e instanceof Error ? e.message : String(e))
    } finally {
      setActing(false)
    }
  }

  return (
    <div className="page">
      <h1>Evidence Lifecycle</h1>
      <p className="muted">Retention status, archival, and version history across all evidence.</p>

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
        </div>
      </Section>

      <Section
        title={`Evidence${list.data ? ` — ${list.data.totalItems}` : ''}`}
        actions={
          list.data && list.data.totalPages > 1 ? (
            <div className="pager">
              <button disabled={page === 0} onClick={() => setPage((p) => p - 1)}>
                Prev
              </button>
              <span>
                Page {page + 1} / {list.data.totalPages}
              </span>
              <button disabled={page + 1 >= list.data.totalPages} onClick={() => setPage((p) => p + 1)}>
                Next
              </button>
            </div>
          ) : undefined
        }
      >
        {list.loading ? <Loading what="evidence" /> : null}
        {list.error ? <ErrorNote message={list.error} /> : null}
        {list.data && list.data.items.length === 0 ? <Empty message="No evidence matches these filters." /> : null}
        {list.data && list.data.items.length > 0 ? (
          <DataTable
            rows={list.data.items}
            rowKey={(r) => r.evidenceId}
            onRowClick={(r) => select(r.evidenceId)}
            columns={[
              { header: 'Application', cell: (r) => r.applicationSlug },
              { header: 'Control', cell: (r) => r.controlId },
              { header: 'Framework', cell: (r) => r.framework },
              { header: 'Ver', cell: (r) => r.currentVersion, align: 'right' },
              { header: 'State', cell: (r) => <StatusPill status={r.lifecycleState ?? 'DRAFT'} /> },
              { header: 'Updated', cell: (r) => new Date(r.updatedAt).toLocaleDateString() },
              {
                header: '',
                cell: (r) => (
                  <button onClick={(e) => { e.stopPropagation(); select(r.evidenceId) }}>Details</button>
                ),
              },
            ]}
          />
        ) : null}
      </Section>

      {selectedId ? (
        <Section
          title="Retention & archival"
          actions={
            detail.data?.lifecycle.effectiveState === 'APPROVED' ? (
              <button disabled={acting} onClick={archive}>
                {acting ? 'Archiving…' : 'Archive'}
              </button>
            ) : undefined
          }
        >
          {detail.loading ? <Loading what="lifecycle" /> : null}
          {detail.error ? <ErrorNote message={detail.error} /> : null}
          {actionError ? <ErrorNote message={actionError} /> : null}
          {detail.data ? (
            <>
              <p>
                <StatusPill status={detail.data.lifecycle.effectiveState} />
                {detail.data.lifecycle.expired ? <StatusPill status="past retention" /> : null}
                <span className="muted small">
                  {' '}
                  {detail.data.lifecycle.applicationSlug} / {detail.data.lifecycle.controlId} · retention{' '}
                  {detail.data.lifecycle.retentionDays}d
                  {detail.data.lifecycle.ageDays != null ? ` · age ${detail.data.lifecycle.ageDays}d` : ''}
                  {detail.data.lifecycle.expiresAt
                    ? ` · expires ${new Date(detail.data.lifecycle.expiresAt).toLocaleDateString()}`
                    : ''}
                </span>
              </p>

              <h3>Audit trail</h3>
              <DataTable
                rows={detail.data.lifecycle.history}
                rowKey={(h) => `${h.occurredAt}-${h.action}`}
                columns={[
                  { header: 'When', cell: (h) => new Date(h.occurredAt).toLocaleString() },
                  { header: 'Action', cell: (h) => h.action },
                  { header: 'From→To', cell: (h) => `${h.fromState ?? '—'} → ${h.toState}` },
                  { header: 'Actor', cell: (h) => h.actor ?? '—' },
                  { header: 'Note', cell: (h) => h.note ?? '' },
                ]}
              />

              <h3>Version history</h3>
              <DataTable
                rows={detail.data.versions}
                rowKey={(v) => String(v.version)}
                columns={[
                  { header: 'Ver', cell: (v) => v.version },
                  { header: 'SHA-256', cell: (v) => <code>{v.sha256.slice(0, 20)}…</code> },
                  { header: 'Type', cell: (v) => v.contentType },
                  { header: 'Size', cell: (v) => `${v.sizeBytes} B` },
                  { header: 'Collected', cell: (v) => new Date(v.collectedAt).toLocaleString() },
                ]}
              />
            </>
          ) : null}
        </Section>
      ) : null}
    </div>
  )
}

function clean(f: Record<string, string | undefined>): Record<string, string> {
  const out: Record<string, string> = {}
  for (const [k, v] of Object.entries(f)) {
    if (v !== undefined && v !== '') out[k] = v
  }
  return out
}
