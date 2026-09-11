import { afterEach, describe, expect, it, vi } from 'vitest'
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
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

  it('Regulatory Report tab renders a regulator-ready filing distinct from the catalogue (mock)', async () => {
    renderOffline(<Reports />)
    await userEvent.click(screen.getByRole('button', { name: 'Regulatory Report' }))

    expect(await screen.findByText('Regulatory Compliance Filing')).toBeInTheDocument()
    expect(screen.getByText(/^REG-/)).toBeInTheDocument()
    expect(screen.getByText('Per-framework filing', { selector: 'h2' })).toBeInTheDocument()
    expect(screen.getByText('Attestation', { selector: 'h2' })).toBeInTheDocument()
  })
})
