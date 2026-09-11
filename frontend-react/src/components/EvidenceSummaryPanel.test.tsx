import { afterEach, describe, expect, it, vi } from 'vitest'
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { EvidenceSummaryPanel } from './EvidenceSummaryPanel'
import { renderOffline } from '../test/render'

afterEach(() => vi.unstubAllGlobals())

describe('EvidenceSummaryPanel', () => {
  it('generates a grounded, deterministic summary on demand (mock)', async () => {
    renderOffline(<EvidenceSummaryPanel evidenceId="ev-001" />)

    // nothing generated until the button is clicked
    expect(screen.queryByText(/\[mock-ai\]/)).not.toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Generate summary' }))

    expect(await screen.findByText(/\[mock-ai\]/)).toBeInTheDocument()
    expect(screen.getByText('simulated')).toBeInTheDocument()
    expect(screen.getByText(/grounded on \d+ facts/)).toBeInTheDocument()
  })

  it('labels a prompt digest loudly as NOT model-generated (UC-P2-3)', async () => {
    renderOffline(<EvidenceSummaryPanel evidenceId="ev-001" />)
    await userEvent.click(screen.getByRole('button', { name: 'Generate summary' }))

    const banner = await screen.findByRole('status')
    expect(banner).toHaveClass('banner-warn')
    expect(banner).toHaveTextContent('Not model-generated.')
    expect(banner).toHaveTextContent('deterministic digest')
    expect(screen.getByText('prompt digest')).toBeInTheDocument()
  })

  it('names the evidence record the summary was built from (UC-P2-3)', async () => {
    renderOffline(<EvidenceSummaryPanel evidenceId="ev-001" />)
    await userEvent.click(screen.getByRole('button', { name: 'Generate summary' }))

    expect(await screen.findByText('ev-001')).toBeInTheDocument()
  })
})
