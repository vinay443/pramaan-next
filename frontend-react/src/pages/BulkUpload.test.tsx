import { afterEach, describe, expect, it, vi } from 'vitest'
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { BulkUpload } from './BulkUpload'
import { renderOffline } from '../test/render'

afterEach(() => vi.unstubAllGlobals())

function makeFile(name: string, content: string, type = 'text/plain') {
  return new File([content], name, { type })
}

describe('BulkUpload', () => {
  it('submits selected files and shows the per-item outcome summary (mock)', async () => {
    renderOffline(<BulkUpload />)

    await userEvent.type(screen.getByLabelText('Control ID'), 'OS-SSH-ROOT-LOGIN')

    const input = screen.getByLabelText('Choose files') as HTMLInputElement
    await userEvent.upload(input, [
      makeFile('sshd_config.txt', 'PermitRootLogin no'),
      makeFile('audit.txt', 'auditd enabled'),
    ])

    expect(screen.getByText('sshd_config.txt')).toBeInTheDocument()
    expect(screen.getByText('audit.txt')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Submit bulk' }))

    expect(await screen.findByText('Result')).toBeInTheDocument()
    const received = screen.getByText('Received').closest('.card') as HTMLElement
    expect(received.textContent).toContain('2')
    expect(screen.getAllByText('OS-SSH-ROOT-LOGIN')).toHaveLength(2)
    expect(screen.getByText('CREATED')).toBeInTheDocument()
  })

  it('allows removing a selected file before submit', async () => {
    renderOffline(<BulkUpload />)
    const input = screen.getByLabelText('Choose files') as HTMLInputElement
    await userEvent.upload(input, [makeFile('one.txt', 'a'), makeFile('two.txt', 'b')])

    expect(screen.getByText('one.txt')).toBeInTheDocument()
    await userEvent.click(screen.getByLabelText('Remove one.txt'))
    expect(screen.queryByText('one.txt')).not.toBeInTheDocument()
    expect(screen.getByText('two.txt')).toBeInTheDocument()
  })

  it('rejects an empty submission', async () => {
    renderOffline(<BulkUpload />)
    await userEvent.type(screen.getByLabelText('Control ID'), 'C-1')
    await userEvent.click(screen.getByRole('button', { name: 'Submit bulk' }))
    expect(await screen.findByRole('alert')).toHaveTextContent(/select at least one file/i)
  })

  it('requires application, framework and control before submitting', async () => {
    renderOffline(<BulkUpload />)
    const input = screen.getByLabelText('Choose files') as HTMLInputElement
    await userEvent.upload(input, [makeFile('one.txt', 'a')])
    await userEvent.clear(screen.getByLabelText('Application'))

    await userEvent.click(screen.getByRole('button', { name: 'Submit bulk' }))
    expect(await screen.findByRole('alert')).toHaveTextContent(/required/i)
  })
})
