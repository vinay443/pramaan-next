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

  it('separates a duplicate skip from the success summary and from true failures', async () => {
    renderOffline(<BulkUpload />)

    await userEvent.type(screen.getByLabelText('Control ID'), 'OS-SSH-ROOT-LOGIN')
    const input = screen.getByLabelText('Choose files') as HTMLInputElement
    // the mock backend flags the second uploaded item as a duplicate
    await userEvent.upload(input, [makeFile('first.txt', 'a'), makeFile('second.txt', 'b')])

    await userEvent.click(screen.getByRole('button', { name: 'Submit bulk' }))
    expect(await screen.findByText('Result')).toBeInTheDocument()

    // distinct success-count summary line — not a generic pass/fail message
    expect(screen.getByText('1 added successfully, 1 skipped as duplicate.')).toBeInTheDocument()

    // the duplicate is named individually and explained, separately from the summary
    const note = screen.getByRole('status')
    expect(note).toHaveTextContent('Skipped as duplicate (1):')
    expect(note).toHaveTextContent('second.txt')
    expect(note).toHaveTextContent('was not added — duplicate of existing evidence')

    // it is not reported as a failure
    expect(screen.queryByText(/Failed \(/)).not.toBeInTheDocument()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
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

    // the empty required field is visibly flagged, not just reported in the banner
    expect(screen.getByLabelText('Application')).toHaveAttribute('aria-invalid', 'true')
    expect(screen.getByLabelText('Framework')).toHaveAttribute('aria-invalid', 'false')
  })

  it('surfaces a visible error when the live request itself fails (not silently swallowed)', async () => {
    renderOffline(<BulkUpload />)
    // renderOffline stubs fetch to reject, which withFallback treats as "offline" and
    // silently serves mock data — force a non-offline HTTP failure instead so the error path
    // (as opposed to the offline-fallback path) is what's under test.
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: false,
        status: 400,
        statusText: 'Bad Request',
        text: () => Promise.resolve(JSON.stringify({ message: 'controlId must not be blank' })),
      }),
    )

    await userEvent.type(screen.getByLabelText('Control ID'), 'C-1')
    const input = screen.getByLabelText('Choose files') as HTMLInputElement
    await userEvent.upload(input, [makeFile('one.txt', 'a')])

    await userEvent.click(screen.getByRole('button', { name: 'Submit bulk' }))
    expect(await screen.findByRole('alert')).toHaveTextContent(/controlId must not be blank/i)
  })
})
