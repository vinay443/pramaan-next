import { afterEach, describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor, within } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import userEvent from '@testing-library/user-event'
import { EvidenceDetail } from './EvidenceDetail'
import { renderOffline } from '../test/render'

afterEach(() => vi.unstubAllGlobals())

/** Like renderOffline, but routed so useParams() resolves :id from the path. */
function renderAtEvidenceId(id: string) {
  vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('offline in test')))
  return render(
    <MemoryRouter initialEntries={[`/evidence/${id}`]}>
      <Routes>
        <Route path="/evidence/:id" element={<EvidenceDetail />} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('EvidenceDetail lifecycle (UC13)', () => {
  it('shows the lifecycle state, audit trail and advances it (mock)', async () => {
    renderOffline(<EvidenceDetail />)

    const life = (await screen.findByRole('heading', { name: 'Lifecycle' })).closest('.card') as HTMLElement
    expect(within(life).getByText('INGESTED')).toBeInTheDocument()

    await userEvent.click(within(life).getByRole('button', { name: 'SUBMIT' }))
    await waitFor(() => expect(within(life).getByText('SUBMITTED')).toBeInTheDocument())
    expect(within(life).getByText('SUBMIT')).toBeInTheDocument()
  })
})

describe('EvidenceDetail integrity badge (UC04)', () => {
  it('shows VERIFIED for an intact record', async () => {
    renderAtEvidenceId('ev-001')
    expect((await screen.findAllByText('VERIFIED')).length).toBeGreaterThan(0)
  })

  it('shows TAMPERED for a record whose stored hash no longer matches its object', async () => {
    renderAtEvidenceId('ev-006')
    expect((await screen.findAllByText('TAMPERED')).length).toBeGreaterThan(0)
  })
})

describe('EvidenceDetail evidence summary', () => {
  it('shows the rule-based summary automatically — no Generate button, no AI wording', async () => {
    renderAtEvidenceId('ev-004')

    expect(await screen.findByRole('heading', { name: 'Evidence summary' })).toBeInTheDocument()
    expect(await screen.findByText(/fails an automated check/)).toBeInTheDocument()
    expect(screen.getByText(/No AI model is involved/)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /generate summary/i })).not.toBeInTheDocument()
    expect(screen.queryByText(/AI summary|prompt digest|model-generated/i)).not.toBeInTheDocument()
  })
})

describe('EvidenceDetail download (UC01-FT003)', () => {
  it('links to the content endpoint for the latest version and for each version row', async () => {
    renderAtEvidenceId('ev-001')

    const latest = await screen.findByRole('link', { name: 'Download latest' })
    expect(latest.getAttribute('href')?.endsWith('/api/v1/evidence/ev-001/content')).toBe(true)

    const v = await screen.findByRole('link', { name: /^Download version [0-9]+$/ })
    const n = v.getAttribute('aria-label')?.replace('Download version ', '')
    expect(v.getAttribute('href')?.endsWith('/api/v1/evidence/ev-001/content?version=' + n)).toBe(true)
  })
})
