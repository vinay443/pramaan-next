import { afterEach, describe, expect, it, vi } from 'vitest'
import { screen } from '@testing-library/react'
import { Trend } from './Trend'
import { renderOffline } from '../test/render'

afterEach(() => vi.unstubAllGlobals())

describe('Trend (UC19)', () => {
  it('shows the compliance trend points and closure metrics (mock)', async () => {
    renderOffline(<Trend />)
    expect(await screen.findByText('Compliance % over time')).toBeInTheDocument()
    expect(screen.getByText('Approvals', { selector: '.stat-label' })).toBeInTheDocument()
    expect(screen.getByRole('img', { name: 'compliance trend' })).toBeInTheDocument()
    // headline "Current compliance" is the live rollup, not the last synthetic snapshot
    const card = screen.getByText('Current compliance').closest('.card') as HTMLElement
    expect(card.textContent).toContain('live rollup')
  })
})
