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
})
