import { afterEach, describe, expect, it, vi } from 'vitest'
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Reports } from './Reports'
import { renderOffline } from '../test/render'

afterEach(() => vi.unstubAllGlobals())

describe('Reports (UC17)', () => {
  it('lists the report catalogue with JSON/CSV download links (mock)', async () => {
    renderOffline(<Reports />)
    expect(await screen.findByText(/^Reports —/)).toBeInTheDocument()
    expect(screen.getByText('pan-india')).toBeInTheDocument()
    expect(screen.getAllByRole('link', { name: 'CSV' }).length).toBeGreaterThan(0)
  })

  it('Regulatory Report tab renders a regulator-ready filing distinct from the catalogue (mock)', async () => {
    renderOffline(<Reports />)
    await userEvent.click(screen.getByRole('button', { name: 'Regulatory Report' }))

    expect(await screen.findByText('Regulatory Compliance Filing')).toBeInTheDocument()
    expect(screen.getByText(/^REG-/)).toBeInTheDocument()
    expect(screen.getByText('Per-framework filing', { selector: 'h2' })).toBeInTheDocument()
    expect(screen.getByText('Attestation', { selector: 'h2' })).toBeInTheDocument()
  })
})

describe('Reports — Evidence summary tab', () => {
  it('lists the new report in the catalogue (mock)', async () => {
    renderOffline(<Reports />)
    expect(await screen.findByText('Evidence summary — audit documentation')).toBeInTheDocument()
    expect(screen.getByText('evidence-summary')).toBeInTheDocument()
  })

  it('renders totals, controls with text pills, MISSING and FAIL rows, and expandable evidence links (mock)', async () => {
    renderOffline(<Reports />)
    await userEvent.click(screen.getByRole('button', { name: 'Evidence summary' }))

    expect(await screen.findByRole('heading', { name: 'Evidence summary — audit documentation' })).toBeInTheDocument()
    for (const label of ['Controls expected', 'With evidence', 'Missing', 'Evidence FAIL', 'Evidence WARNING', 'Stale evidence']) {
      expect(screen.getByText(label)).toBeInTheDocument()
    }
    expect(screen.getByText(/no AI\s+model is involved/)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'JSON' })).toHaveAttribute('href', expect.stringContaining('/reports/evidence-summary?format=json'))
    expect(screen.getByRole('link', { name: 'CSV' })).toHaveAttribute('href', expect.stringContaining('format=csv'))

    expect(screen.getAllByText('MISSING').length).toBeGreaterThan(0)
    expect(screen.getAllByText('no evidence').length).toBeGreaterThan(0)
    expect(screen.getAllByText('FAIL').length).toBeGreaterThan(0)

    const fail = Array.from(document.querySelectorAll('details')).find(
      (d) => d.textContent?.includes('TLS-CERT-EXPIRY') && d.textContent.includes('Application: payments'),
    ) as HTMLElement
    const link = fail.querySelector('a') as HTMLAnchorElement
    expect(link).toHaveAttribute('href', '/evidence/ev-004')
    expect(link.textContent).toContain('fails an automated check')
  })

  it('applies the Application filter to the report (mock)', async () => {
    renderOffline(<Reports />)
    await userEvent.click(screen.getByRole('button', { name: 'Evidence summary' }))
    await screen.findByRole('heading', { name: 'Evidence summary — audit documentation' })

    await userEvent.type(screen.getByLabelText('Application'), 'mobile-banking')

    await vi.waitFor(() => expect(screen.queryAllByText('Application: payments')).toHaveLength(0))
    expect(screen.getAllByText('Application: mobile-banking').length).toBeGreaterThan(0)
  })

  it('shows an error when the report cannot be loaded', async () => {
    renderOffline(<Reports />)
    vi.stubGlobal(
      'fetch',
      vi.fn().mockImplementation(() =>
        Promise.resolve(new Response(JSON.stringify({ message: 'Unknown application: zzz' }), { status: 404 })),
      ),
    )
    await userEvent.click(screen.getByRole('button', { name: 'Evidence summary' }))
    await userEvent.type(screen.getByLabelText('Application'), 'zzz')
    expect(await screen.findByText(/Unknown application/)).toBeInTheDocument()
  })
})
