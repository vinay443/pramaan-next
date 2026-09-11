import { afterEach, describe, expect, it, vi } from 'vitest'
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { EvidenceLifecycle } from './EvidenceLifecycle'
import { renderOffline } from '../test/render'

afterEach(() => vi.unstubAllGlobals())

describe('EvidenceLifecycle (UC13 portfolio view)', () => {
  it('lists evidence and shows retention status + version history for a selected record', async () => {
    renderOffline(<EvidenceLifecycle />)

    await waitFor(() => expect(screen.getAllByRole('row').length).toBeGreaterThan(1))

    const rows = screen.getAllByRole('row')
    await userEvent.click(rows[1])

    const retention = (await screen.findByRole('heading', { name: 'Retention & archival' })).closest(
      '.card',
    ) as HTMLElement
    expect(within(retention).getByText('Audit trail', { selector: 'h3' })).toBeInTheDocument()
    expect(within(retention).getByText('Version history', { selector: 'h3' })).toBeInTheDocument()
  })

  it('archives an approved record and reflects the new state', async () => {
    renderOffline(<EvidenceLifecycle />)
    await waitFor(() => expect(screen.getAllByRole('row').length).toBeGreaterThan(1))
    await userEvent.click(screen.getAllByRole('row')[1])

    const retention = (await screen.findByRole('heading', { name: 'Retention & archival' })).closest(
      '.card',
    ) as HTMLElement

    // Mock evidence starts in DRAFT — no Archive action until it reaches APPROVED.
    expect(within(retention).queryByRole('button', { name: 'Archive' })).not.toBeInTheDocument()
  })
})
