import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { EvidenceSummaryPanel } from './EvidenceSummaryPanel'
import { mockSummary } from '../api/mocks'
import { renderOffline } from '../test/render'

const getSummary = vi.hoisted(() => vi.fn())
vi.mock('../api/endpoints', async (orig) => ({
  ...(await orig<typeof import('../api/endpoints')>()),
  getEvidenceSummary: getSummary,
}))

beforeEach(() => {
  getSummary.mockImplementation((id: string, v?: number) => Promise.resolve(mockSummary(id, v)))
})
afterEach(() => {
  vi.unstubAllGlobals()
  getSummary.mockReset()
})

describe('EvidenceSummaryPanel', () => {
  it('renders automatically on mount — no button press, no Generate button', async () => {
    renderOffline(<EvidenceSummaryPanel evidenceId="ev-001" />)

    expect(screen.getByRole('heading', { name: 'Evidence summary' })).toBeInTheDocument()
    expect(await screen.findByText(/SSH root login disabled \(OS-SSH-ROOT-LOGIN, C-SITE\) — meets the automated checks/)).toBeInTheDocument()
    expect(getSummary).toHaveBeenCalledWith('ev-001', undefined)
    expect(screen.queryByRole('button', { name: /generate/i })).not.toBeInTheDocument()
  })

  it('shows the verdict as a text label together with the headline', async () => {
    renderOffline(<EvidenceSummaryPanel evidenceId="ev-004" />)

    const headline = await screen.findByText(/fails an automated check/)
    expect(headline).toHaveTextContent('TLS-02: observed 12, expected days_to_expiry>30')
    expect(screen.getAllByText('FAIL').length).toBeGreaterThan(0)
    expect(screen.getByText('Simulated data')).toBeInTheDocument()
  })

  it('lists key facts with where each was found, and passes the version through', async () => {
    renderOffline(<EvidenceSummaryPanel evidenceId="ev-001" version={2} />)

    expect(await screen.findByRole('columnheader', { name: 'Found at' })).toBeInTheDocument()
    expect(screen.getByText('check OS-LNX-02')).toBeInTheDocument()
    expect(screen.getAllByText('/findings/0').length).toBeGreaterThan(0)
    expect(screen.getAllByText('record').length).toBeGreaterThan(0)
    expect(getSummary).toHaveBeenCalledWith('ev-001', 2)
  })

  it('shows the first 12 facts and expands with "Show all N facts"', async () => {
    const base = mockSummary('ev-001')
    const many = Array.from({ length: 20 }, (_, i) => ({ label: `fact ${i}`, value: `v${i}`, pointer: `/f/${i}` }))
    getSummary.mockResolvedValue({ ...base, facts: many })
    renderOffline(<EvidenceSummaryPanel evidenceId="ev-001" />)

    const table = (await screen.findAllByRole('table'))[0]
    expect(within(table).getAllByRole('row')).toHaveLength(1 + 12)

    await userEvent.click(screen.getByRole('button', { name: 'Show all 20 facts' }))
    expect(within(screen.getAllByRole('table')[0]).getAllByRole('row')).toHaveLength(1 + 20)
    expect(screen.getByRole('button', { name: 'Show fewer facts' })).toBeInTheDocument()
  })

  it('says so when there are no facts', async () => {
    getSummary.mockResolvedValue({ ...mockSummary('ev-001'), facts: [] })
    renderOffline(<EvidenceSummaryPanel evidenceId="ev-001" />)
    expect(await screen.findByText('No extractable facts.')).toBeInTheDocument()
  })

  it('shows the checks table only when checks exist', async () => {
    const { unmount } = renderOffline(<EvidenceSummaryPanel evidenceId="ev-001" />)
    expect(await screen.findByRole('heading', { name: 'Automated checks' })).toBeInTheDocument()
    expect(screen.getByRole('columnheader', { name: 'Expected' })).toBeInTheDocument()
    expect(screen.getByText('OS-LNX-04')).toBeInTheDocument()
    unmount()

    renderOffline(<EvidenceSummaryPanel evidenceId="ev-008" />)
    await screen.findByText(/collected, not assessed \(no automated rule\)/)
    expect(screen.queryByRole('heading', { name: 'Automated checks' })).not.toBeInTheDocument()
    expect(screen.getByText('NOT ASSESSED')).toBeInTheDocument()
  })

  it('renders findings and the limits list', async () => {
    renderOffline(<EvidenceSummaryPanel evidenceId="ev-008" />)

    const findings = await screen.findByRole('list', { name: 'Findings' })
    expect(within(findings).getByText(/No automated rule applies to this evidence\./)).toBeInTheDocument()
    expect(within(findings).getByText('INFO')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'What this summary could not see' })).toBeInTheDocument()
    expect(within(screen.getByRole('list', { name: 'Limits' })).getByText('Content is not machine-readable here.')).toBeInTheDocument()
    expect(screen.getByText('Missing tags: technology')).toBeInTheDocument()
  })

  it('spells out MISMATCH and STALE as text', async () => {
    renderOffline(<EvidenceSummaryPanel evidenceId="ev-006" />)

    const health = await screen.findByLabelText('Evidence health')
    expect(within(health).getByText('MISMATCH')).toBeInTheDocument()
    expect(within(health).getByText('STALE')).toBeInTheDocument()
    expect(screen.getAllByText('NOT APPLICABLE').length).toBeGreaterThan(0) // verdict + check status
    expect(screen.getAllByText(/does not match the recorded SHA-256/).length).toBeGreaterThan(0)
  })

  it('shows the control-library description and the other frameworks it is relevant to', async () => {
    renderOffline(<EvidenceSummaryPanel evidenceId="ev-002" />)

    expect(await screen.findByText('Database connections must be encrypted in transit.')).toBeInTheDocument()
    expect(screen.getByText('Also relevant to')).toBeInTheDocument()
    expect(screen.getByText('DPSC')).toBeInTheDocument()
  })

  it('states that no AI model is involved and carries none of the old wording', async () => {
    const { container } = renderOffline(<EvidenceSummaryPanel evidenceId="ev-003" />)

    expect(await screen.findByText(/No AI model is involved: the same evidence always produces the same summary\./)).toBeInTheDocument()
    expect(screen.getByText(/Generated automatically by fixed rules from the stored content of version 3/)).toBeInTheDocument()
    for (const old of ['AI summary', 'Generate summary', 'prompt digest', 'model-generated', '[mock-ai]']) {
      expect(container.textContent).not.toContain(old)
    }
  })

  it('Refresh fetches the summary again', async () => {
    renderOffline(<EvidenceSummaryPanel evidenceId="ev-001" />)
    await screen.findByText(/meets the automated checks/)
    expect(getSummary).toHaveBeenCalledTimes(1)

    await userEvent.click(screen.getByRole('button', { name: 'Refresh' }))

    await vi.waitFor(() => expect(getSummary).toHaveBeenCalledTimes(2))
    expect(await screen.findByRole('button', { name: 'Refresh' })).toBeEnabled()
  })

  it('shows the error when the summary cannot be loaded', async () => {
    getSummary.mockRejectedValue(new Error('Evidence x has no version 9'))
    renderOffline(<EvidenceSummaryPanel evidenceId="ev-001" version={9} />)

    expect(await screen.findByRole('alert')).toHaveTextContent('Evidence x has no version 9')
  })
})
