import { afterEach, describe, expect, it, vi } from 'vitest'
import { fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import userEvent from '@testing-library/user-event'
import { BulkUpload } from './BulkUpload'
import { renderOffline } from '../test/render'
import { PERSONA_STORAGE_KEY } from './PersonaLogin'

afterEach(() => {
  vi.unstubAllGlobals()
  sessionStorage.removeItem(PERSONA_STORAGE_KEY)
})

function makeFile(name: string, content: string, type = 'text/plain') {
  return new File([content], name, { type })
}

/** The control select is fed by the catalogue endpoint, so wait for its options before choosing. */
async function chooseControl(id: string) {
  await screen.findByRole('option', { name: id })
  await userEvent.selectOptions(screen.getByLabelText('Control ID'), id)
}

describe('BulkUpload', () => {
  it('Application, Framework and Control ID are selects fed by the catalogue; a control follows its framework', async () => {
    renderOffline(<BulkUpload />)
    const app = screen.getByLabelText('Application') as HTMLSelectElement
    const fw = screen.getByLabelText('Framework') as HTMLSelectElement
    const control = screen.getByLabelText('Control ID') as HTMLSelectElement
    expect([app, fw, control].map((e) => e.tagName)).toEqual(['SELECT', 'SELECT', 'SELECT'])

    expect(await screen.findByRole('option', { name: 'payments' })).toBeInTheDocument()
    await screen.findByRole('option', { name: 'OS-SSH-ROOT-LOGIN' })
    // only catalogue frameworks are offered, not free text
    expect(Array.from(fw.options).map((o) => o.value)).toEqual(expect.arrayContaining(['', 'PCI_DSS', 'C-SITE']))
    expect(Array.from(fw.options).map((o) => o.value)).not.toContain('NOT_A_FW')

    await userEvent.selectOptions(control, 'OS-SSH-ROOT-LOGIN')
    expect(control.value).toBe('OS-SSH-ROOT-LOGIN')
    // switching to a framework the control does not map to clears the selection
    const other = Array.from(fw.options).map((o) => o.value).find((v) => v && v !== 'PCI_DSS' && v !== 'C-SITE' && v !== 'ISO27001' && v !== 'IS')!
    await userEvent.selectOptions(fw, other)
    expect(control.value).toBe('')
    expect(Array.from(control.options).map((o) => o.value)).not.toContain('OS-SSH-ROOT-LOGIN')
  })

  it('submits selected files and shows the per-item outcome summary (mock)', async () => {
    renderOffline(<BulkUpload />)

    await chooseControl('OS-SSH-ROOT-LOGIN')

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
    expect(screen.getAllByText('OS-SSH-ROOT-LOGIN').length).toBeGreaterThanOrEqual(2)
    expect(screen.getByText('CREATED')).toBeInTheDocument()
  })

  it('separates a duplicate skip from the success summary and from true failures', async () => {
    renderOffline(<BulkUpload />)

    await chooseControl('OS-SSH-ROOT-LOGIN')
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
    await chooseControl('OS-SSH-ROOT-LOGIN')
    await userEvent.click(screen.getByRole('button', { name: 'Submit bulk' }))
    expect(await screen.findByRole('alert')).toHaveTextContent(/select at least one file/i)
  })

  it('requires application, framework and control before submitting', async () => {
    renderOffline(<BulkUpload />)
    const input = screen.getByLabelText('Choose files') as HTMLInputElement
    await userEvent.upload(input, [makeFile('one.txt', 'a')])
    await userEvent.selectOptions(screen.getByLabelText('Application'), '')

    await userEvent.click(screen.getByRole('button', { name: 'Submit bulk' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Application and Control ID are required.')

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

    await chooseControl('OS-SSH-ROOT-LOGIN')
    const input = screen.getByLabelText('Choose files') as HTMLInputElement
    await userEvent.upload(input, [makeFile('one.txt', 'a')])

    await userEvent.click(screen.getByRole('button', { name: 'Submit bulk' }))
    expect(await screen.findByRole('alert')).toHaveTextContent(/controlId must not be blank/i)
  })

  it('UC02: reports a disallowed file individually, keeps the rest of the batch and shows the batch id (mock)', async () => {
    renderOffline(<BulkUpload />)
    await chooseControl('OS-SSH-ROOT-LOGIN')
    await userEvent.selectOptions(screen.getByLabelText('Environment'), 'UAT')
    const input = screen.getByLabelText('Choose files') as HTMLInputElement
    await userEvent.upload(input, [makeFile('good.txt', 'a'), makeFile('tool.exe', 'MZ')])
    await userEvent.click(screen.getByRole('button', { name: 'Submit bulk' }))

    expect(await screen.findByText('Result')).toBeInTheDocument()
    expect(screen.getByText(/^Batch$/)).toBeInTheDocument()
    expect(screen.getByText('1 added successfully, 1 failed.')).toBeInTheDocument()
    expect(screen.getByText('REJECTED')).toBeInTheDocument()
    expect(screen.getByText('Not stored (1):', { exact: false })).toBeInTheDocument()
    expect(screen.getAllByText(/files are not accepted/).length).toBeGreaterThan(0)
    // a rejected file cannot be retried as-is; no retry button for it
    expect(screen.queryByRole('button', { name: /Retry/ })).not.toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Open this batch in the Repository' })).toHaveAttribute(
      'href',
      expect.stringContaining('/evidence?tag=batchId'),
    )
  })

  it('UC02: sends the persona headers, uploader, environment and retry link; retries only the failed files', async () => {
    sessionStorage.setItem(PERSONA_STORAGE_KEY, 'APP')
    const batch1 = {
      batchId: 'b1', status: 'PARTIAL', uploadedBy: 'App Owner', received: 2, created: 1, newVersions: 0, duplicates: 0, failed: 1,
      results: [{ evidenceId: 'e1', evidenceKey: 'k', applicationSlug: 'net-banking', controlId: 'C-1', framework: 'PCI_DSS', sourceSystem: 'BULK_UPLOAD', outcome: 'CREATED', version: 1, sha256: 'a'.repeat(64), sizeBytes: 1, sourceObjectId: 'ok.txt' }],
      errors: [{ index: 1, message: 'flaky.txt: could not be stored' }],
      files: [
        { index: 0, filename: 'ok.txt', status: 'CREATED', evidenceId: 'e1', version: 1, sha256: 'a'.repeat(64), sizeBytes: 1, retryable: false },
        { index: 1, filename: 'flaky.txt', status: 'FAILED', code: 'PROCESSING_ERROR', message: 'could not be stored, please retry', retryable: true },
      ],
    }
    const batch2 = { ...batch1, batchId: 'b2', status: 'COMPLETED', received: 1, failed: 0, errors: [], results: [], files: [{ index: 0, filename: 'flaky.txt', status: 'CREATED', evidenceId: 'e2', version: 1, sha256: 'b'.repeat(64), sizeBytes: 1, retryable: false }] }
    const calls: Array<{ url: string; init: RequestInit }> = []
    const ok = (body: unknown) => ({ ok: true, status: 200, statusText: 'OK', text: () => Promise.resolve(JSON.stringify(body)) })
    renderOffline(<BulkUpload />)
    vi.stubGlobal('fetch', vi.fn((url: string, init: RequestInit) => {
      calls.push({ url, init })
      if (url.includes('/bulk/upload')) return Promise.resolve(ok(calls.filter((c) => c.url.includes('/bulk/upload')).length === 1 ? batch1 : batch2))
      return Promise.resolve(ok({ items: [], page: 0, size: 10, totalItems: 0, totalPages: 1 }))
    }))

    await chooseControl('OS-SSH-ROOT-LOGIN')
    await userEvent.selectOptions(screen.getByLabelText('Environment'), 'PROD')
    await userEvent.upload(screen.getByLabelText('Choose files') as HTMLInputElement, [makeFile('ok.txt', 'a'), makeFile('flaky.txt', 'b')])
    await userEvent.click(screen.getByRole('button', { name: 'Submit bulk' }))

    expect(await screen.findByText('Not stored (1):', { exact: false })).toBeInTheDocument()
    const first = calls.find((c) => c.url.includes('/bulk/upload'))!
    expect(first.init.headers).toMatchObject({ 'X-Pramaan-Role': 'APP', 'X-Pramaan-User': 'App Owner' })
    const form1 = first.init.body as FormData
    expect(form1.get('environment')).toBe('PROD')
    expect(form1.get('collectedBy')).toBe('App Owner')
    expect(form1.getAll('files')).toHaveLength(2)

    await userEvent.click(screen.getByRole('button', { name: 'Retry 1 failed file' }))
    await screen.findByText('b2')
    const retryCall = calls.filter((c) => c.url.includes('/bulk/upload'))[1]
    const form2 = retryCall.init.body as FormData
    expect(form2.get('retryOfBatchId')).toBe('b1')
    expect((form2.getAll('files') as File[]).map((f) => f.name)).toEqual(['flaky.txt'])
  })

  it('UC02: a role that may not upload sees the reason and cannot submit', async () => {
    sessionStorage.setItem(PERSONA_STORAGE_KEY, 'AUDITOR')
    renderOffline(<BulkUpload />)
    expect(await screen.findByRole('alert')).toHaveTextContent(/cannot upload evidence/i)
    expect(screen.getByRole('button', { name: 'Submit bulk' })).toBeDisabled()
    // the whole form is disabled, not only the submit button
    expect(screen.getByLabelText('Application')).toBeDisabled()
    expect(screen.getByLabelText('Technology')).toBeDisabled()
    expect(screen.getByLabelText('Choose files')).toBeDisabled()
  })

  it('UC02: an App Owner can use the form', async () => {
    sessionStorage.setItem(PERSONA_STORAGE_KEY, 'APP')
    renderOffline(<BulkUpload />)
    expect(await screen.findByLabelText('Application')).toBeEnabled()
    expect(screen.getByLabelText('Choose files')).toBeEnabled()
    expect(screen.queryByText(/cannot upload evidence/i)).not.toBeInTheDocument()
  })

  it('browse dialog: files chosen via the input appear in the list and are submitted (input is reset after the change)', async () => {
    renderOffline(<BulkUpload />)
    await chooseControl('OS-SSH-ROOT-LOGIN')
    const input = screen.getByLabelText('Choose files') as HTMLInputElement
    const a = makeFile('browse-a.txt', 'a')
    const b = makeFile('browse-b.txt', 'b')
    // emulate a real browser: the handler's reset empties the live FileList before React runs the state updater
    Object.defineProperty(input, 'files', { configurable: true, value: [a, b] })
    fireEvent.change(input)
    expect(screen.getByText('browse-a.txt')).toBeInTheDocument()
    expect(screen.getByText('browse-b.txt')).toBeInTheDocument()
    expect(screen.queryByText('No files selected.')).not.toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Submit bulk' }))
    expect(await screen.findByText('Result')).toBeInTheDocument()
    expect(screen.getAllByText('browse-a.txt').length).toBeGreaterThan(0)
    expect(screen.getByText('Received').closest('.card')?.textContent).toContain('2')
  })

  it('browse dialog: re-selecting the same file after the reset still shows it', async () => {
    renderOffline(<BulkUpload />)
    await userEvent.upload(screen.getByLabelText('Choose files') as HTMLInputElement, makeFile('again.txt', 'x'))
    expect(screen.getByText('again.txt')).toBeInTheDocument()
    await userEvent.click(screen.getByLabelText('Remove again.txt'))
    expect(screen.queryByText('again.txt')).not.toBeInTheDocument()
    await userEvent.upload(screen.getByLabelText('Choose files') as HTMLInputElement, makeFile('again.txt', 'x'))
    expect(screen.getByText('again.txt')).toBeInTheDocument()
  })

  it('Recent batches: the Details panel opens directly under its own row, one at a time, and Hide removes it', async () => {
    const run = (id: string, status: string) => ({
      runId: id, trigger: 'BULK', status, applications: ['net-banking'], frameworks: ['PCI_DSS'], sources: ['BULK_UPLOAD'],
      received: 1, ingested: 1, duplicates: 0, failed: 0, perSource: { [`${id}.txt`]: 'outcome=CREATED version=1' },
      createdAt: '2026-10-13T05:00:00Z',
    })
    const items = [run('aaaaaaaa-1', 'FAILED'), run('bbbbbbbb-2', 'COMPLETED'), run('cccccccc-3', 'COMPLETED')]
    const body = JSON.stringify({ items, page: 0, size: 10, totalItems: 3, totalPages: 1 })
    vi.stubGlobal('fetch', vi.fn((url: string) => Promise.resolve({ ok: true, status: 200, statusText: 'OK', text: () => Promise.resolve(url.includes('/scheduler/runs') ? body : '[]') })))
    render(<MemoryRouter><BulkUpload /></MemoryRouter>)

    await screen.findByText('aaaaaaaa')
    const rowOf = (prefix: string) => screen.getByText(prefix).closest('tr') as HTMLElement
    const panels = () => document.querySelectorAll('tr.batch-detail-row')
    const buttons = () => screen.getAllByRole('button', { name: /^(Details|Hide)$/ })
    expect(panels()).toHaveLength(0)
    buttons().forEach((b) => expect(b).toHaveAttribute('aria-expanded', 'false'))

    await userEvent.click(buttons()[1])
    expect(panels()).toHaveLength(1)
    expect(rowOf('bbbbbbbb').nextElementSibling).toBe(panels()[0])
    expect(rowOf('bbbbbbbb').nextElementSibling).toHaveClass('batch-detail-row')
    // nothing is rendered after the last batch row
    expect(rowOf('cccccccc').nextElementSibling).toBeNull()
    expect(buttons()[1]).toHaveTextContent('Hide')
    expect(buttons()[1]).toHaveAttribute('aria-expanded', 'true')

    // opening another batch closes the first
    await userEvent.click(buttons()[2])
    expect(panels()).toHaveLength(1)
    expect(rowOf('bbbbbbbb').nextElementSibling).not.toHaveClass('batch-detail-row')
    expect(rowOf('cccccccc').nextElementSibling).toBe(panels()[0])
    expect(buttons()[1]).toHaveAttribute('aria-expanded', 'false')

    await userEvent.click(screen.getByRole('button', { name: 'Hide' }))
    expect(panels()).toHaveLength(0)
    expect(rowOf('cccccccc').nextElementSibling).toBeNull()
  })

  it('drop zone opens the file input with Enter and Space', async () => {
    renderOffline(<BulkUpload />)
    const input = screen.getByLabelText('Choose files') as HTMLInputElement
    const click = vi.spyOn(input, 'click')
    screen.getByRole('button', { name: 'File drop zone' }).focus()
    await userEvent.keyboard('{Enter}')
    await userEvent.keyboard(' ')
    expect(click).toHaveBeenCalledTimes(2)
  })
})
