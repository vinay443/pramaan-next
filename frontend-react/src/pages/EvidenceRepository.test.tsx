import { afterEach, describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import userEvent from '@testing-library/user-event'
import { EvidenceRepository } from './EvidenceRepository'
import { renderOffline } from '../test/render'

afterEach(() => vi.unstubAllGlobals())

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
})
