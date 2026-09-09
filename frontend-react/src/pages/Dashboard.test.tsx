import { afterEach, describe, expect, it, vi } from 'vitest'
import { screen, within } from '@testing-library/react'
import { Dashboard } from './Dashboard'
import { renderOffline } from '../test/render'

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
