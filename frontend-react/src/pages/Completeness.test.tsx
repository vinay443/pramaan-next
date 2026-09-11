import { afterEach, describe, expect, it, vi } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Completeness } from './Completeness'
import { renderOffline } from '../test/render'

afterEach(() => vi.unstubAllGlobals())

describe('Completeness', () => {
  it('shows coverage tiles and per-control rows for the first application (mock)', async () => {
    renderOffline(<Completeness />)

    // net-banking: 7 expected controls, 2 covered from the shared fixtures
    expect(await screen.findByText('Controls — 7')).toBeInTheDocument()
    expect(screen.getByText('Expected', { selector: '.stat-label' })).toBeInTheDocument()
    expect(screen.getByText('OS-SSH-ROOT-LOGIN')).toBeInTheDocument()
    expect(screen.getAllByText('MISSING').length).toBeGreaterThan(0)
  })

  it('traces each covered control to its evidence record and version (UC-P2-1)', async () => {
    renderOffline(<Completeness />)
    await screen.findByText('Controls — 7')

    // net-banking OS-SSH-ROOT-LOGIN is covered by ev-001 in the shared fixtures
    const link = screen.getByRole('link', { name: 'ev-001' })
    expect(link).toHaveAttribute('href', '/evidence/ev-001')

    // MISSING rows genuinely have no evidence record
    expect(screen.getAllByText('—').length).toBeGreaterThan(0)
  })

  it('completeness % states its own numerator and denominator (UC-P2-1)', async () => {
    renderOffline(<Completeness />)
    await screen.findByText('Controls — 7')

    const pct = screen.getByText('Completeness', { selector: '.stat-label' }).closest('.stat') as HTMLElement
    expect(pct.querySelector('.stat-hint')?.textContent).toMatch(/^\d+ covered \/ \d+ expected$/)
  })

  it('switching application reloads the report', async () => {
    renderOffline(<Completeness />)
    await screen.findByText('Controls — 7')
    await userEvent.selectOptions(screen.getByLabelText('Application'), 'payments')
    // payments has ITPP-CHG-02 evidence dated 2026-06-01 -> STALE against referenceNow
    await waitFor(() => expect(screen.getAllByText('STALE').length).toBeGreaterThan(0))
  })
})
