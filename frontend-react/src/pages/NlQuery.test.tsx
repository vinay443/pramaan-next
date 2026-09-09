import { afterEach, describe, expect, it, vi } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { NlQuery } from './NlQuery'
import { renderOffline } from '../test/render'

afterEach(() => vi.unstubAllGlobals())

describe('NlQuery', () => {
  it('routes a missing-evidence question to the completeness query (mock)', async () => {
    renderOffline(<NlQuery />)

    await userEvent.click(screen.getByRole('button', { name: 'Ask' }))

    await waitFor(() => expect(screen.getByText('completeness')).toBeInTheDocument())
    expect(screen.getByText(/^\[mock-ai\]/)).toBeInTheDocument()
    expect(screen.getByText('simulated')).toBeInTheDocument()
  })

  it('an example chip routes to compliance', async () => {
    renderOffline(<NlQuery />)
    await userEvent.click(screen.getByRole('button', { name: 'What is our compliance posture?' }))
    await waitFor(() => expect(screen.getByText('compliance')).toBeInTheDocument())
  })

  it('an evidence-lookup question retrieves and cites evidence (RAG, mock)', async () => {
    renderOffline(<NlQuery />)
    await userEvent.click(
      screen.getByRole('button', { name: 'What evidence do we have for SSH root login?' }),
    )
    await waitFor(() => expect(screen.getByText('evidence-lookup')).toBeInTheDocument())
    expect(screen.getByText(/grounded in/)).toBeInTheDocument()
  })
})
