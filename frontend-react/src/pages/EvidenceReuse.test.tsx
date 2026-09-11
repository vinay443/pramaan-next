import { afterEach, describe, expect, it, vi } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { EvidenceReuse } from './EvidenceReuse'
import { renderOffline } from '../test/render'

afterEach(() => vi.unstubAllGlobals())

describe('EvidenceReuse', () => {
  it('free-text search returns ranked matches from the shared fixtures (mock)', async () => {
    renderOffline(<EvidenceReuse />)

    await userEvent.click(screen.getByRole('button', { name: 'Search' }))

    await waitFor(() =>
      expect(screen.getByText('Matches', { selector: 'h2' })).toBeInTheDocument(),
    )
    expect(screen.getByText('Indexed', { selector: '.stat-label' })).toBeInTheDocument()
    expect(screen.getByText('mock-embed:v1(dim=256)')).toBeInTheDocument()
    // at least one match row beyond the header row
    expect(screen.getAllByRole('row').length).toBeGreaterThan(1)
  })

  it('browse-by-control shows frameworks + held evidence and an empty state', async () => {
    renderOffline(<EvidenceReuse />)
    await userEvent.click(screen.getByRole('button', { name: 'Browse by control' }))

    // a control with no evidence held in the fixtures
    await userEvent.type(await screen.findByLabelText('Control'), 'MW-HSTS')
    expect(
      await screen.findByText(/No evidence held for this control yet/),
    ).toBeInTheDocument()

    await userEvent.clear(screen.getByLabelText('Control'))
    await userEvent.type(screen.getByLabelText('Control'), 'OS-SSH-ROOT-LOGIN')
    expect(await screen.findByText('Existing evidence — 1')).toBeInTheDocument()
    expect(screen.getByText('Frameworks requiring OS-SSH-ROOT-LOGIN', { selector: 'h2' })).toBeInTheDocument()
  })

  it('"Reuse for [framework]" tags an existing record and refreshes the row', async () => {
    renderOffline(<EvidenceReuse />)
    await userEvent.click(screen.getByRole('button', { name: 'Browse by control' }))

    // ev-002 is seeded tagged to only its primary framework (PCI_DSS)
    await userEvent.type(await screen.findByLabelText('Control'), 'DB-TLS-IN-TRANSIT')

    const reuse = await screen.findByRole('button', { name: 'Reuse for DPSC' })
    await userEvent.click(reuse)

    await waitFor(() =>
      expect(screen.queryByRole('button', { name: 'Reuse for DPSC' })).not.toBeInTheDocument(),
    )
  })
})
