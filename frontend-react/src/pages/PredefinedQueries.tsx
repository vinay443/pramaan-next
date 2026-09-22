import { useMemo, useState, type FormEvent } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { listApplications, listPredefinedQueries, runAllPredefinedQueries, runPredefinedQuery } from '../api/endpoints'
import type { PredefinedQueryItem, PredefinedQueryRunResult, PredefinedQueryRunSummary } from '../api/types'
import { useAsync } from '../hooks/useAsync'
import { DataTable, Empty, ErrorNote, Loading, Meter, Section, StatCard, StatusPill } from '../components/ui'

const PAGE_SIZE_OPTIONS = [10, 25, 50] as const

/** Technology values the catalogue cannot resolve yet — kept out of the executable UI
 *  (data-quality flag, not a UI bug) until a real technology mapping lands. */
const UNRESOLVED_TECHNOLOGIES = new Set(['Unknown'])

/**
 * Recommended `Type` values, derived from the catalogue's evidenceType/command shape.
 * The catalogue itself has no `type` field yet (that's a data-model change out of
 * this pass's scope), so this is a display-only classification — nothing is persisted.
 */
const RECOMMENDED_TYPES = [
  'SQL Query',
  'Shell Command',
  'Configuration Check',
  'API Check',
  'File/Artifact Check',
  'Agent Script',
] as const

const SQL_LEAD = /^\s*(SELECT|SHOW|INSERT|UPDATE|DELETE)\b/i
const API_LEAD = /^\s*(curl|GET |POST |PUT )/i
const SCAN_TOOL = /\b(trivy|gitleaks|dependency-check)\b/i

function deriveType(q: PredefinedQueryItem): (typeof RECOMMENDED_TYPES)[number] {
  const cmd = q.command ?? ''
  const et = q.evidenceType ?? ''
  if (SQL_LEAD.test(cmd)) return 'SQL Query'
  if (et === 'Scan Report' || SCAN_TOOL.test(cmd)) return 'Agent Script'
  if (API_LEAD.test(cmd) || et === 'API Output' || et === 'Search Output') return 'API Check'
  if (et === 'Configuration File' || et === 'Configuration Export' || et === 'Certificate Output') {
    return 'File/Artifact Check'
  }
  if (['Kubernetes', 'OpenShift', 'MongoDB', 'Aerospike', 'Redis'].includes(q.technology)) {
    return 'Configuration Check'
  }
  return 'Shell Command'
}

function CopyButton({ text }: { text: string }) {
  const [copied, setCopied] = useState(false)
  return (
    <button
      type="button"
      className={copied ? 'copy-btn copied' : 'copy-btn'}
      title="Copy command"
      onClick={async (e) => {
        e.stopPropagation()
        try {
          await navigator.clipboard.writeText(text)
          setCopied(true)
          setTimeout(() => setCopied(false), 1500)
        } catch {
          // clipboard unavailable (e.g. insecure context) — no-op, user can still select the text
        }
      }}
    >
      {copied ? 'Copied' : 'Copy'}
    </button>
  )
}

type SortField = 'controlId' | 'technology' | 'controlName' | 'ecsStatus'
type SortOrder = 'asc' | 'desc'

const SORT_LABELS: Record<SortField, string> = {
  controlId: 'Control ID',
  technology: 'Technology',
  controlName: 'Control Name',
  ecsStatus: 'ECS Status',
}

function sortValue(item: PredefinedQueryItem, field: SortField): string {
  switch (field) {
    case 'controlId':
      return item.controlId
    case 'technology':
      return item.technology
    case 'controlName':
      return item.controlName
    case 'ecsStatus':
      return item.runtimeStatus
  }
}

interface FilterState {
  technology: string
  framework: string
  search: string
  sortBy: SortField
  order: SortOrder
}

const DEFAULT_FILTERS: FilterState = {
  technology: '',
  framework: '',
  search: '',
  sortBy: 'controlId',
  order: 'asc',
}

const DEFAULT_PAGE_SIZE: (typeof PAGE_SIZE_OPTIONS)[number] = 25

const SORT_FIELDS = Object.keys(SORT_LABELS) as SortField[]

function isDefaultFilters(f: FilterState): boolean {
  return (
    f.technology === DEFAULT_FILTERS.technology &&
    f.framework === DEFAULT_FILTERS.framework &&
    f.search === DEFAULT_FILTERS.search &&
    f.sortBy === DEFAULT_FILTERS.sortBy &&
    f.order === DEFAULT_FILTERS.order
  )
}

/** Reads filter state out of the URL, falling back to DEFAULT_FILTERS for any missing/invalid key. */
function filtersFromParams(params: URLSearchParams): FilterState {
  const sortBy = params.get('sortBy')
  const order = params.get('order')
  return {
    technology: params.get('technology') ?? DEFAULT_FILTERS.technology,
    framework: params.get('framework') ?? DEFAULT_FILTERS.framework,
    search: params.get('search') ?? DEFAULT_FILTERS.search,
    sortBy: sortBy && (SORT_FIELDS as string[]).includes(sortBy) ? (sortBy as SortField) : DEFAULT_FILTERS.sortBy,
    order: order === 'asc' || order === 'desc' ? order : DEFAULT_FILTERS.order,
  }
}

function pageFromParams(params: URLSearchParams): number {
  const n = Number(params.get('page'))
  return Number.isInteger(n) && n > 0 ? n : 0
}

function pageSizeFromParams(params: URLSearchParams): (typeof PAGE_SIZE_OPTIONS)[number] {
  const n = Number(params.get('pageSize'))
  return (PAGE_SIZE_OPTIONS as readonly number[]).includes(n) ? (n as (typeof PAGE_SIZE_OPTIONS)[number]) : DEFAULT_PAGE_SIZE
}

const TABS = ['Query Catalog', 'Execution History', 'Manual Controls'] as const
type Tab = (typeof TABS)[number]

export function PredefinedQueries() {
  const navigate = useNavigate()
  const [tab, setTab] = useState<Tab>('Query Catalog')
  const [searchParams, setSearchParams] = useSearchParams()

  // Draft mirrors what's in the filter controls; applied is what's actually in
  // effect. Nothing here re-filters until Apply is clicked (or the form submitted).
  // Both — plus page/pageSize — are seeded from the URL on mount so the list
  // state survives navigating away (e.g. to a control's detail page) and back.
  const [draft, setDraft] = useState<FilterState>(() => filtersFromParams(searchParams))
  const [applied, setApplied] = useState<FilterState>(() => filtersFromParams(searchParams))
  const [applicationSlug, setApplicationSlug] = useState('')
  const [page, setPage] = useState(() => pageFromParams(searchParams))
  const [pageSize, setPageSize] = useState<(typeof PAGE_SIZE_OPTIONS)[number]>(() => pageSizeFromParams(searchParams))

  /** Writes the given list state into the URL (replacing, not pushing, history). Keys at their
   *  default value are omitted so the URL — and Clear filters — stay clean. */
  function writeParams(next: { filters: FilterState; page: number; pageSize: (typeof PAGE_SIZE_OPTIONS)[number] }) {
    const params: Record<string, string> = {}
    if (next.filters.technology) params.technology = next.filters.technology
    if (next.filters.framework) params.framework = next.filters.framework
    if (next.filters.search) params.search = next.filters.search
    if (next.filters.sortBy !== DEFAULT_FILTERS.sortBy) params.sortBy = next.filters.sortBy
    if (next.filters.order !== DEFAULT_FILTERS.order) params.order = next.filters.order
    if (next.page > 0) params.page = String(next.page)
    if (next.pageSize !== DEFAULT_PAGE_SIZE) params.pageSize = String(next.pageSize)
    setSearchParams(params, { replace: true })
  }

  const serverFilters = useMemo(
    () => ({
      technology: applied.technology || undefined,
      framework: applied.framework || undefined,
    }),
    [applied.technology, applied.framework],
  )
  const catalog = useAsync(() => listPredefinedQueries(serverFilters), [JSON.stringify(serverFilters)])
  const apps = useAsync(() => listApplications(), [])
  const onboarded = (apps.data ?? []).filter((a) => a.active)

  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string>()
  const [summary, setSummary] = useState<PredefinedQueryRunSummary>()
  const [lastRun, setLastRun] = useState<PredefinedQueryRunResult>()

  const resolvedItems = useMemo(
    () => (catalog.data?.items ?? []).filter((q) => !UNRESOLVED_TECHNOLOGIES.has(q.technology)),
    [catalog.data],
  )
  const technologies = useMemo(
    () => (catalog.data?.technologies ?? []).filter((t) => !UNRESOLVED_TECHNOLOGIES.has(t)),
    [catalog.data],
  )
  const unsupportedTechCount = useMemo(
    () => (catalog.data?.items ?? []).filter((q) => UNRESOLVED_TECHNOLOGIES.has(q.technology)).length,
    [catalog.data],
  )
  const controlsWithQueries = useMemo(() => resolvedItems.filter((i) => !!i.command).length, [resolvedItems])
  const manualControls = resolvedItems.length - controlsWithQueries

  const searchTerm = applied.search.trim().toLowerCase()
  const filteredItems = useMemo(() => {
    let items = resolvedItems
    if (searchTerm) {
      items = items.filter(
        (item) =>
          item.controlId.toLowerCase().includes(searchTerm) ||
          item.controlName.toLowerCase().includes(searchTerm) ||
          item.technology.toLowerCase().includes(searchTerm) ||
          item.command.toLowerCase().includes(searchTerm) ||
          item.frameworks.some((f) => f.toLowerCase().includes(searchTerm)),
      )
    }
    const sorted = [...items].sort((a, b) => {
      const cmp = sortValue(a, applied.sortBy).localeCompare(sortValue(b, applied.sortBy))
      return applied.order === 'asc' ? cmp : -cmp
    })
    return sorted
  }, [resolvedItems, searchTerm, applied.sortBy, applied.order])

  const totalPages = Math.max(1, Math.ceil(filteredItems.length / pageSize))
  const clampedPage = Math.min(page, totalPages - 1)
  const rangeStart = filteredItems.length === 0 ? 0 : clampedPage * pageSize + 1
  const rangeEnd = Math.min(filteredItems.length, clampedPage * pageSize + pageSize)
  const pageItems = filteredItems.slice(clampedPage * pageSize, clampedPage * pageSize + pageSize)

  const count = resolvedItems.length

  function applyFilters(e?: FormEvent) {
    e?.preventDefault()
    setApplied(draft)
    setPage(0)
    writeParams({ filters: draft, page: 0, pageSize })
  }

  function clearFilters() {
    setDraft(DEFAULT_FILTERS)
    setApplied(DEFAULT_FILTERS)
    setPage(0)
    setSearchParams({}, { replace: true })
  }

  function goToPage(next: number) {
    setPage(next)
    writeParams({ filters: applied, page: next, pageSize })
  }

  function changePageSize(next: (typeof PAGE_SIZE_OPTIONS)[number]) {
    setPageSize(next)
    setPage(0)
    writeParams({ filters: applied, page: 0, pageSize: next })
  }

  const canClearFilters = !isDefaultFilters(draft) || !isDefaultFilters(applied) || page !== 0

  async function runOne(controlId: string) {
    setBusy(true)
    setError(undefined)
    setSummary(undefined)
    try {
      setLastRun(await runPredefinedQuery(controlId, applicationSlug || undefined))
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e))
    } finally {
      setBusy(false)
    }
  }

  async function runAll() {
    setBusy(true)
    setError(undefined)
    setLastRun(undefined)
    setSummary(undefined)
    try {
      setSummary(await runAllPredefinedQueries({ ...serverFilters, applicationSlug: applicationSlug || undefined }))
      catalog.reload()
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e))
    } finally {
      setBusy(false)
    }
  }

  const commandFor = (controlId: string) => catalog.data?.items.find((i) => i.controlId === controlId)?.command

  return (
    <div className="page">
      <h1>Predefined Queries</h1>
      <p className="muted">
        Technical control catalogue (<code>GET /api/v1/predefined-queries</code>). Running a query executes
        it in <strong>simulated</strong> mode and produces an evidence record via the canonical ingestion
        path — tagged <code>collectionMethod=predefined-query</code>.
      </p>

      {catalog.data ? (
        <div className="stat-grid">
          <StatCard label="Total Controls" value={resolvedItems.length} />
          <StatCard label="Predefined Queries" value={controlsWithQueries} />
          <StatCard label="Manual Controls" value={manualControls} />
          <StatCard label="Frameworks Covered" value={catalog.data.frameworks.length} />
          <StatCard label="Unsupported Tech" value={unsupportedTechCount} hint="excluded from the catalogue below" />
        </div>
      ) : null}

      <Section
        title="Filters"
        actions={
          <button className="primary" onClick={runAll} disabled={busy || count === 0}>
            {busy ? 'Running…' : `Run all${count ? ` (${count})` : ''}`}
          </button>
        }
      >
        <form className="filter-row" onSubmit={applyFilters}>
          <label>
            Technology
            <select
              aria-label="Technology"
              value={draft.technology}
              onChange={(e) => setDraft((d) => ({ ...d, technology: e.target.value }))}
            >
              <option value="">(any)</option>
              {technologies.map((t) => (
                <option key={t}>{t}</option>
              ))}
            </select>
          </label>
          <label>
            Framework
            <select
              aria-label="Framework"
              value={draft.framework}
              onChange={(e) => setDraft((d) => ({ ...d, framework: e.target.value }))}
            >
              <option value="">(any)</option>
              {(catalog.data?.frameworks ?? []).map((f) => (
                <option key={f}>{f}</option>
              ))}
            </select>
          </label>
          <label>
            Application
            <select
              aria-label="Application"
              value={applicationSlug}
              onChange={(e) => setApplicationSlug(e.target.value)}
            >
              <option value="">(first onboarded)</option>
              {onboarded.map((a) => (
                <option key={a.slug} value={a.slug}>
                  {a.name}
                </option>
              ))}
            </select>
          </label>
          <label>
            Search
            <input
              aria-label="Search catalogue"
              value={draft.search}
              onChange={(e) => setDraft((d) => ({ ...d, search: e.target.value }))}
              placeholder="Control ID, name, technology, command…"
            />
          </label>
          <label>
            Sort
            <select
              aria-label="Sort"
              value={draft.sortBy}
              onChange={(e) => setDraft((d) => ({ ...d, sortBy: e.target.value as SortField }))}
            >
              {(Object.keys(SORT_LABELS) as SortField[]).map((f) => (
                <option key={f} value={f}>
                  {SORT_LABELS[f]}
                </option>
              ))}
            </select>
          </label>
          <label>
            Order
            <select
              aria-label="Order"
              value={draft.order}
              onChange={(e) => setDraft((d) => ({ ...d, order: e.target.value as SortOrder }))}
            >
              <option value="asc">Ascending</option>
              <option value="desc">Descending</option>
            </select>
          </label>
          <div className="filter-apply">
            <button type="submit" className="primary">
              Apply
            </button>
            {canClearFilters ? (
              <button type="button" className="ghost" onClick={clearFilters}>
                Clear filters
              </button>
            ) : null}
          </div>
        </form>

        {busy ? (
          <Meter value={0} indeterminate tone="accent" label={`Running${count ? ` ${count} controls` : ''}…`} />
        ) : null}
        {error ? <ErrorNote message={error} /> : null}
        {lastRun ? (
          <div className="answer">
            <p>
              <StatusPill status={lastRun.outcome} /> {lastRun.controlId} →{' '}
              <Link to={`/predefined-queries/${lastRun.controlId}`}>view details</Link>
            </p>
            {lastRun.error ? <ErrorNote message={lastRun.error} /> : null}
          </div>
        ) : null}
        {summary ? (
          <>
            <div className="stat-grid">
              <StatCard label="Received" value={summary.received} />
              <StatCard label="Ingested" value={summary.ingested} />
              <StatCard label="Duplicates" value={summary.duplicates} />
              <StatCard label="Failed" value={summary.failed} />
            </div>
            <p className="muted small">
              {summary.message} — application {summary.applicationSlug}, mode {summary.mode}
            </p>
            <DataTable
              rows={summary.results}
              rowKey={(r) => r.controlId}
              columns={[
                { header: 'Control', cell: (r) => r.controlId },
                { header: 'Command', cell: (r) => <code>{commandFor(r.controlId)}</code> },
                { header: 'Outcome', cell: (r) => <StatusPill status={r.outcome} /> },
                { header: 'Error', cell: (r) => (r.error ? r.error : '') },
              ]}
            />
          </>
        ) : null}
      </Section>

      <div className="row-actions">
        {TABS.map((t) => (
          <button key={t} className={t === tab ? 'primary' : undefined} onClick={() => setTab(t)}>
            {t}
          </button>
        ))}
      </div>

      {tab === 'Query Catalog' ? (
        <Section
          title={
            catalog.data
              ? `Catalogue — ${filteredItems.length}${filteredItems.length !== count ? ` of ${count}` : ''}`
              : 'Catalogue'
          }
          actions={
            catalog.data && filteredItems.length > 0 ? (
              <div className="pager">
                <span>
                  Showing {rangeStart}–{rangeEnd} of {filteredItems.length} controls
                </span>
                <label className="pager-page-size">
                  Per page
                  <select
                    aria-label="Rows per page"
                    value={pageSize}
                    onChange={(e) => changePageSize(Number(e.target.value) as (typeof PAGE_SIZE_OPTIONS)[number])}
                  >
                    {PAGE_SIZE_OPTIONS.map((n) => (
                      <option key={n} value={n}>
                        {n}
                      </option>
                    ))}
                  </select>
                </label>
                <button disabled={clampedPage === 0} onClick={() => goToPage(clampedPage - 1)}>
                  Prev
                </button>
                <span>
                  Page {clampedPage + 1} / {totalPages}
                </span>
                <button disabled={clampedPage + 1 >= totalPages} onClick={() => goToPage(clampedPage + 1)}>
                  Next
                </button>
              </div>
            ) : undefined
          }
        >
          {catalog.loading ? <Loading what="predefined queries" /> : null}
          {catalog.error ? <ErrorNote message={catalog.error} /> : null}
          {catalog.data ? (
            <DataTable
              className="pq-table"
              rows={pageItems}
              rowKey={(item) => item.controlId}
              columns={[
                { header: 'Control ID', className: 'col-id', cell: (item) => <code>{item.controlId}</code> },
                { header: 'Technology', className: 'col-tech', cell: (item) => item.technology },
                {
                  header: 'Control Name',
                  className: 'col-name',
                  cell: (item) => (
                    <span className="ellipsis-cell" title={item.controlName}>
                      {item.controlName}
                    </span>
                  ),
                },
                {
                  header: 'Query / Command',
                  className: 'col-cmd',
                  cell: (item) => (
                    <div className="cmd-cell">
                      <code title={item.command}>{item.command}</code>
                      <CopyButton text={item.command} />
                    </div>
                  ),
                },
                {
                  header: 'Framework',
                  className: 'col-fw',
                  cell: (item) => (
                    <div className="chip-row">
                      {item.frameworks.map((f) => (
                        <span key={f} className="tag">
                          {f}
                        </span>
                      ))}
                    </div>
                  ),
                },
                {
                  header: 'Type',
                  className: 'col-type',
                  cell: (item) => <span className="tag">{deriveType(item)}</span>,
                },
                {
                  header: 'ECS Status',
                  className: 'col-status',
                  cell: (item) => <StatusPill status={item.runtimeStatus} />,
                },
                {
                  header: '',
                  className: 'col-actions',
                  cell: (item) => (
                    <div className="row-actions">
                      <button type="button" onClick={() => navigate(`/predefined-queries/${item.controlId}`)}>
                        View
                      </button>
                      <button type="button" disabled={busy} onClick={() => runOne(item.controlId)}>
                        Run Query
                      </button>
                    </div>
                  ),
                },
              ]}
            />
          ) : null}
        </Section>
      ) : null}

      {tab === 'Execution History' ? (
        <Section title="Execution History">
          <Empty message="Execution history isn't wired up on this screen yet — each run's result is still available from a control's own detail page (Result / Audit Trail tabs)." />
        </Section>
      ) : null}

      {tab === 'Manual Controls' ? (
        <Section title="Manual Controls">
          <Empty message="No manual-control data source exists in the catalogue yet — every catalogued control currently ships with a predefined query." />
        </Section>
      ) : null}
    </div>
  )
}
