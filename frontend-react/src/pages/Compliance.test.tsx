import { afterEach, describe, expect, it, vi } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Compliance } from './Compliance'
import { renderOffline } from '../test/render'

afterEach(() => vi.unstubAllGlobals())

describe('Compliance', () => {
  it('rolls up posture per control and per framework (mock)', async () => {
    renderOffline(<Compliance />)

    expect(await screen.findByText('Controls — 7')).toBeInTheDocument()
    expect(screen.getByText('Compliance', { selector: '.stat-label' })).toBeInTheDocument()
    // net-banking OS-SSH-ROOT-LOGIN has a PASS verdict in results.json
    expect(screen.getAllByText('COMPLIANT').length).toBeGreaterThan(0)

    await userEvent.selectOptions(screen.getByLabelText('Application'), 'payments')
    // payments TLS-CERT-EXPIRY / ITPP-CHG-02 have FAIL verdicts
    await waitFor(() => expect(screen.getAllByText('NON_COMPLIANT').length).toBeGreaterThan(0))
  })

  it('honours ?applicationSlug from the Leadership drill-down (UC-P2-5)', async () => {
    renderOffline(<Compliance />, '/compliance?applicationSlug=payments')

    await screen.findByText(/^Controls — /)
    expect((screen.getByLabelText('Application') as HTMLSelectElement).value).toBe('payments')
    // payments TLS-CERT-EXPIRY / ITPP-CHG-02 have FAIL verdicts; net-banking does not
    await waitFor(() => expect(screen.getAllByText('NON_COMPLIANT').length).toBeGreaterThan(0))
  })

  it('compliance % states its own numerator and denominator', async () => {
    renderOffline(<Compliance />)
    await screen.findByText('Controls — 7')

    const pct = screen.getByText('Compliance', { selector: '.stat-label' }).closest('.stat') as HTMLElement
    expect(pct.querySelector('.stat-hint')?.textContent).toMatch(/^\d+ compliant \/ \d+ expected$/)
  })
})
