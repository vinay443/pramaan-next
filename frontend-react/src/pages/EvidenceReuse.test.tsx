import { afterEach, describe, expect, it, vi } from 'vitest'
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { EvidenceReuse } from './EvidenceReuse'
import { renderOffline } from '../test/render'
import { isDataSourceBannerSuppressed } from '../api/dataSource'
import { getEvidenceReuse, searchEvidenceReuse } from '../api/endpoints'

afterEach(() => vi.unstubAllGlobals())

const matchCount = () => {
  const el = screen.getByText('Matches', { selector: '.stat-label' })
  return Number(el.parentElement?.querySelector('.stat-value')?.textContent)
}

describe('EvidenceReuse - Find similar evidence (self-contained demo data)', () => {
  it('by-evidence-type mode starts empty and searches on selection', async () => {
    renderOffline(<EvidenceReuse />)

    const type = screen.getByLabelText('Evidence type')
    expect(type).toHaveValue('')
    expect(screen.queryByRole('button', { name: 'Search' })).not.toBeInTheDocument()
    expect(screen.getByText('Select an evidence type to find similar evidence')).toBeInTheDocument()
    expect(Array.from((type as HTMLSelectElement).options).map((o) => o.value)).toEqual([
      '',
      'HOST-CONFIG',
      'DB-CONFIG',
      'MIDDLEWARE-CONFIG',
      'TLS-SCAN',
      'CHANGE-TICKET',
      'CODE-REVIEW',
      'AGENT-SCAN',
      'GENERAL',
    ])

    await userEvent.selectOptions(type, 'TLS-SCAN')

    await screen.findByText('Indexed', { selector: '.stat-label' })
    expect(screen.getByText('mock-embed:v1(dim=256)')).toBeInTheDocument()
    expect(matchCount()).toBeGreaterThan(0)

    // clearing the type returns to the empty state
    await userEvent.selectOptions(type, '')
    expect(screen.getByText('Select an evidence type to find similar evidence')).toBeInTheDocument()
  })

  it('never calls the backend and shows no mock-fallback badge', async () => {
    renderOffline(<EvidenceReuse />)
    await userEvent.selectOptions(screen.getByLabelText('Evidence type'), 'HOST-CONFIG')
    await screen.findByText('Indexed', { selector: '.stat-label' })

    await userEvent.selectOptions(screen.getByLabelText('Mode'), 'evidence')
    await userEvent.selectOptions(screen.getByLabelText('Application'), 'payments')
    const evidence = screen.getByLabelText('Evidence') as HTMLSelectElement
    await waitFor(() => expect(evidence.options.length).toBeGreaterThan(1))
    await userEvent.selectOptions(evidence, evidence.options[1].value)
    await screen.findByText('Indexed', { selector: '.stat-label' })

    expect(fetch).not.toHaveBeenCalled()
    expect(screen.queryByText('Sample data (mock fallback)')).not.toBeInTheDocument()
  })

  it('hides the shell "Backend unavailable" banner only while this tab is mounted', async () => {
    renderOffline(<EvidenceReuse />)
    expect(isDataSourceBannerSuppressed()).toBe(true)

    await userEvent.click(screen.getByRole('button', { name: 'Browse by control' }))
    expect(isDataSourceBannerSuppressed()).toBe(false)

    await userEvent.click(screen.getByRole('button', { name: 'Find similar evidence' }))
    expect(isDataSourceBannerSuppressed()).toBe(true)
  })

  // Every preset returns rows at Broad strictness (the default), led by a record of that type.
  it.each([
    ['HOST-CONFIG', /OS-SSH-ROOT-LOGIN|OS-SSH-PASSWORD-AUTH/],
    ['DB-CONFIG', /DB-TLS-IN-TRANSIT|DB-AUDIT-LOGGING/],
    ['MIDDLEWARE-CONFIG', /MW-TLS-VERSION|MW-HSTS/],
    ['TLS-SCAN', /TLS-CERT-EXPIRY|TLS-CERT-TRUST|TLS-PROTOCOL-VERSION/],
    ['CHANGE-TICKET', /ITPP-CHG-02/],
    ['CODE-REVIEW', /DPSC-SDLC-04|PCI-DSS-6\.2/],
    ['AGENT-SCAN', /NET-FIREWALL-RULES/],
    ['GENERAL', /POLICY-ACCESS-REVIEW/],
  ])('preset %s returns matches at Broad strictness', async (type, expectedTop) => {
    renderOffline(<EvidenceReuse />)
    expect(screen.getByLabelText('Match strictness')).toHaveValue('broad')

    await userEvent.selectOptions(screen.getByLabelText('Evidence type'), type)

    await screen.findByText('Indexed', { selector: '.stat-label' })
    expect(matchCount()).toBeGreaterThan(0)
    expect(screen.queryByText(/No evidence above the similarity threshold/)).not.toBeInTheDocument()
    expect(screen.getAllByRole('row')[1]).toHaveTextContent(expectedTop)
  })

  it('has a believable corpus: 12+ rows possible, varied scores', async () => {
    renderOffline(<EvidenceReuse />)
    await userEvent.selectOptions(screen.getByLabelText('Number of results'), '20')
    await userEvent.selectOptions(screen.getByLabelText('Evidence type'), 'HOST-CONFIG')
    await screen.findByText('Indexed', { selector: '.stat-label' })

    expect(matchCount()).toBeGreaterThanOrEqual(10)
    const scores = screen
      .getAllByRole('row')
      .slice(1)
      .map((r) => within(r).getAllByRole('cell')[0].textContent)
    expect(new Set(scores).size).toBeGreaterThanOrEqual(6)
    // several applications and frameworks appear, not one repeated
    const apps = new Set(screen.getAllByRole('row').slice(1).map((r) => within(r).getAllByRole('cell')[1].textContent))
    expect(apps.size).toBe(3)
  })

  it('re-runs the type search when result count or match strictness changes, and strict never widens it', async () => {
    renderOffline(<EvidenceReuse />)
    await userEvent.selectOptions(screen.getByLabelText('Evidence type'), 'HOST-CONFIG')
    await userEvent.selectOptions(screen.getByLabelText('Number of results'), '20')
    await userEvent.selectOptions(screen.getByLabelText('Match strictness'), 'broad')

    await screen.findByText('Indexed', { selector: '.stat-label' })
    const broadCount = matchCount()

    await userEvent.selectOptions(screen.getByLabelText('Match strictness'), 'strict')
    await waitFor(() => expect(matchCount()).toBeLessThan(broadCount))
    expect(matchCount()).toBeGreaterThan(0)
  })

  it('by-evidence mode: application -> evidence cascade runs on selection, with exact-duplicate and flag badges', async () => {
    renderOffline(<EvidenceReuse />)
    await userEvent.selectOptions(screen.getByLabelText('Mode'), 'evidence')

    expect(screen.queryByLabelText('Evidence type')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Search' })).not.toBeInTheDocument()
    expect(screen.getByLabelText('Evidence')).toBeDisabled()

    await userEvent.selectOptions(screen.getByLabelText('Application'), 'net-banking')
    const evidence = screen.getByLabelText('Evidence') as HTMLSelectElement
    await waitFor(() => expect(evidence.options.length).toBeGreaterThan(1))
    const tls = Array.from(evidence.options).find((o) => o.text.includes('DB-TLS-IN-TRANSIT'))!
    await userEvent.selectOptions(evidence, tls.value)

    // ev-104 shares a SHA-256 with a payments record -> shown as an exact duplicate
    expect(await screen.findByText('Exact duplicates — 1')).toBeInTheDocument()
    expect(screen.getAllByText('Exact duplicate').length).toBeGreaterThan(0)
    expect(screen.getAllByText(/Cross-application|Same control/).length).toBeGreaterThan(0)

    // changing application clears the evidence choice and the result
    await userEvent.selectOptions(screen.getByLabelText('Application'), 'payments')
    expect(screen.getByLabelText('Evidence')).toHaveValue('')
    expect(screen.queryByText('Indexed', { selector: '.stat-label' })).not.toBeInTheDocument()
  })

  it('clicking a result row opens a detail modal with evidence fields, and Close dismisses it', async () => {
    renderOffline(<EvidenceReuse />)
    await userEvent.selectOptions(screen.getByLabelText('Evidence type'), 'TLS-SCAN')
    await screen.findByText('Indexed', { selector: '.stat-label' })

    await userEvent.click(screen.getAllByRole('row')[1])

    const dialog = await screen.findByRole('dialog', { name: 'Evidence detail' })
    for (const label of [
      'Evidence ID',
      'Application',
      'Framework',
      'Control',
      'Similarity score',
      'File name',
      'File type',
      'Uploaded',
      'SHA-256',
    ]) {
      expect(within(dialog).getByText(label, { selector: 'dt' })).toBeInTheDocument()
    }
    expect(within(dialog).getByText(/^ev-1\d\d$/)).toBeInTheDocument()
    expect(within(dialog).getByText(/\.json$/)).toBeInTheDocument()
    expect(within(dialog).getByText(/^[0-9a-f]{64}$/)).toBeInTheDocument()
    expect(within(dialog).getByText('Content preview')).toBeInTheDocument()

    await userEvent.click(within(dialog).getByRole('button', { name: 'Close' }))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })
})

describe('EvidenceReuse - API helpers keep their extended timeout', () => {
  // The Find-similar tab no longer calls these, but they remain exported and reindex-prone, so
  // the earlier 30s patience allowance must still be in place for any other caller.
  it('getEvidenceReuse / searchEvidenceReuse pass 30s to apiFetch; setTimeout delays are checked, not elapsed time', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('offline in test')))
    const spy = vi.spyOn(globalThis, 'setTimeout')
    try {
      await getEvidenceReuse('ev-001')
      expect(spy.mock.calls.map((c) => c[1])).toContain(30000)
      spy.mockClear()
      await searchEvidenceReuse('ssh root login')
      expect(spy.mock.calls.map((c) => c[1])).toContain(30000)
    } finally {
      spy.mockRestore()
    }
  })
})

describe('EvidenceReuse - Browse by control (still API-backed with mock fallback)', () => {
  it('starts empty, then shows frameworks + held evidence per selection', async () => {
    renderOffline(<EvidenceReuse />)
    await userEvent.click(screen.getByRole('button', { name: 'Browse by control' }))

    expect(await screen.findByText('Select a control to see reusable evidence')).toBeInTheDocument()

    // a control with no evidence held in the fixtures
    const select = await screen.findByLabelText('Control')
    await waitFor(() => expect(screen.getByRole('option', { name: /^MW-HSTS/ })).toBeInTheDocument())
    await userEvent.selectOptions(select, 'MW-HSTS')
    expect(await screen.findByText(/No evidence held for this control yet/)).toBeInTheDocument()

    await userEvent.selectOptions(select, 'OS-SSH-ROOT-LOGIN')
    expect(await screen.findByText('Existing evidence — 1')).toBeInTheDocument()
    expect(screen.getByText('Frameworks requiring OS-SSH-ROOT-LOGIN', { selector: 'h2' })).toBeInTheDocument()
    expect(screen.getByText('Evidence matched', { selector: '.stat-label' })).toBeInTheDocument()
    expect(screen.getByText('Frameworks unmapped', { selector: '.stat-label' })).toBeInTheDocument()
    // this tab still goes through the API (which is mocked offline here)
    expect(fetch).toHaveBeenCalled()
  })

  it('flags results that came from the mock fallback', async () => {
    renderOffline(<EvidenceReuse />)
    await userEvent.click(screen.getByRole('button', { name: 'Browse by control' }))
    expect(await screen.findByText('Sample data (mock fallback)')).toBeInTheDocument()
  })

  it('"Reuse for [framework]" tags an existing record and refreshes the row', async () => {
    renderOffline(<EvidenceReuse />)
    await userEvent.click(screen.getByRole('button', { name: 'Browse by control' }))

    // ev-002 is seeded tagged to only its primary framework (PCI_DSS)
    const select = await screen.findByLabelText('Control')
    await waitFor(() => expect(screen.getByRole('option', { name: /^DB-TLS-IN-TRANSIT/ })).toBeInTheDocument())
    await userEvent.selectOptions(select, 'DB-TLS-IN-TRANSIT')

    const reuse = await screen.findByRole('button', { name: 'Reuse for DPSC' })
    await userEvent.click(reuse)

    await waitFor(() =>
      expect(screen.queryByRole('button', { name: 'Reuse for DPSC' })).not.toBeInTheDocument(),
    )
  })
})
