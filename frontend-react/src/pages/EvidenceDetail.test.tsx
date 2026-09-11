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
