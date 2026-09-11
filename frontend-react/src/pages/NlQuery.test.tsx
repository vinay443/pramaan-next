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

  it('shows retrieval provenance for the RAG path (UC-P2-4)', async () => {
    renderOffline(<NlQuery />)
    await userEvent.click(
      screen.getByRole('button', { name: 'What evidence do we have for SSH root login?' }),
    )
    await waitFor(() => expect(screen.getByText('evidence-lookup')).toBeInTheDocument())
    expect(screen.getByText(/vector store/)).toBeInTheDocument()
    expect(screen.getByText(/embedding model/)).toBeInTheDocument()
    expect(screen.getByText(/record\(s\) indexed/)).toBeInTheDocument()
  })

  it('labels the narrative as a prompt digest, not model output (UC-P2-4)', async () => {
    renderOffline(<NlQuery />)
    await userEvent.click(screen.getByRole('button', { name: 'Ask' }))
    await waitFor(() => expect(screen.getByText('completeness')).toBeInTheDocument())
    expect(screen.getByText('prompt digest')).toBeInTheDocument()
  })

  it('reports an unmatched question as unsupported and lists what it can answer (UC-P2-4)', async () => {
    renderOffline(<NlQuery />)
    await userEvent.clear(screen.getByLabelText('Question'))
    await userEvent.type(screen.getByLabelText('Question'), 'what is the capital of France?')
    await userEvent.click(screen.getByRole('button', { name: 'Ask' }))

    await waitFor(() => expect(screen.getByText('Unsupported question')).toBeInTheDocument())
    expect(screen.getByRole('status')).toHaveTextContent(/isn't supported/)
    expect(screen.getByText('Supported question types:')).toBeInTheDocument()
    expect(screen.getByText(/Evidence source breakdown/)).toBeInTheDocument()
    // it must not render an answer it does not have
    expect(screen.queryByText('Structured result')).not.toBeInTheDocument()
  })
})
