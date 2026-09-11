import { afterEach, describe, expect, it, vi } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { GrcIntegration } from './GrcIntegration'
import { renderOffline } from '../test/render'

afterEach(() => vi.unstubAllGlobals())

describe('GrcIntegration (outbound GRC sync)', () => {
  it('shows never-synced status, then syncs and reports a mock reference (mock)', async () => {
    renderOffline(<GrcIntegration />)

    expect(await screen.findByText('NEVER SYNCED')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Sync now' }))

    await waitFor(() => expect(screen.getByText('SUCCESS')).toBeInTheDocument())
    expect(screen.getByText(/synced \d+ evidence record/)).toBeInTheDocument()
  })
})
