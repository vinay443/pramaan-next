import { afterEach, describe, expect, it, vi } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Onboarding } from './Onboarding'
import { renderOffline } from '../test/render'

afterEach(() => vi.unstubAllGlobals())

describe('Onboarding (UC12)', () => {
  it('shows the catalogue and supports selective deboard / onboard', async () => {
    renderOffline(<Onboarding />)

    expect(await screen.findByText('Catalogue')).toBeInTheDocument()
    expect(screen.getAllByText(/SHAREPOINT/).length).toBeGreaterThan(0)
    expect(screen.getAllByText(/SERVICENOW/).length).toBeGreaterThan(0)

    // all apps start onboarded
    expect(screen.getByText('Every catalogued application is onboarded.')).toBeInTheDocument()

    // deboard the first row
    const firstDeboard = screen.getAllByRole('button', { name: 'Deboard' })[0]
    await userEvent.click(firstDeboard)

    // it now appears as selectable in the "not yet onboarded" picker and can be re-onboarded
    const onboardCheckbox = await screen.findByRole('checkbox', { name: /net banking|mobile banking|payments/i })
    await userEvent.click(onboardCheckbox)
    await userEvent.click(screen.getByRole('button', { name: 'Onboard (1)' }))

    await waitFor(() => expect(screen.getByText(/Onboarded \d+ —/)).toBeInTheDocument())
  })
})
