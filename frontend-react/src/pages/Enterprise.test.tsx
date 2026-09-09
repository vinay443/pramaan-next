import { afterEach, describe, expect, it, vi } from 'vitest'
import { screen, within } from '@testing-library/react'
import { Enterprise } from './Enterprise'
import { renderOffline } from '../test/render'

afterEach(() => vi.unstubAllGlobals())

describe('Enterprise & National (UC16 + UC20)', () => {
  it('shows business-unit / criticality cuts and the national region table (mock)', async () => {
    renderOffline(<Enterprise />)
    expect(await screen.findByText('By business unit')).toBeInTheDocument()
    expect(screen.getByText('By criticality')).toBeInTheDocument()
    const national = screen.getByText('National / pan-India').closest('.card') as HTMLElement
    expect(within(national).getByText('North')).toBeInTheDocument()
  })
})
