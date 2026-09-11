import { afterEach, describe, expect, it, vi } from 'vitest'
import { screen, within } from '@testing-library/react'
import { NationalDashboard } from './NationalDashboard'
import { renderOffline } from '../test/render'

afterEach(() => vi.unstubAllGlobals())

describe('NationalDashboard (national rollup, distinct from Enterprise.tsx)', () => {
  it('shows regions ranked by gap to the national average and a region x framework breakdown (mock)', async () => {
    renderOffline(<NationalDashboard />)

    const ranked = (
      await screen.findByText('Regions ranked by gap to national average (furthest-behind first)')
    ).closest('.card') as HTMLElement
    expect(within(ranked).getByText('North')).toBeInTheDocument()

    const breakdown = screen.getByText('Region x framework breakdown').closest('.card') as HTMLElement
    expect(within(breakdown).getAllByRole('row').length).toBeGreaterThan(1)
  })
})
