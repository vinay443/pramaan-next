import { afterEach, describe, expect, it, vi } from 'vitest'
import { screen } from '@testing-library/react'
import { Comparison } from './Comparison'
import { renderOffline } from '../test/render'

afterEach(() => vi.unstubAllGlobals())

describe('Comparison (UC14)', () => {
  it('renders the per-framework matrix and a gap list across applications (mock)', async () => {
    renderOffline(<Comparison />)
    expect(await screen.findByText(/Control status matrix/)).toBeInTheDocument()
    expect(screen.getByText(/^Gaps —/)).toBeInTheDocument()
  })
})
