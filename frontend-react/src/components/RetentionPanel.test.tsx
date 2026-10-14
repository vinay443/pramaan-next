import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { RetentionPanel } from './RetentionPanel'
import { PERSONA_STORAGE_KEY } from '../pages/PersonaLogin'

const run = {
  id: 'r1', mode: 'EXECUTE', triggeredBy: 'Platform Admin', startedAt: '2026-10-09T10:00:00Z', finishedAt: '2026-10-09T10:00:01Z',
  retentionDays: 0, application: 'payments', scanned: 9, archived: 4, skipped: 5,
}
const item = {
  evidenceId: 'e1', applicationSlug: 'payments', controlId: 'PCI-C6', framework: 'PCI_DSS',
  collectedAt: '2026-01-01T00:00:00Z', expiresAt: '2027-01-01T00:00:00Z',
}

function stubBackend(calls: Array<{ url: string; body?: string }>) {
  vi.stubGlobal(
    'fetch',
    vi.fn((url: string, init?: { method?: string; body?: string }) => {
      calls.push({ url: String(url), body: init?.body })
      const reply = (body: unknown) =>
        Promise.resolve({ ok: true, status: 200, statusText: 'OK', text: async () => JSON.stringify(body) })
      if (String(url).endsWith('/api/v1/retention/policy')) return reply({ retentionDays: 365, version: 2, updatedBy: 'Platform Admin' })
      if (String(url).endsWith('/api/v1/retention/runs')) return reply([run])
      if (String(url).endsWith('/api/v1/retention/archive')) {
        const mode = JSON.parse(init?.body ?? '{}').mode
        return reply({ runId: 'r2', mode, retentionDays: 365, scanned: 9, eligible: 1, archived: mode === 'EXECUTE' ? 1 : 0, skipped: 8, items: [item] })
      }
      return Promise.reject(new TypeError('offline in test'))
    }),
  )
}

beforeEach(() => sessionStorage.removeItem(PERSONA_STORAGE_KEY))
afterEach(() => {
  vi.unstubAllGlobals()
  cleanup()
})

describe('RetentionPanel', () => {
  it('lets Admin edit the policy, preview, confirm and archive, and shows the run history', async () => {
    sessionStorage.setItem(PERSONA_STORAGE_KEY, 'ADMIN')
    const calls: Array<{ url: string; body?: string }> = []
    stubBackend(calls)
    render(<RetentionPanel />)

    expect(await screen.findByDisplayValue('365')).toBeEnabled()
    expect(await screen.findByText('Platform Admin', { selector: 'td' })).toBeInTheDocument() // history row
    expect(screen.getByText('EXECUTE', { selector: 'td' })).toBeInTheDocument()

    await userEvent.type(screen.getByLabelText('Archival scope'), 'payments')
    await userEvent.click(screen.getByRole('button', { name: 'Preview eligible' }))
    expect(await screen.findByText('PCI-C6')).toBeInTheDocument()
    expect(JSON.parse(calls.filter((c) => c.url.endsWith('/archive'))[0].body ?? '{}')).toEqual({ mode: 'PREVIEW', application: 'payments' })

    await userEvent.click(screen.getByRole('button', { name: 'Archive eligible evidence' }))
    expect(screen.getByRole('alertdialog')).toHaveTextContent('This archives 1 evidence records. Nothing is deleted.')
    await userEvent.click(screen.getByRole('button', { name: 'Confirm archive' }))
    await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent('Archived 1 evidence record. Nothing was deleted.'))
    expect(JSON.parse(calls.filter((c) => c.url.endsWith('/archive')).pop()?.body ?? '{}').mode).toBe('EXECUTE')
  })

  it('an unscoped run sends the explicit allApplications scope (the backend refuses an unscoped EXECUTE)', async () => {
    sessionStorage.setItem(PERSONA_STORAGE_KEY, 'ADMIN')
    const calls: Array<{ url: string; body?: string }> = []
    stubBackend(calls)
    render(<RetentionPanel />)
    await screen.findByDisplayValue('365')

    await userEvent.click(screen.getByRole('button', { name: 'Preview eligible' }))
    expect(await screen.findByText('PCI-C6')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Archive eligible evidence' }))
    await userEvent.click(screen.getByRole('button', { name: 'Confirm archive' }))
    await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent('Archived 1 evidence record.'))
    const bodies = calls.filter((c) => c.url.endsWith('/archive')).map((c) => JSON.parse(c.body ?? '{}'))
    expect(bodies).toEqual([
      { mode: 'PREVIEW', allApplications: true },
      { mode: 'EXECUTE', allApplications: true },
    ])
  })

  it('is read-only for an Auditor: policy shown, no save and no archive button', async () => {
    sessionStorage.setItem(PERSONA_STORAGE_KEY, 'AUDITOR')
    stubBackend([])
    render(<RetentionPanel />)

    expect(await screen.findByDisplayValue('365')).toBeDisabled()
    expect(screen.getByText(/only Admin changes the policy/)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Archive eligible evidence' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Save' })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Preview eligible' })).toBeInTheDocument()
    expect(await screen.findByText('Platform Admin', { selector: 'td' })).toBeInTheDocument() // can still read history
  })
})
