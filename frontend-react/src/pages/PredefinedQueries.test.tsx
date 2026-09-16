import { afterEach, describe, expect, it, vi } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { PredefinedQueries } from './PredefinedQueries'
import { renderOffline } from '../test/render'

afterEach(() => vi.unstubAllGlobals())

describe('PredefinedQueries', () => {
  it('lists the catalogue, filters by technology, and runs one query (mock)', async () => {
    renderOffline(<PredefinedQueries />)

    expect(await screen.findByText(/Catalogue —/)).toBeInTheDocument()
    expect(screen.getByText('DB-001')).toBeInTheDocument()

    // Selecting a technology alone doesn't refilter — it only takes effect on Apply.
    await userEvent.selectOptions(screen.getByLabelText('Technology'), 'Linux')
    expect(screen.getByText('DB-001')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Apply' }))
    await waitFor(() => expect(screen.queryByText('DB-001')).not.toBeInTheDocument())
    expect(screen.getByText('LNX-007')).toBeInTheDocument()

    await userEvent.click(screen.getAllByRole('button', { name: 'Run Query' })[0])
    await waitFor(() => expect(screen.getByRole('link', { name: 'view details' })).toBeInTheDocument())
    expect(screen.getByRole('link', { name: 'view details' })).toHaveAttribute(
      'href',
      expect.stringMatching(/^\/predefined-queries\/LNX-007$/),
    )
  })

  it('runs the whole (filtered) catalogue and shows a scheduler-shaped summary with per-control results', async () => {
    renderOffline(<PredefinedQueries />)
    expect(await screen.findByText(/Catalogue —/)).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: /^Run all/ }))
    await waitFor(() => expect(screen.getByText('Received')).toBeInTheDocument())
    expect(screen.getByText('Ingested')).toBeInTheDocument()
    expect(screen.getAllByText('DB-001').length).toBeGreaterThan(1)
  })

  it('shows stat cards computed from the catalogue and a pagination range for the mock (12-item) catalogue', async () => {
    renderOffline(<PredefinedQueries />)
    expect(await screen.findByText(/Catalogue —/)).toBeInTheDocument()

    expect(screen.getByText('Total Controls')).toBeInTheDocument()
    expect(screen.getByText('Frameworks Covered')).toBeInTheDocument()

    // 12 mock rows fit on one page — no Prev/Next pager, just the range.
    expect(screen.getByText(/Showing 1–12 of 12 controls/)).toBeInTheDocument()
  })

  it('switches to the Execution History and Manual Controls tabs without fetching new data', async () => {
    renderOffline(<PredefinedQueries />)
    expect(await screen.findByText(/Catalogue —/)).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Execution History' }))
    expect(screen.getByText(/Execution history isn't wired up/)).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Manual Controls' }))
    expect(screen.getByText(/No manual-control data source/)).toBeInTheDocument()
  })
})
