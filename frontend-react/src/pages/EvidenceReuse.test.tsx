import { describe, expect, it } from 'vitest'
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { EvidenceReuse } from './EvidenceReuse'
import { renderOffline } from '../test/render'

// The page is a static, client-only workbench mock-up now: no backend calls, no
// "Find similar evidence" / "Browse by control" tabs. These tests check the
// static content renders and the cosmetic filter/action controls behave.

describe('EvidenceReuse', () => {
  it('renders the page title and workbench stat cards', () => {
    renderOffline(<EvidenceReuse />)
    expect(screen.getByRole('heading', { name: 'Evidence Reuse', level: 1 })).toBeInTheDocument()
    expect(fetch).not.toHaveBeenCalled()

    expect(screen.getByText('Evidence Records', { selector: '.stat-label' })).toBeInTheDocument()
    expect(screen.getByText('606')).toBeInTheDocument()
    expect(screen.getByText('1.0x')).toBeInTheDocument()
  })

  it('renders all six workbench actions and re-runs on click', async () => {
    renderOffline(<EvidenceReuse />)
    for (const label of [
      'Refresh evidence',
      'Run reuse analysis',
      'Validate completeness',
      'Refresh audit readiness',
      'Generate observations',
      'Check closure eligibility',
    ]) {
      expect(screen.getByRole('button', { name: label })).toBeInTheDocument()
    }

    await userEvent.click(screen.getByRole('button', { name: 'Generate observations' }))
    expect(screen.getByText(/Ran "Generate observations"/)).toBeInTheDocument()
  })

  it('filters the Action Result table by application', async () => {
    renderOffline(<EvidenceReuse />)
    expect(screen.getByText('UPI::ASST-14 — Container & Cloud Coverage v1')).toBeInTheDocument()
    expect(screen.getByText('Treasury::ASST-16 — Privileged Tool Usage v1')).toBeInTheDocument()

    await userEvent.type(screen.getByLabelText('Application'), 'Treasury')

    expect(screen.queryByText('UPI::ASST-14 — Container & Cloud Coverage v1')).not.toBeInTheDocument()
    expect(screen.getByText('Treasury::ASST-16 — Privileged Tool Usage v1')).toBeInTheDocument()
  })

  it('renders section 1 - Evidence Generated with violation and satisfied rows', () => {
    renderOffline(<EvidenceReuse />)
    expect(screen.getByText('1 · Evidence Generated', { selector: 'h2' })).toBeInTheDocument()
    expect(screen.getAllByText('PQ-EVD-DEMO-DB-001').length).toBeGreaterThan(0)
    expect(screen.getByText('ssl · --- · off')).toBeInTheDocument()
    expect(screen.getAllByText('Violation').length).toBeGreaterThan(0)
    expect(screen.getAllByText('Satisfied').length).toBeGreaterThan(0)
  })

  it('renders section 2 - Evidence Reuse stats and 9 rows', () => {
    renderOffline(<EvidenceReuse />)
    expect(screen.getByText('2 · Evidence Reuse', { selector: 'h2' })).toBeInTheDocument()
    const reuseCountLabel = screen.getByText('Reuse Count', { selector: '.stat-label' })
    expect(reuseCountLabel.parentElement?.querySelector('.stat-value')?.textContent).toBe('9')
    expect(screen.getByText('Showing 1–9 of 9 records')).toBeInTheDocument()
  })

  it('renders section 3 - Audit Readiness with a meter per framework', () => {
    renderOffline(<EvidenceReuse />)
    expect(screen.getByText('3 · Audit Readiness', { selector: 'h2' })).toBeInTheDocument()
    expect(screen.getByText('66.7%')).toBeInTheDocument()
    expect(screen.getAllByText('DB Baseline').length).toBeGreaterThan(0)
    expect(screen.getByText('0/1 (0.0%)', { selector: '.meter-label' })).toBeInTheDocument()
    expect(screen.getAllByText('1/2 (50.0%)', { selector: '.meter-label' }).length).toBe(2)
  })

  it('renders section 4 - Observations, open and ready-for-closure', () => {
    renderOffline(<EvidenceReuse />)
    expect(screen.getByText('4 · Observations', { selector: 'h2' })).toBeInTheDocument()
    expect(screen.getByText('OBS-DB-001-0001')).toBeInTheDocument()
    expect(screen.getByText(/Database SSL\/TLS is OFF/)).toBeInTheDocument()
    expect(screen.getByText('OBS-OS-0007')).toBeInTheDocument()
    expect(screen.getByText('READY FOR CLOSURE')).toBeInTheDocument()
  })
})
