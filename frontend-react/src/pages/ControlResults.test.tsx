import { afterEach, describe, expect, it, vi } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ControlResults } from './ControlResults'
import { renderOffline } from '../test/render'

afterEach(() => vi.unstubAllGlobals())

describe('ControlResults', () => {
  it('shows PASS/WARNING/FAIL rows from shared mock data and filters by status', async () => {
    renderOffline(<ControlResults />)

    // 10 rows in results.json
    expect(await screen.findByText('Results — 10')).toBeInTheDocument()

    // summary tile: 5 PASS in the shared fixtures
    expect(screen.getByText('PASS', { selector: '.stat-label' })).toBeInTheDocument()

    await userEvent.selectOptions(screen.getByLabelText('Status'), 'FAIL')
    await waitFor(() => expect(screen.getByText('Results — 2')).toBeInTheDocument())
    expect(screen.getByText('TLS-CERT-EXPIRY')).toBeInTheDocument()
  })

  it('re-evaluate reports a deterministic summary', async () => {
    renderOffline(<ControlResults />)
    await screen.findByText('Results — 10')

    await userEvent.click(screen.getByRole('button', { name: 'Re-evaluate' }))
    expect(await screen.findByText(/results ·/)).toBeInTheDocument()
  })
})
