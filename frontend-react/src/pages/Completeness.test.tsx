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

  it('switching application reloads the report', async () => {
    renderOffline(<Completeness />)
    await screen.findByText('Controls — 7')
    await userEvent.selectOptions(screen.getByLabelText('Application'), 'payments')
    // payments has ITPP-CHG-02 evidence dated 2026-06-01 -> STALE against referenceNow
    await waitFor(() => expect(screen.getAllByText('STALE').length).toBeGreaterThan(0))
  })
})
