import { useMemo, useState } from 'react'
import { DataTable, Empty, Meter, Section, StatCard, StatusPill } from '../components/ui'

// ---- static demo data ------------------------------------------------------
// This page is a cosmetic workbench mock-up: no backend calls, no real filter/
// action wiring. All numbers/rows live here so they're easy to edit later.

interface ActionResultRow {
  evidenceKey: string
  control: string
  technology: string
  application: string
  verdict: string
  integrity: string
  collected: string
}

const ACTION_RESULT_ROWS: ActionResultRow[] = [
  {
    evidenceKey: 'UPI::ASST-14 — Container & Cloud Coverage v1',
    control: 'ASST-14 — Container & Cloud Coverage',
    technology: '—',
    application: 'UPI',
    verdict: 'Unassessed',
    integrity: 'verified',
    collected: '2026-09-23T08:47:06.554023+00:00',
  },
  {
    evidenceKey: 'Mobile Banking::ASST-15 — Mobile App Security v2',
    control: 'ASST-15 — Mobile App Security',
    technology: '—',
    application: 'Mobile Banking',
    verdict: 'Unassessed',
    integrity: 'verified',
    collected: '2026-09-23T08:47:06.554023+00:00',
  },
  {
    evidenceKey: 'UPI::ASST-15 — Mobile App Security v1',
    control: 'ASST-15 — Mobile App Security',
    technology: '—',
    application: 'UPI',
    verdict: 'Unassessed',
    integrity: 'verified',
    collected: '2026-09-23T08:47:06.554023+00:00',
  },
  {
    evidenceKey: 'Payments::ASST-16 — Privileged Tool Usage v1',
    control: 'ASST-16 — Privileged Tool Usage',
    technology: '—',
    application: 'Payments',
    verdict: 'Unassessed',
    integrity: 'verified',
    collected: '2026-09-23T08:47:06.554023+00:00',
  },
  {
    evidenceKey: 'Treasury::ASST-16 — Privileged Tool Usage v1',
    control: 'ASST-16 — Privileged Tool Usage',
    technology: '—',
    application: 'Treasury',
    verdict: 'Unassessed',
    integrity: 'verified',
    collected: '2026-09-23T08:47:06.554023+00:00',
  },
  {
    evidenceKey: 'Net Banking::ASST-17 — Sensitive Data Inventory v1',
    control: 'ASST-17 — Sensitive Data Inventory',
    technology: '—',
    application: 'Net Banking',
    verdict: 'Unassessed',
    integrity: 'verified',
    collected: '2026-09-23T08:47:06.554023+00:00',
  },
  {
    evidenceKey: 'Mobile Banking::ASST-17 — Sensitive Data Inventory v1',
    control: 'ASST-17 — Sensitive Data Inventory',
    technology: '—',
    application: 'Mobile Banking',
    verdict: 'Unassessed',
    integrity: 'verified',
    collected: '2026-09-23T08:47:06.554023+00:00',
  },
  {
    evidenceKey: 'Loan System::ASST-13 — Identity Lifecycle v1',
    control: 'ASST-13 — Identity Lifecycle',
    technology: '—',
    application: 'Loan System',
    verdict: 'Unassessed',
    integrity: 'verified',
    collected: '2026-09-23T08:47:06.552019+00:00',
  },
]

const WORKBENCH_STATS = {
  evidenceRecords: 606,
  reuseFactor: '1.0x',
  auditReadiness: '0.0%',
  openObservations: 0,
  readyForClosure: 0,
}

const ACTION_RESULT_TOTAL = 606
const ACTION_RESULT_PAGE_SIZE = 10
const ACTION_RESULT_TOTAL_PAGES = Math.ceil(ACTION_RESULT_TOTAL / ACTION_RESULT_PAGE_SIZE)

interface EvidenceGeneratedRow {
  evidenceId: string
  control: string
  application: string
  technology: string
  collected: string
  result: string
  status: string
}

const EVIDENCE_GENERATED_ROWS: EvidenceGeneratedRow[] = [
  {
    evidenceId: 'PQ-EVD-DEMO-DB-001',
    control: 'DB-001 — Database transport encryption (SSL/TLS enforced)',
    application: 'Core Banking Database',
    technology: 'PostgreSQL',
    collected: '2026-09-23 08:48 UTC',
    result: 'ssl · --- · off',
    status: 'Violation',
  },
  {
    evidenceId: 'PQ-EVD-DEMO-APP-001',
    control: 'APP-001 — Application source-code security scanning',
    application: 'Net Banking Application',
    technology: 'SonarQube',
    collected: '2026-09-23 08:48 UTC',
    result: 'SonarQube Project Count: 6',
    status: 'Satisfied',
  },
  {
    evidenceId: 'PQ-EVD-DEMO-OS-001',
    control: 'OS-001 — Operating-system baseline hardening',
    application: 'Payment Gateway Host',
    technology: 'Linux',
    collected: '2026-09-23 08:48 UTC',
    result: 'PermitRootLogin no · yum check-update: 0 pending updates',
    status: 'Satisfied',
  },
]

const EVIDENCE_REUSE_STATS = {
  reuseCount: 9,
  reuseFactor: '3.0x',
  frameworksCovered: 6,
  controlsCovered: 3,
  collectionEffortSaved: '24h',
}

interface EvidenceReuseRow {
  evidence: string
  framework: string
  reference: string
  control: string
  status: string
}

const EVIDENCE_REUSE_ROWS: EvidenceReuseRow[] = [
  { evidence: 'PQ-EVD-DEMO-DB-001', framework: 'PCI DSS', reference: 'Req 4.1', control: 'DB-001', status: 'Violation' },
  { evidence: 'PQ-EVD-DEMO-DB-001', framework: 'RBI C-SITE', reference: 'C-SITE 5.2', control: 'DB-001', status: 'Violation' },
  { evidence: 'PQ-EVD-DEMO-DB-001', framework: 'DB Baseline', reference: 'DBB-07', control: 'DB-001', status: 'Violation' },
  { evidence: 'PQ-EVD-DEMO-APP-001', framework: 'PCI DSS', reference: 'Req 6.3', control: 'APP-001', status: 'Satisfied' },
  { evidence: 'PQ-EVD-DEMO-APP-001', framework: 'DPSC', reference: 'DPSC 3.4', control: 'APP-001', status: 'Satisfied' },
  { evidence: 'PQ-EVD-DEMO-APP-001', framework: 'ITPP', reference: 'ITPP 8.1', control: 'APP-001', status: 'Satisfied' },
  { evidence: 'PQ-EVD-DEMO-OS-001', framework: 'RBI C-SITE', reference: 'C-SITE 4.7', control: 'OS-001', status: 'Satisfied' },
  { evidence: 'PQ-EVD-DEMO-OS-001', framework: 'OS Baseline', reference: 'OSB-12', control: 'OS-001', status: 'Satisfied' },
  { evidence: 'PQ-EVD-DEMO-OS-001', framework: 'ITPP', reference: 'ITPP 8.3', control: 'OS-001', status: 'Satisfied' },
]

const AUDIT_READINESS_STATS = {
  coveredControls: 6,
  totalControls: 9,
  readiness: '66.7%',
}

interface FrameworkReadinessRow {
  framework: string
  covered: number
  total: number
  pct: number
}

const AUDIT_READINESS_ROWS: FrameworkReadinessRow[] = [
  { framework: 'DB Baseline', covered: 0, total: 1, pct: 0 },
  { framework: 'DPSC', covered: 1, total: 1, pct: 100 },
  { framework: 'ITPP', covered: 2, total: 2, pct: 100 },
  { framework: 'OS Baseline', covered: 1, total: 1, pct: 100 },
  { framework: 'PCI DSS', covered: 1, total: 2, pct: 50 },
  { framework: 'RBI C-SITE', covered: 1, total: 2, pct: 50 },
]

const OPEN_OBSERVATIONS = [
  {
    observationId: 'OBS-DB-001-0001',
    framework: 'PCI DSS',
    control: 'DB-001',
    application: 'Core Banking Database',
    severity: 'High',
    finding: 'Database SSL/TLS is OFF — data in transit is not encrypted.',
    evidenceRef: 'PQ-EVD-DEMO-DB-001',
  },
]

const READY_FOR_CLOSURE_OBSERVATIONS = [
  {
    observation: 'OBS-OS-0007',
    evidenceUsed: 'PQ-EVD-DEMO-OS-001',
    controlCovered: 'OS-001 — Operating-system baseline hardening',
    frameworkCovered: 'OS Baseline',
    status: 'READY FOR CLOSURE',
  },
]

// ---- filter state (cosmetic; only Application/Control/Technology narrow the
// Action Result table client-side — Framework/Status/dates/maker-checker are
// display-only, same as the rest of the workbench) ---------------------------

interface Filters {
  application: string
  framework: string
  control: string
  technology: string
  status: string
  dateFrom: string
  dateTo: string
  makerChecker: boolean
}

const EMPTY_FILTERS: Filters = {
  application: '',
  framework: '',
  control: '',
  technology: '',
  status: '',
  dateFrom: '',
  dateTo: '',
  makerChecker: false,
}

const WORKBENCH_ACTIONS = [
  'Refresh evidence',
  'Run reuse analysis',
  'Validate completeness',
  'Refresh audit readiness',
  'Generate observations',
  'Check closure eligibility',
] as const

export function EvidenceReuse() {
  const [filters, setFilters] = useState<Filters>(EMPTY_FILTERS)
  const [status, setStatus] = useState('Ready.')
  const [page, setPage] = useState(1)

  function setFilter<K extends keyof Filters>(key: K, value: Filters[K]) {
    setFilters((f) => ({ ...f, [key]: value }))
  }

  function runAction(label: string) {
    setStatus(`Ran "${label}" — showing the latest static workbench result.`)
  }

  const rows = useMemo(() => {
    const app = filters.application.trim().toLowerCase()
    const control = filters.control.trim().toLowerCase()
    const tech = filters.technology.trim().toLowerCase()
    return ACTION_RESULT_ROWS.filter(
      (r) =>
        (!app || r.application.toLowerCase().includes(app)) &&
        (!control || r.control.toLowerCase().includes(control) || r.evidenceKey.toLowerCase().includes(control)) &&
        (!tech || r.technology.toLowerCase().includes(tech)),
    )
  }, [filters.application, filters.control, filters.technology])

  return (
    <div className="page reuse-page">
      <h1>Evidence Reuse</h1>
      <p className="muted">
        Reuse evidence already held instead of re-collecting it: filter what's on hand, run the reuse
        and completeness checks, and see which observations are open or ready to close.
      </p>

      <Section title="Workbench">
        <div className="filter-row">
          <label>
            Application
            <input
              aria-label="Application"
              placeholder="any"
              value={filters.application}
              onChange={(e) => setFilter('application', e.target.value)}
            />
          </label>
          <label>
            Framework
            <input
              aria-label="Framework"
              placeholder="any"
              value={filters.framework}
              onChange={(e) => setFilter('framework', e.target.value)}
            />
          </label>
          <label>
            Control
            <input
              aria-label="Control"
              placeholder="e.g. DB-001"
              value={filters.control}
              onChange={(e) => setFilter('control', e.target.value)}
            />
          </label>
          <label>
            Technology
            <input
              aria-label="Technology"
              placeholder="any"
              value={filters.technology}
              onChange={(e) => setFilter('technology', e.target.value)}
            />
          </label>
          <label>
            Status
            <input
              aria-label="Status"
              placeholder="any"
              value={filters.status}
              onChange={(e) => setFilter('status', e.target.value)}
            />
          </label>
          <label>
            Date from
            <input
              aria-label="Date from"
              type="date"
              value={filters.dateFrom}
              onChange={(e) => setFilter('dateFrom', e.target.value)}
            />
          </label>
          <label>
            Date to
            <input
              aria-label="Date to"
              type="date"
              value={filters.dateTo}
              onChange={(e) => setFilter('dateTo', e.target.value)}
            />
          </label>
          <label className="checkbox">
            <input
              type="checkbox"
              aria-label="Require maker-checker approval"
              checked={filters.makerChecker}
              onChange={(e) => setFilter('makerChecker', e.target.checked)}
            />
            Require maker-checker approval
          </label>
        </div>

        <div className="row-actions">
          {WORKBENCH_ACTIONS.map((label) => {
            const tone =
              label === 'Generate observations' ? 'warn' : label === 'Check closure eligibility' ? 'ok' : undefined
            return (
              <button
                key={label}
                onClick={() => runAction(label)}
                style={tone ? { borderColor: `var(--${tone})`, color: `var(--${tone})` } : undefined}
              >
                {label}
              </button>
            )
          })}
        </div>

        <p className="muted small">{status}</p>

        <div className="stat-grid">
          <StatCard label="Evidence Records" value={WORKBENCH_STATS.evidenceRecords} />
          <StatCard label="Reuse Factor" value={WORKBENCH_STATS.reuseFactor} />
          <StatCard label="Audit Readiness" value={WORKBENCH_STATS.auditReadiness} />
          <StatCard label="Open Observations" value={WORKBENCH_STATS.openObservations} />
          <StatCard label="Ready for Closure" value={WORKBENCH_STATS.readyForClosure} />
        </div>

        <div className="section-head">
          <h2>Action Result</h2>
          <div className="pager">
            <label>
              Rows
              <select aria-label="Rows per page" defaultValue={ACTION_RESULT_PAGE_SIZE}>
                <option value={10}>10</option>
                <option value={25}>25</option>
                <option value={50}>50</option>
              </select>
            </label>
            <button disabled={page <= 1} onClick={() => setPage((p) => Math.max(1, p - 1))}>
              ‹ Prev
            </button>
            <span>
              Showing {rows.length === 0 ? 0 : (page - 1) * ACTION_RESULT_PAGE_SIZE + 1}–
              {Math.min(page * ACTION_RESULT_PAGE_SIZE, ACTION_RESULT_TOTAL)} of {ACTION_RESULT_TOTAL} records · Page{' '}
              {page} of {ACTION_RESULT_TOTAL_PAGES}
            </span>
            <button disabled={page >= ACTION_RESULT_TOTAL_PAGES} onClick={() => setPage((p) => p + 1)}>
              Next ›
            </button>
          </div>
        </div>
        {rows.length === 0 ? (
          <Empty message="No evidence matches these filters." />
        ) : (
          <DataTable
            rows={rows}
            rowKey={(r) => r.evidenceKey}
            columns={[
              { header: 'Evidence Key', cell: (r) => r.evidenceKey },
              { header: 'Control', cell: (r) => r.control },
              { header: 'Technology', cell: (r) => r.technology },
              { header: 'Application', cell: (r) => r.application },
              { header: 'Verdict', cell: (r) => <StatusPill status={r.verdict} tone="bad" /> },
              { header: 'Integrity', cell: (r) => <StatusPill status={r.integrity} tone="ok" /> },
              { header: 'Collected', cell: (r) => <code>{r.collected}</code> },
            ]}
          />
        )}
      </Section>

      <Section title="1 · Evidence Generated">
        <p className="muted">What did each predefined query produce, and does it satisfy its control?</p>
        <DataTable
          rows={EVIDENCE_GENERATED_ROWS}
          rowKey={(r) => r.evidenceId}
          columns={[
            { header: 'Evidence ID', cell: (r) => <code>{r.evidenceId}</code> },
            { header: 'Control', cell: (r) => r.control },
            { header: 'Application', cell: (r) => r.application },
            { header: 'Technology', cell: (r) => r.technology },
            { header: 'Collected', cell: (r) => r.collected },
            { header: 'Result', cell: (r) => <code>{r.result}</code> },
            { header: 'Status', cell: (r) => <StatusPill status={r.status} tone={r.status === 'Violation' ? 'bad' : 'ok'} /> },
          ]}
        />
      </Section>

      <Section title="2 · Evidence Reuse">
        <p className="muted">How many framework obligations does this evidence satisfy without re-collection?</p>
        <div className="stat-grid">
          <StatCard label="Reuse Count" value={EVIDENCE_REUSE_STATS.reuseCount} />
          <StatCard label="Reuse Factor" value={EVIDENCE_REUSE_STATS.reuseFactor} />
          <StatCard label="Frameworks Covered" value={EVIDENCE_REUSE_STATS.frameworksCovered} />
          <StatCard label="Controls Covered" value={EVIDENCE_REUSE_STATS.controlsCovered} />
          <StatCard label="Collection Effort Saved" value={EVIDENCE_REUSE_STATS.collectionEffortSaved} />
        </div>
        <p className="muted small">3 evidence records satisfy 9 framework obligations — 6 manual collections avoided.</p>
        <DataTable
          rows={EVIDENCE_REUSE_ROWS}
          rowKey={(r) => `${r.evidence}-${r.framework}-${r.reference}`}
          columns={[
            { header: 'Evidence', cell: (r) => <code>{r.evidence}</code> },
            { header: 'Framework', cell: (r) => r.framework },
            { header: 'Reference', cell: (r) => r.reference },
            { header: 'Control', cell: (r) => r.control },
            { header: 'Status', cell: (r) => <StatusPill status={r.status} tone={r.status === 'Violation' ? 'bad' : 'ok'} /> },
          ]}
        />
        <p className="muted small">Showing 1–{EVIDENCE_REUSE_ROWS.length} of {EVIDENCE_REUSE_ROWS.length} records</p>
      </Section>

      <Section title="3 · Audit Readiness">
        <p className="muted">
          Across the in-scope frameworks, how many control obligations are covered? (drill: Framework →
          Control → Evidence)
        </p>
        <div className="stat-grid">
          <StatCard label="Covered Controls" value={AUDIT_READINESS_STATS.coveredControls} />
          <StatCard label="Total Controls" value={AUDIT_READINESS_STATS.totalControls} />
          <StatCard label="Readiness" value={AUDIT_READINESS_STATS.readiness} />
        </div>
        {AUDIT_READINESS_ROWS.map((r) => (
          <div key={r.framework} className="row-actions" style={{ justifyContent: 'space-between' }}>
            <strong>{r.framework}</strong>
            <div style={{ flex: 1, maxWidth: 420 }}>
              <Meter
                value={r.covered}
                max={r.total}
                tone={r.pct === 100 ? 'ok' : 'accent'}
                label={`${r.covered}/${r.total} (${r.pct.toFixed(1)}%)`}
              />
            </div>
          </div>
        ))}
      </Section>

      <Section title="4 · Observations">
        <p className="muted">
          Violations create observations automatically; satisfying evidence makes open observations ready
          for closure.
        </p>

        <h3>Open — auto-created from violations ({OPEN_OBSERVATIONS.length})</h3>
        <DataTable
          rows={OPEN_OBSERVATIONS}
          rowKey={(r) => r.observationId}
          columns={[
            { header: 'Observation ID', cell: (r) => r.observationId },
            { header: 'Framework', cell: (r) => r.framework },
            { header: 'Control', cell: (r) => r.control },
            { header: 'Application', cell: (r) => r.application },
            { header: 'Severity', cell: (r) => <StatusPill status={r.severity} tone="bad" /> },
            { header: 'Finding', cell: (r) => r.finding },
            { header: 'Evidence Ref', cell: (r) => <code>{r.evidenceRef}</code> },
          ]}
        />

        <h3>Ready for closure — satisfied by new evidence ({READY_FOR_CLOSURE_OBSERVATIONS.length})</h3>
        <DataTable
          rows={READY_FOR_CLOSURE_OBSERVATIONS}
          rowKey={(r) => r.observation}
          columns={[
            { header: 'Observation', cell: (r) => r.observation },
            { header: 'Evidence Used', cell: (r) => <code>{r.evidenceUsed}</code> },
            { header: 'Control Covered', cell: (r) => r.controlCovered },
            { header: 'Framework Covered', cell: (r) => r.frameworkCovered },
            { header: 'Status', cell: (r) => <StatusPill status={r.status} tone="ok" /> },
          ]}
        />
      </Section>
    </div>
  )
}
