import { afterEach, describe, expect, it, vi } from 'vitest'
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { BulkUpload } from './BulkUpload'
import { renderOffline } from '../test/render'

afterEach(() => vi.unstubAllGlobals())

describe('BulkUpload', () => {
  it('submits items and shows the per-item outcome summary (mock)', async () => {
    renderOffline(<BulkUpload />)

    await userEvent.type(screen.getByLabelText('Control 1'), 'OS-SSH-ROOT-LOGIN')
    await userEvent.type(screen.getByLabelText('Content 1'), 'PermitRootLogin no')

    await userEvent.click(screen.getByRole('button', { name: /add item/i }))
    await userEvent.type(screen.getByLabelText('Control 2'), 'OS-AUDIT-LOGGING')
    await userEvent.type(screen.getByLabelText('Content 2'), 'auditd enabled')

    await userEvent.click(screen.getByRole('button', { name: 'Submit bulk' }))

    expect(await screen.findByText('Result')).toBeInTheDocument()
    // received tile
    const received = screen.getByText('Received').closest('.card') as HTMLElement
    expect(received.textContent).toContain('2')
    expect(screen.getByText('OS-SSH-ROOT-LOGIN')).toBeInTheDocument()
    expect(screen.getByText('CREATED')).toBeInTheDocument()
  })

  it('rejects an empty submission', async () => {
    renderOffline(<BulkUpload />)
    await userEvent.click(screen.getByRole('button', { name: 'Submit bulk' }))
    expect(await screen.findByRole('alert')).toHaveTextContent(/at least one item/i)
  })
})
