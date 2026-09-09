import { afterEach, describe, expect, it, vi } from 'vitest'
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { EvidenceDetail } from './EvidenceDetail'
import { renderOffline } from '../test/render'

afterEach(() => vi.unstubAllGlobals())

describe('EvidenceDetail lifecycle (UC13)', () => {
  it('shows the lifecycle state, audit trail and advances it (mock)', async () => {
    renderOffline(<EvidenceDetail />)

    const life = (await screen.findByRole('heading', { name: 'Lifecycle' })).closest('.card') as HTMLElement
    expect(within(life).getByText('INGESTED')).toBeInTheDocument()

    await userEvent.click(within(life).getByRole('button', { name: 'SUBMIT' }))
    await waitFor(() => expect(within(life).getByText('SUBMITTED')).toBeInTheDocument())
    expect(within(life).getByText('SUBMIT')).toBeInTheDocument()
  })
})
