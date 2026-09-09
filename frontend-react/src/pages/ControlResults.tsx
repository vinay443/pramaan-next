import { useMemo, useState } from 'react'
import { evaluateChecks, listCheckResults } from '../api/endpoints'
import type { CheckResultParams, EvaluationSummary } from '../api/types'
import { useAsync } from '../hooks/useAsync'
import { DataTable, Empty, ErrorNote, Loading, Section, StatCard, StatusPill } from '../components/ui'

const STATUSES = ['', 'PASS', 'WARNING', 'FAIL', 'NOT_APPLICABLE'] as const
const PAGE_SIZE = 100

export function ControlResults() {
  const [filters, setFilters] = useState<CheckResultParams>({})
  const [summary, setSummary] = useState<EvaluationSummary>()
  const [evaluating, setEvaluating] = useState(false)
  const [actionError, setActionError] = useState<string>()

  const query = useMemo<CheckResultParams>(
    () => ({ ...clean(filters), page: 0, size: PAGE_SIZE }),
    [filters],
  )
  const { data, loading, error, reload } = useAsync(
    () => listCheckResults(query),
    [JSON.stringify(query), summary?.evaluatedAt ?? ''],
  )

  const counts = useMemo(() => {
    const c: Record<string, number> = { PASS: 0, WARNING: 0, FAIL: 0, NOT_APPLICABLE: 0 }
    ;(data?.items ?? []).forEach((r) => (c[r.status] = (c[r.status] ?? 0) + 1))
    return c
  }, [data])

  function update(field: keyof CheckResultParams, value: string) {
    setFilters((f) => ({ ...f, [field]: value || undefined }))
  }

  async function reevaluate() {
    setEvaluating(true)
    setActionError(undefined)
    try {
      setSummary(await evaluateChecks(clean(filters)))
      reload()
    } catch (e) {
      setActionError(e instanceof Error ? e.message : String(e))
    } finally {
      setEvaluating(false)
    }
  }

  return (
    <div className="page">
      <h1>Control Results</h1>
      <p className="muted">
        Deterministic rule evaluation over stored evidence (no LLM). Backed by{' '}
        <code>GET /api/v1/check-results</code>.
      </p>

      <div className="stat-grid">
        <StatCard label="PASS" value={counts.PASS} />
        <StatCard label="WARNING" value={counts.WARNING} />
        <StatCard label="FAIL" value={counts.FAIL} />
        <StatCard label="N/A" value={counts.NOT_APPLICABLE} />
      </div>

      <Section
        title="Filters"
        actions={
          <button onClick={reevaluate} disabled={evaluating}>
            {evaluating ? 'Evaluating…' : 'Re-evaluate'}
          </button>
        }
      >
        <div className="filter-row">
          <label>
            Application
            <input
              aria-label="Application"
              value={filters.applicationSlug ?? ''}
              onChange={(e) => update('applicationSlug', e.target.value)}
              placeholder="payments"
            />
          </label>
          <label>
            Framework
            <input
              aria-label="Framework"
              value={filters.framework ?? ''}
              onChange={(e) => update('framework', e.target.value)}
              placeholder="C-SITE"
            />
          </label>
          <label>
            Control
            <input
              aria-label="Control"
              value={filters.controlId ?? ''}
              onChange={(e) => update('controlId', e.target.value)}
              placeholder="TLS-CERT-EXPIRY"
            />
          </label>
          <label>
            Status
            <select
              aria-label="Status"
              value={filters.status ?? ''}
              onChange={(e) => update('status', e.target.value)}
            >
              {STATUSES.map((s) => (
                <option key={s} value={s}>
                  {s || '(any)'}
                </option>
              ))}
            </select>
          </label>
        </div>
      </Section>

      {actionError ? <ErrorNote message={actionError} /> : null}
      {summary ? (
        <Section title="Last evaluation">
          <p className="muted small">
            {summary.evidenceEvaluated} evidence evaluated · {summary.resultsWritten} results ·{' '}
            {new Date(summary.evaluatedAt).toLocaleString()}
          </p>
          <div className="filter-row">
            {Object.entries(summary.byStatus).map(([k, v]) => (
              <StatusPill key={k} status={`${k}: ${v}`} />
            ))}
          </div>
        </Section>
      ) : null}

      <Section title={`Results${data ? ` — ${data.totalItems}` : ''}`}>
        {loading ? <Loading what="control results" /> : null}
        {error ? <ErrorNote message={error} /> : null}
        {data && data.items.length === 0 ? <Empty message="No control results match these filters." /> : null}
        {data && data.items.length > 0 ? (
          <DataTable
            rows={data.items}
            rowKey={(r) => r.id}
            columns={[
              { header: 'Status', cell: (r) => <StatusPill status={r.status} /> },
              { header: 'Application', cell: (r) => r.applicationSlug },
              { header: 'Framework', cell: (r) => r.framework },
              { header: 'Control', cell: (r) => r.controlId },
              { header: 'Check', cell: (r) => r.checkId },
              { header: 'Observed', cell: (r) => r.observed },
              { header: 'Expected', cell: (r) => r.expected },
              { header: 'Detail', cell: (r) => r.detail },
            ]}
          />
        ) : null}
      </Section>
    </div>
  )
}

function clean(f: CheckResultParams): CheckResultParams {
  const out: CheckResultParams = {}
  for (const [k, v] of Object.entries(f)) {
    if (v !== undefined && v !== '') (out as Record<string, unknown>)[k] = v
  }
  return out
}
