import { afterEach, describe, expect, it, vi } from 'vitest'
import { screen } from '@testing-library/react'
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
})
