import { afterEach, describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import userEvent from '@testing-library/user-event'
import { EvidenceRepository } from './EvidenceRepository'
import { renderOffline } from '../test/render'
import { PERSONA_STORAGE_KEY } from './PersonaLogin'

afterEach(() => {
  vi.unstubAllGlobals()
  sessionStorage.removeItem(PERSONA_STORAGE_KEY)
})

describe('EvidenceRepository', () => {
  it('lists mock evidence and filters by application', async () => {
    renderOffline(<EvidenceRepository />)

    // all six mock rows visible via total count
    expect(await screen.findByText('Results — 6')).toBeInTheDocument()

    await userEvent.type(screen.getByLabelText('Application'), 'payments')

    await waitFor(() => expect(screen.getByText('Results — 3')).toBeInTheDocument())
    expect(screen.getByText('TLS-CERT-EXPIRY')).toBeInTheDocument()
    expect(screen.queryByText('OS-SSH-ROOT-LOGIN')).not.toBeInTheDocument()
  })

  it('filters by the technology facet (UC3) across scheduler- and upload-sourced evidence', async () => {
    renderOffline(<EvidenceRepository />)
    expect(await screen.findByText('Results — 6')).toBeInTheDocument()

    await userEvent.type(screen.getByLabelText('Technology'), 'postgresql')
    await waitFor(() => expect(screen.getByText('Results — 1')).toBeInTheDocument())
    expect(screen.getByText('DB-TLS-IN-TRANSIT')).toBeInTheDocument()
  })

  it('filters by the collection-method facet', async () => {
    renderOffline(<EvidenceRepository />)
    expect(await screen.findByText('Results — 6')).toBeInTheDocument()

    await userEvent.selectOptions(screen.getByLabelText('Collection method'), 'scheduled')
    await waitFor(() => expect(screen.getByText('Results — 5')).toBeInTheDocument())
  })

  it('shows a per-row integrity badge (UC04), flagging the tampered demo record', async () => {
    renderOffline(<EvidenceRepository />)
    expect(await screen.findByText('Results — 6')).toBeInTheDocument()

    expect(screen.getAllByText('VERIFIED').length).toBe(5)
    expect(screen.getByText('TAMPERED')).toBeInTheDocument()
  })

  it('offers "predefined-query" as a collection-method option (backend already supports it)', async () => {
    renderOffline(<EvidenceRepository />)
    const select = (await screen.findByLabelText('Collection method')) as HTMLSelectElement
    const options = [...select.options].map((o) => o.value)
    expect(options).toContain('predefined-query')

    await userEvent.selectOptions(select, 'predefined-query')
    expect(select.value).toBe('predefined-query')
  })

  it('details panel shows the upload metadata, full SHA-256 with a copy button, size and content type (FT004/FT006)', async () => {
    const sha = 'ab12cd34'.repeat(8)
    const row = {
      evidenceId: 'e1', evidenceKey: 'net-banking|PCI_DSS|MW-TLS-VERSION|BULK_UPLOAD|tls-report.txt',
      applicationSlug: 'net-banking', controlId: 'MW-TLS-VERSION', framework: 'PCI_DSS', sourceSystem: 'BULK_UPLOAD',
      currentVersion: 2, createdAt: '2026-10-13T05:00:00Z', updatedAt: '2026-10-13T06:00:00Z', integrityStatus: 'VERIFIED',
      tags: { name: 'net-banking_MW-TLS-VERSION_20261013_002', sourceTitle: 'tls-report.txt', environment: 'PROD', uploadedBy: 'App Owner',
        batchId: 'b-1234', frameworks: 'PCI_DSS,DPSC', technology: 'nginx', evidenceType: 'MIDDLEWARE-CONFIG', collectionMethod: 'bulk' },
      latest: { version: 2, sha256: sha, contentType: 'text/plain', sizeBytes: 2048, objectKey: 'k', collectedAt: '2026-10-13T06:30:00Z', collectedBy: 'App Owner', metadata: {} },
    }
    const body = JSON.stringify({ items: [row], page: 0, size: 20, totalItems: 1, totalPages: 1 })
    vi.stubGlobal('fetch', vi.fn(() => Promise.resolve({ ok: true, status: 200, statusText: 'OK', text: () => Promise.resolve(body) })))
    const writeText = vi.fn().mockResolvedValue(undefined)
    Object.defineProperty(navigator, 'clipboard', { configurable: true, value: { writeText } })
    render(<MemoryRouter><EvidenceRepository /></MemoryRouter>)

    // the File column tells bulk uploads apart
    expect(await screen.findByRole('cell', { name: 'tls-report.txt' })).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Show details' }))

    const panel = document.querySelector('dl.kv') as HTMLElement
    const value = (label: string) => within(panel).getByText(label, { selector: 'dt' }).nextElementSibling as HTMLElement
    expect(value('Environment')).toHaveTextContent('PROD')
    expect(value('Uploaded by')).toHaveTextContent('App Owner')
    expect(value('Batch ID')).toHaveTextContent('b-1234')
    expect(value('File')).toHaveTextContent('tls-report.txt')
    expect(value('Framework(s)')).toHaveTextContent('PCI_DSS,DPSC')
    expect(value('Technology')).toHaveTextContent('nginx')
    // date AND time, not just the day
    expect(value('Uploaded at').textContent).toMatch(/\d{1,2}:\d{2}/)
    expect(value('Size')).toHaveTextContent('2.0 KB')
    expect(value('Content type')).toHaveTextContent('text/plain')
    expect(value('SHA-256')).toHaveTextContent(sha)

    await userEvent.click(within(panel).getByRole('button', { name: 'Copy SHA-256' }))
    expect(writeText).toHaveBeenCalledWith(sha)
    expect(await within(panel).findByText('Copied')).toBeInTheDocument()
  })

  describe('FT006 metadata edit', () => {
    const row = {
      evidenceId: 'e1', evidenceKey: 'net-banking|PCI_DSS|MW-TLS-VERSION|BULK_UPLOAD|tls-report.txt',
      applicationSlug: 'net-banking', controlId: 'MW-TLS-VERSION', framework: 'PCI_DSS', sourceSystem: 'BULK_UPLOAD',
      currentVersion: 1, createdAt: '2026-10-13T05:00:00Z', updatedAt: '2026-10-13T06:00:00Z', integrityStatus: 'VERIFIED',
      lifecycleState: 'APPROVED',
      tags: { name: 'net-banking_MW-TLS-VERSION_UAT_20261013_001', sourceTitle: 'tls-report.txt', environment: 'UAT',
        frameworks: 'PCI_DSS', technology: 'nginx', evidenceType: 'MIDDLEWARE-CONFIG' },
      latest: { version: 1, sha256: 'ab'.repeat(32), contentType: 'text/plain', sizeBytes: 10, objectKey: 'k', collectedAt: '2026-10-13T06:30:00Z', collectedBy: 'App Owner', metadata: {} },
    }
    const note = JSON.stringify({ reason: 'wrong tech', role: 'APP', changes: { technology: { old: 'apache', new: 'nginx' } } })
    const lifecycle = {
      evidenceId: 'e1', applicationSlug: 'net-banking', controlId: 'MW-TLS-VERSION', state: 'DRAFT', effectiveState: 'DRAFT',
      expired: false, retentionDays: 365, currentVersion: 1,
      history: [
        { toState: 'DRAFT', action: 'INGESTED', actor: 'App Owner', occurredAt: '2026-10-13T06:30:00Z' },
        { fromState: 'APPROVED', toState: 'DRAFT', action: 'METADATA_EDITED', actor: 'App Owner', note, occurredAt: '2026-10-14T06:30:00Z' },
      ],
    }

    function stub(calls: Array<{ url: string; init?: { method?: string; body?: string; headers?: Record<string, string> } }>) {
      vi.stubGlobal('fetch', vi.fn((url: string, init?: { method?: string; body?: string; headers?: Record<string, string> }) => {
        calls.push({ url: String(url), init })
        const reply = (b: unknown) =>
          Promise.resolve({ ok: true, status: 200, statusText: 'OK', text: () => Promise.resolve(JSON.stringify(b)) })
        if (String(url).includes('/metadata')) return reply({ ...row, lifecycleState: 'DRAFT' })
        if (String(url).includes('/lifecycle')) return reply(lifecycle)
        return reply({ items: [row], page: 0, size: 20, totalItems: 1, totalPages: 1 })
      }))
    }

    async function open() {
      render(<MemoryRouter><EvidenceRepository /></MemoryRouter>)
      await userEvent.click(await screen.findByRole('button', { name: 'Show details' }))
    }

    it('shows Edit only to APP and ADMIN, and lists the metadata history read-only', async () => {
      stub([])
      sessionStorage.setItem(PERSONA_STORAGE_KEY, 'AUDITOR')
      await open()
      expect(await screen.findByText('Metadata history')).toBeInTheDocument()
      expect(await screen.findByText(/technology: apache → nginx/)).toBeInTheDocument()
      expect(screen.getByText(/reason: wrong tech/)).toBeInTheDocument()
      expect(screen.queryByRole('button', { name: 'Edit metadata' })).not.toBeInTheDocument()
    })

    it.each(['APP', 'ADMIN'])('%s can edit: a reason is mandatory and the PATCH carries only the changed fields', async (code) => {
      const calls: Array<{ url: string; init?: { method?: string; body?: string; headers?: Record<string, string> } }> = []
      stub(calls)
      sessionStorage.setItem(PERSONA_STORAGE_KEY, code)
      await open()
      await userEvent.click(await screen.findByRole('button', { name: 'Edit metadata' }))

      const tech = screen.getByLabelText('Edit technology')
      await userEvent.clear(tech)
      await userEvent.type(tech, 'Tomcat')
      await userEvent.click(screen.getByRole('button', { name: 'Save changes' }))
      expect(await screen.findByRole('alert')).toHaveTextContent(/reason is required/i)
      expect(calls.some((c) => c.init?.method === 'PATCH')).toBe(false)

      await userEvent.type(screen.getByLabelText('Reason for the change'), 'fix the technology')
      await userEvent.click(screen.getByRole('button', { name: 'Save changes' }))
      expect(await screen.findByText(/Saved. Lifecycle state: DRAFT/)).toBeInTheDocument()

      const patch = calls.find((c) => c.init?.method === 'PATCH')!
      expect(patch.url).toContain('/api/v1/evidence/e1/metadata')
      expect(JSON.parse(patch.init!.body!)).toEqual({ changes: { technology: 'Tomcat' }, reason: 'fix the technology' })
      expect(patch.init!.headers).toMatchObject({ 'X-Pramaan-Role': code })
    })
  })
})
