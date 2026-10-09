import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { ObservationsPanel } from './ObservationsPanel'
import { PERSONA_STORAGE_KEY } from '../pages/PersonaLogin'
import { mockEvidence } from '../api/mocks'

const ev = mockEvidence[0]

function renderPanel() {
  vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('offline in test'))) // mock data path
  return render(
    <MemoryRouter>
      <ObservationsPanel evidenceId={ev.evidenceId} />
    </MemoryRouter>,
  )
}

beforeEach(() => sessionStorage.removeItem(PERSONA_STORAGE_KEY))
afterEach(() => vi.unstubAllGlobals())

describe('ObservationsPanel', () => {
  it('lets an Auditor add an observation, shown as plain text with its control and framework, then close it', async () => {
    sessionStorage.setItem(PERSONA_STORAGE_KEY, 'AUDITOR')
    renderPanel()

    await userEvent.type(await screen.findByLabelText('Observation text'), 'Missing sign-off <b>x</b>')
    await userEvent.click(screen.getByRole('button', { name: 'Add observation' }))

    expect(await screen.findByText('Missing sign-off <b>x</b>')).toBeInTheDocument() // text, not markup
    expect(screen.getByText(ev.controlId, { selector: 'strong' })).toBeInTheDocument()
    expect(screen.getByText(`(${ev.framework})`)).toBeInTheDocument()
    expect(screen.getByText('OPEN')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Close' }))
    expect(await screen.findByText('CLOSED')).toBeInTheDocument()
  })

  it('shows the list read-only to an App Owner: no form, no close action', async () => {
    sessionStorage.setItem(PERSONA_STORAGE_KEY, 'APP')
    renderPanel()

    expect(await screen.findByText(/Only Auditor and Admin can add or close observations/)).toBeInTheDocument()
    expect(screen.queryByLabelText('Observation text')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Add observation' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Close' })).not.toBeInTheDocument()
  })
})
