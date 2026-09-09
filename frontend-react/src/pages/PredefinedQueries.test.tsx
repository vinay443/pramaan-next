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

    await userEvent.selectOptions(screen.getByLabelText('Technology'), 'Linux')
    await waitFor(() => expect(screen.queryByText('DB-001')).not.toBeInTheDocument())
    expect(screen.getByText('LNX-007')).toBeInTheDocument()

    await userEvent.click(screen.getAllByRole('button', { name: 'Run' })[0])
    await waitFor(() => expect(screen.getByText(/→ evidence ev-pq-/)).toBeInTheDocument())
  })

  it('runs the whole (filtered) catalogue and shows a scheduler-shaped summary', async () => {
    renderOffline(<PredefinedQueries />)
    expect(await screen.findByText(/Catalogue —/)).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: /^Run all/ }))
    await waitFor(() => expect(screen.getByText('Received')).toBeInTheDocument())
    expect(screen.getByText('Ingested')).toBeInTheDocument()
  })
})
