import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { Dashboard } from './Dashboard'
import { renderOffline } from '../test/render'
import { PERSONA_STORAGE_KEY } from './PersonaLogin'
import { getDataSource } from '../api/dataSource'

afterEach(() => {
  vi.unstubAllGlobals()
  cleanup()
})

describe('Dashboard', () => {
  it('renders repository totals, hash integrity and recent runs from mock data', async () => {
    renderOffline(<Dashboard />)

    expect(screen.getByRole('heading', { name: 'Dashboard' })).toBeInTheDocument()

    // integrity panel resolves from the mock evidence dashboard (async fallback)
    const integrity = await screen.findByText('Hash integrity')
    const card = integrity.closest('.card') as HTMLElement
    expect(await within(card).findByText(/current versions verified intact/)).toBeInTheDocument()
    expect(within(card).getByText('PASS')).toBeInTheDocument()

    // recent scheduler runs table shows a mock run id
    expect(await screen.findByText('run-001')).toBeInTheDocument()
    expect(screen.getAllByText('COMPLETED').length).toBeGreaterThan(0)

    // dashboard trend charts render from the mock trend report
    expect(await screen.findByText('Evidence volume over time')).toBeInTheDocument()
    expect(screen.getByLabelText('evidence record count per snapshot')).toBeInTheDocument()
    expect(screen.getByLabelText('hash integrity pass rate per snapshot')).toBeInTheDocument()
  })
})

describe('Dashboard — CIO', () => {
  afterEach(() => sessionStorage.removeItem(PERSONA_STORAGE_KEY))

  it('National & Enterprise tab shows the org compliance trend, region RAG, cuts and top risks (mock)', async () => {
    sessionStorage.setItem(PERSONA_STORAGE_KEY, 'CIO')
    renderOffline(<Dashboard />)

    fireEvent.click(await screen.findByRole('tab', { name: 'National & Enterprise' }))

    expect(await screen.findByText('Org-wide compliance % trend')).toBeInTheDocument()
    expect(await screen.findByLabelText('org-wide compliance percentage per snapshot')).toBeInTheDocument()
    expect(await screen.findByText('By business unit')).toBeInTheDocument()
    expect(screen.getByText('By criticality')).toBeInTheDocument()
    expect(screen.getByText(/Top risks/)).toBeInTheDocument()
    expect(screen.getByText('Regions ranked by gap to national average (furthest-behind first)')).toBeInTheDocument()
  })
})

describe('Dashboard — Auditor', () => {
  afterEach(() => sessionStorage.removeItem(PERSONA_STORAGE_KEY))

  it('Closure & Trend tab shows closure metrics and the org compliance trend, no business cuts (mock)', async () => {
    sessionStorage.setItem(PERSONA_STORAGE_KEY, 'AUDITOR')
    renderOffline(<Dashboard />)

    fireEvent.click(await screen.findByRole('tab', { name: 'Closure & Trend' }))

    expect(await screen.findByText('Approvals')).toBeInTheDocument()
    expect(screen.getByText('Avg days to approve')).toBeInTheDocument()
    expect(screen.getByText('Rejections')).toBeInTheDocument()
    expect(await screen.findByLabelText('org-wide compliance percentage per snapshot')).toBeInTheDocument()
    expect(screen.queryByText('By business unit')).not.toBeInTheDocument()
  })
})

describe('Dashboard — App Owner', () => {
  afterEach(() => sessionStorage.removeItem(PERSONA_STORAGE_KEY))

  it('renders the four App Owner tabs with the POC-matched header (mock)', async () => {
    sessionStorage.setItem(PERSONA_STORAGE_KEY, 'APP')
    renderOffline(<Dashboard />)

    expect(await screen.findByRole('heading', { name: 'Application Owner Dashboard' })).toBeInTheDocument()
    expect(screen.getByText('Governance posture and prioritized actions')).toBeInTheDocument()

    const tablist = await screen.findByRole('tablist', { name: 'Dashboard sections' })
    expect(within(tablist).getAllByRole('tab').map((t) => t.textContent)).toEqual([
      'Evidence',
      'Frameworks',
      'Applications',
      'Audit Readiness',
    ])
  })

  const kpis = (over: Record<string, unknown> = {}, auto: Record<string, unknown> = {}) => ({
    generatedAt: '2026-10-09T10:00:00Z',
    filters: {},
    evidence: {
      total: 7,
      counts: { draft: 3, submitted: 2, approved: 1, rejected: 1, expired: 0, superseded: 0 },
      closed: 3,
      closureRatePct: 42.9,
      rejected: 1,
      expiringSoon: 1,
      expired: 0,
      retentionDays: 365,
      avgReviewTimeDays: null, // no reviewed evidence: this tile must not appear
      auditorSla: null,
      pendingAging: { count: 5, avgDaysInQueue: 1.5 },
      ...over,
    },
    automated: {
      totalRuns: 5, scheduledRuns: 3, manualRuns: 2, bulkRuns: 0, completed: 3, partial: 0, failed: 2, inProgress: 0,
      successRatePct: 60, evidenceIngested: 12, duplicatesSkipped: 4,
      lastRun: { runId: 'run-9', status: 'FAILED', at: '2026-10-09T09:00:00Z', scheduleName: 'Jira pull' },
      activeSchedules: 1, nextDueAt: null, runsWithinSla: 5, runsBreached: 0, slaSeconds: 60,
      openObservations: 2, unreadNotifications: 1,
      ...auto,
    },
  })

  /** Only the KPI endpoint is 'live'; everything else is offline and falls back to mock data. */
  function stubKpiBackend(respond: (url: string) => unknown) {
    const calls: string[] = []
    vi.stubGlobal(
      'fetch',
      vi.fn((url: string) => {
        if (String(url).includes('/api/v1/dashboard/kpis')) {
          calls.push(String(url))
          return Promise.resolve({ ok: true, status: 200, statusText: 'OK', text: async () => JSON.stringify(respond(String(url))) })
        }
        return Promise.reject(new TypeError('offline in test'))
      }),
    )
    return calls
  }

  it('Evidence tab renders the API KPIs, hides metrics it cannot compute and shows no demo-data note', async () => {
    sessionStorage.setItem(PERSONA_STORAGE_KEY, 'APP')
    stubKpiBackend(() => kpis())
    render(<MemoryRouter><Dashboard /></MemoryRouter>)

    expect(await screen.findByLabelText('Evidence in scope')).toHaveTextContent('7 evidence records in scope')
    expect(screen.getByText('Total evidence')).toBeInTheDocument()
    expect(screen.getByText('42.9%')).toBeInTheDocument() // closure rate
    expect(screen.getByText('Avg 1.5 days in queue')).toBeInTheDocument()
    expect(screen.getByText(/1 within 30 days · 0 past the 365-day retention/)).toBeInTheDocument()
    // not computable -> not rendered (never a placeholder number)
    expect(screen.queryByText('Avg review time')).not.toBeInTheDocument()
    expect(screen.queryByText('Auditor SLA')).not.toBeInTheDocument()
    expect(screen.queryByText('Rejection trend')).not.toBeInTheDocument()
    expect(screen.queryByText(/Demo data/)).not.toBeInTheDocument()
    expect(screen.queryByText('51.6%')).not.toBeInTheDocument()

    // Automated collection section
    expect(await screen.findByRole('heading', { name: 'Automated collection' })).toBeInTheDocument()
    expect(screen.getByText('Total runs')).toBeInTheDocument()
    expect(screen.getByText('60%')).toBeInTheDocument() // success rate
    expect(screen.getByRole('link', { name: 'open run' })).toHaveAttribute('href', '/scheduler?run=run-9')
    expect(screen.getByRole('link', { name: /Open Scheduler/ })).toHaveAttribute('href', '/scheduler')
  })

  it('changing a filter refetches with those params, empty results show zeros and Reset restores everything', async () => {
    sessionStorage.setItem(PERSONA_STORAGE_KEY, 'APP')
    const calls = stubKpiBackend((url) =>
      url.includes('collectionMethod=scheduled')
        ? kpis(
            { total: 0, counts: { draft: 0, submitted: 0, approved: 0, rejected: 0, expired: 0, superseded: 0 }, closed: 0, closureRatePct: null, rejected: 0, expiringSoon: 0, pendingAging: { count: 0, avgDaysInQueue: null } },
            { totalRuns: 0, scheduledRuns: 0, manualRuns: 0, completed: 0, failed: 0, successRatePct: null, evidenceIngested: 0, duplicatesSkipped: 0, lastRun: null, runsWithinSla: 0 },
          )
        : kpis(),
    )
    render(<MemoryRouter><Dashboard /></MemoryRouter>)
    expect(await screen.findByLabelText('Evidence in scope')).toHaveTextContent('7 evidence records')

    const app = screen.getByLabelText('Application') as HTMLSelectElement
    await waitFor(() => expect(app.options.length).toBeGreaterThan(1))
    fireEvent.change(app, { target: { value: app.options[1].value } })
    await waitFor(() => expect(calls.some((c) => c.includes(`application=${app.options[1].value}`))).toBe(true))

    fireEvent.change(screen.getByLabelText('Collection method'), { target: { value: 'scheduled' } })
    await waitFor(() => expect(calls[calls.length - 1]).toContain('collectionMethod=scheduled'))
    expect(calls[calls.length - 1]).toContain(`application=${app.options[1].value}`)
    // zeros, not the previous numbers
    expect(await screen.findByLabelText('Evidence in scope')).toHaveTextContent('0 evidence records')
    expect(screen.queryByText('42.9%')).not.toBeInTheDocument()
    expect(screen.getByText(/no runs match these filters/)).toBeInTheDocument()
    expect(screen.getByLabelText('Active filters')).toHaveTextContent('collectionMethod = scheduled')

    fireEvent.click(screen.getByRole('button', { name: 'Reset' }))
    await waitFor(() => expect(calls[calls.length - 1]).not.toContain('?'))
    expect(await screen.findByLabelText('Evidence in scope')).toHaveTextContent('7 evidence records')
  })

  it('labels the page as offline sample data when the backend is unreachable', async () => {
    sessionStorage.setItem(PERSONA_STORAGE_KEY, 'APP')
    renderOffline(<Dashboard />)
    expect(await screen.findByText(/Offline sample data/)).toBeInTheDocument()
    expect(screen.queryByText(/Demo data/)).not.toBeInTheDocument()
  })

  it('one failing widget shows its own Retry while the others stay on live data (no mock banner)', async () => {
    sessionStorage.setItem(PERSONA_STORAGE_KEY, 'APP')
    const mocks = await import('../api/mocks')
    let lifecycleCalls = 0
    const ok = (body: unknown) =>
      Promise.resolve({ ok: true, status: 200, statusText: 'OK', text: async () => JSON.stringify(body) })
    vi.stubGlobal(
      'fetch',
      vi.fn((url: string) => {
        const u = String(url)
        if (u.includes('/api/v1/dashboard/kpis')) return ok(kpis())
        if (u.includes('/api/v1/applications')) return ok(mocks.mockApplications)
        if (u.includes('/api/v1/insight/trend')) return ok(mocks.mockTrendForApplication('net-banking'))
        if (u.includes('/api/v1/insight/evidence-completeness/frameworks')) return ok(mocks.mockEvidenceCompletenessFrameworks())
        if (u.includes('/api/v1/evidence?')) return ok({ items: [], page: 0, size: 500, totalItems: 0, totalPages: 1 })
        if (u.includes('/api/v1/insight/evidence-lifecycle/summary')) {
          lifecycleCalls++
          // fails on the first try only: the widget's Retry must be able to recover
          return lifecycleCalls === 1
            ? Promise.resolve({ ok: false, status: 500, statusText: 'Server Error', text: async () => JSON.stringify({ message: 'summary exploded' }) })
            : ok(mocks.mockEvidenceLifecycleSummary('net-banking'))
        }
        if (u.includes('/api/v1/system/status')) return ok({}) // the backend is reachable
        return Promise.resolve({ ok: false, status: 404, statusText: 'Not Found', text: async () => '{}' })
      }),
    )
    render(<MemoryRouter><Dashboard /></MemoryRouter>)

    // the KPI widget is live
    expect(await screen.findByLabelText('Evidence in scope')).toHaveTextContent('7 evidence records in scope')
    // the failing widget says so and offers Retry
    expect(await screen.findByText(/Couldn't load this widget \(summary exploded\)/)).toBeInTheDocument()
    // ... without flipping the page to mock data
    expect(screen.queryByText(/Offline sample data/)).not.toBeInTheDocument()
    expect(getDataSource()).toBe('live')

    fireEvent.click(screen.getByRole('button', { name: 'Retry' }))
    await waitFor(() => expect(lifecycleCalls).toBe(2))
    await waitFor(() => expect(screen.queryByText(/Couldn't load this widget/)).not.toBeInTheDocument())
    expect(screen.getByLabelText('Evidence in scope')).toHaveTextContent('7 evidence records in scope')
  })

  it('Frameworks tab shows Framework Compliance, the Pending Actions Work Queue and Critical Findings (mock)', async () => {
    sessionStorage.setItem(PERSONA_STORAGE_KEY, 'APP')
    renderOffline(<Dashboard />)

    fireEvent.click(await screen.findByRole('tab', { name: 'Frameworks' }))

    // Compliance tab's KPI row + By framework table (via ComplianceView, reused as-is)
    expect(await screen.findByText('Framework Compliance')).toBeInTheDocument()
    expect(screen.getByText('By framework')).toBeInTheDocument()

    // Controls tab's Pending Actions Work Queue (moved)
    expect(screen.getByText('80 open')).toBeInTheDocument()
    expect(screen.getByText('DPS-C11')).toBeInTheDocument()
    expect(screen.getByText('DPSC_MANUAL_OVERRIDE_AP_Q1.xlsx')).toBeInTheDocument()
    expect(screen.getAllByText('2026-11-30').length).toBeGreaterThan(0)

    // Findings tab's card grid, renamed "Critical Findings" (moved) — scope the Critical
    // count to this section since the Work Queue table's Priority column also reads "Critical".
    const findingsHeading = screen.getByText('Critical Findings')
    const findingsSection = findingsHeading.closest('.card') as HTMLElement
    expect(within(findingsSection).getAllByText('Critical').length).toBe(8)
    expect(screen.getByText(/Manual override approval log/)).toBeInTheDocument()
    expect(screen.getByText(/Post-implementation review samples/)).toBeInTheDocument()
  })

  it('Applications tab shows the profile card and Upcoming Audits for this app (mock)', async () => {
    sessionStorage.setItem(PERSONA_STORAGE_KEY, 'APP')
    renderOffline(<Dashboard />)

    fireEvent.click(await screen.findByRole('tab', { name: 'Applications' }))

    expect(await screen.findByText('Application Profile')).toBeInTheDocument()
    expect(screen.getByText('Region')).toBeInTheDocument()
    expect(screen.getByText('India — West')).toBeInTheDocument()

    expect(await screen.findByText('Upcoming Audits')).toBeInTheDocument()
  })

  it('Audit Readiness tab shows the composite score, Ready/At Risk band and Coverage by Framework table (mock)', async () => {
    sessionStorage.setItem(PERSONA_STORAGE_KEY, 'APP')
    renderOffline(<Dashboard />)

    fireEvent.click(await screen.findByRole('tab', { name: 'Audit Readiness' }))

    expect(await screen.findByText('Audit Readiness Score')).toBeInTheDocument()
    expect(screen.getByText('Composite score')).toBeInTheDocument()
    expect(screen.getByText('Control coverage')).toBeInTheDocument()
    expect(screen.getByText('Approved evidence')).toBeInTheDocument()
    expect(screen.getByText('Freshness')).toBeInTheDocument()
    expect(screen.getByText('88%')).toBeInTheDocument() // mock freshness input
    expect(screen.getByText(/^(Ready|At Risk)$/)).toBeInTheDocument()

    expect(await screen.findByText('Coverage by Framework')).toBeInTheDocument()
  })

  it('no longer offers the old Overview/Controls/Findings/Remediation/Compliance tabs', async () => {
    sessionStorage.setItem(PERSONA_STORAGE_KEY, 'APP')
    renderOffline(<Dashboard />)

    await screen.findByRole('tablist', { name: 'Dashboard sections' })
    for (const name of ['Overview', 'Controls', 'Findings', 'Remediation', 'Compliance']) {
      expect(screen.queryByRole('tab', { name })).not.toBeInTheDocument()
    }
  })
})

describe('Dashboard — Admin (platform-ops only)', () => {
  afterEach(() => sessionStorage.removeItem(PERSONA_STORAGE_KEY))

  it('offers exactly the three ops tabs and no compliance, trend or leadership content', async () => {
    sessionStorage.setItem(PERSONA_STORAGE_KEY, 'ADMIN')
    renderOffline(<Dashboard />)

    const tablist = await screen.findByRole('tablist', { name: 'Dashboard sections' })
    expect(within(tablist).getAllByRole('tab').map((t) => t.textContent)).toEqual([
      'System Health',
      'Ingestion & Sources',
      'Scope',
    ])

    for (const name of ['System Health', 'Ingestion & Sources', 'Scope']) {
      fireEvent.click(within(tablist).getByRole('tab', { name }))
      expect(screen.queryByText(/compliance/i)).not.toBeInTheDocument()
      expect(screen.queryByText(/leadership/i)).not.toBeInTheDocument()
      expect(screen.queryByText(/over time/i)).not.toBeInTheDocument()
    }
  })
})
