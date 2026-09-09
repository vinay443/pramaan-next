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
})
