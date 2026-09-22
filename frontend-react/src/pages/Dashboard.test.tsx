import { afterEach, describe, expect, it, vi } from 'vitest'
import { fireEvent, screen, within } from '@testing-library/react'
import { Dashboard } from './Dashboard'
import { renderOffline } from '../test/render'
import { PERSONA_STORAGE_KEY } from './PersonaLogin'

afterEach(() => vi.unstubAllGlobals())

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
  afterEach(() => localStorage.removeItem(PERSONA_STORAGE_KEY))

  it('National & Enterprise tab shows the org compliance trend, region RAG, cuts and top risks (mock)', async () => {
    localStorage.setItem(PERSONA_STORAGE_KEY, 'CIO')
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
  afterEach(() => localStorage.removeItem(PERSONA_STORAGE_KEY))

  it('Closure & Trend tab shows closure metrics and the org compliance trend, no business cuts (mock)', async () => {
    localStorage.setItem(PERSONA_STORAGE_KEY, 'AUDITOR')
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
  afterEach(() => localStorage.removeItem(PERSONA_STORAGE_KEY))

  it('renders the six App Owner tabs with the POC-matched header (mock)', async () => {
    localStorage.setItem(PERSONA_STORAGE_KEY, 'APP')
    renderOffline(<Dashboard />)

    expect(await screen.findByRole('heading', { name: 'Application Owner Dashboard' })).toBeInTheDocument()
    expect(screen.getByText('Governance posture and prioritized actions')).toBeInTheDocument()

    const tablist = await screen.findByRole('tablist', { name: 'Dashboard sections' })
    expect(within(tablist).getAllByRole('tab').map((t) => t.textContent)).toEqual([
      'Overview',
      'Controls',
      'Evidence',
      'Findings',
      'Remediation',
      'Compliance',
    ])
  })

  it('Overview tab shows the POC-matched KPI numbers (mock)', async () => {
    localStorage.setItem(PERSONA_STORAGE_KEY, 'APP')
    renderOffline(<Dashboard />)

    expect(await screen.findByText('Draft')).toBeInTheDocument()
    expect(screen.getAllByText('78').length).toBeGreaterThanOrEqual(2) // Draft + Submitted KPI cards
    expect(screen.getByText('51.6%')).toBeInTheDocument() // Closure rate
    expect(screen.getByText('3.2d')).toBeInTheDocument() // Avg review time
    expect(screen.getByText('↓ 12%')).toBeInTheDocument() // Rejection trend
    expect(screen.getByText('94.5%')).toBeInTheDocument() // Auditor SLA
    expect(screen.getByText('Within 5-day target')).toBeInTheDocument()
    expect(screen.getByText('80')).toBeInTheDocument() // Pending Actions highlight
    expect(screen.getByText('12')).toBeInTheDocument() // Rejected Evidence highlight
    expect(screen.getByText('36')).toBeInTheDocument() // Expiring / Stale highlight
    expect(screen.getByText('83')).toBeInTheDocument() // Closed chip
  })

  it('Controls tab shows the Pending Actions Work Queue with POC rows (mock)', async () => {
    localStorage.setItem(PERSONA_STORAGE_KEY, 'APP')
    renderOffline(<Dashboard />)

    fireEvent.click(await screen.findByRole('tab', { name: 'Controls' }))
    expect(await screen.findByText('80 open')).toBeInTheDocument()
    expect(screen.getByText('DPS-C11')).toBeInTheDocument()
    expect(screen.getByText('DPSC_MANUAL_OVERRIDE_AP_Q1.xlsx')).toBeInTheDocument()
    expect(screen.getAllByText('2026-11-30').length).toBeGreaterThan(0)
  })

  it('Findings tab shows 8 Critical prioritized-action cards (mock)', async () => {
    localStorage.setItem(PERSONA_STORAGE_KEY, 'APP')
    renderOffline(<Dashboard />)

    fireEvent.click(await screen.findByRole('tab', { name: 'Findings' }))
    expect(await screen.findByText('Prioritized Actions')).toBeInTheDocument()
    expect(screen.getAllByText('Critical').length).toBe(8)
    expect(screen.getByText(/Manual override approval log/)).toBeInTheDocument()
    expect(screen.getByText(/Post-implementation review samples/)).toBeInTheDocument()
  })

  it('Remediation tab shows 5 resubmission cards wired to Bulk Upload (mock)', async () => {
    localStorage.setItem(PERSONA_STORAGE_KEY, 'APP')
    renderOffline(<Dashboard />)

    fireEvent.click(await screen.findByRole('tab', { name: 'Remediation' }))
    const resubmitLinks = await screen.findAllByRole('link', { name: 'Resubmit Evidence' })
    expect(resubmitLinks).toHaveLength(5)
    expect(resubmitLinks[0]).toHaveAttribute(
      'href',
      expect.stringContaining('applicationSlug=upi&framework=DPSC&controlId=DPS-C19'),
    )
  })

  it('Evidence tab shows the Evidence Rejections audit trail with the POC-matched rows (mock)', async () => {
    localStorage.setItem(PERSONA_STORAGE_KEY, 'APP')
    renderOffline(<Dashboard />)

    fireEvent.click(await screen.findByRole('tab', { name: 'Evidence' }))
    expect(await screen.findByText('Evidence Rejections (12)')).toBeInTheDocument()
    expect(screen.getByText('Biometric Data Minimization')).toBeInTheDocument()
    expect(screen.getAllByText('S. Nair (Auditor)').length).toBeGreaterThan(0)
    expect(screen.getAllByText('Owner Review').length).toBeGreaterThan(0)
  })

  it('Compliance tab merges Framework Compliance and vs. Portfolio Average (mock)', async () => {
    localStorage.setItem(PERSONA_STORAGE_KEY, 'APP')
    renderOffline(<Dashboard />)

    fireEvent.click(await screen.findByRole('tab', { name: 'Compliance' }))
    expect(await screen.findByText('Framework Compliance')).toBeInTheDocument()
    expect(await screen.findByText(/vs\. portfolio average/)).toBeInTheDocument()
    expect(screen.queryByRole('tab', { name: 'vs. Portfolio' })).not.toBeInTheDocument()
  })
})

describe('Dashboard — Admin (platform-ops only)', () => {
  afterEach(() => localStorage.removeItem(PERSONA_STORAGE_KEY))

  it('offers exactly the three ops tabs and no compliance, trend or leadership content', async () => {
    localStorage.setItem(PERSONA_STORAGE_KEY, 'ADMIN')
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
