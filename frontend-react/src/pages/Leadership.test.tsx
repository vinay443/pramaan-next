import { afterEach, describe, expect, it, vi } from 'vitest'
import { screen, within } from '@testing-library/react'
import { Leadership } from './Leadership'
import { renderOffline } from '../test/render'

afterEach(() => vi.unstubAllGlobals())

describe('Leadership (UC11)', () => {
  it('rolls up portfolio compliance, check verdicts and per-application posture (mock)', async () => {
    renderOffline(<Leadership />)

    expect(await screen.findByText('Portfolio compliance')).toBeInTheDocument()

    // per-application table lists every seeded application
    const byApp = screen.getByText(/By application —/).closest('.card') as HTMLElement
    expect(within(byApp).getByText('Net Banking')).toBeInTheDocument()
    expect(within(byApp).getByText('Payments')).toBeInTheDocument()

    // actual check verdicts are surfaced
    const verdicts = screen.getByText('Check verdicts (actual results)').closest('.card') as HTMLElement
    expect(within(verdicts).getByText('PASS', { selector: '.stat-label' })).toBeInTheDocument()
  })

  it('drills each application row down to that application, not a bare /compliance (UC-P2-5)', async () => {
    renderOffline(<Leadership />)
    await screen.findByText('Portfolio compliance')

    const byApp = screen.getByText(/By application —/).closest('.card') as HTMLElement
    expect(within(byApp).getByRole('link', { name: 'Payments' })).toHaveAttribute(
      'href',
      '/compliance?applicationSlug=payments',
    )
    expect(within(byApp).getByRole('link', { name: 'Net Banking' })).toHaveAttribute(
      'href',
      '/compliance?applicationSlug=net-banking',
    )
  })
})
