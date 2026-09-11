import { describe, expect, it } from 'vitest'
import { screen } from '@testing-library/react'
import { Onboarding } from './Onboarding'
import { renderOffline } from '../test/render'

describe('Onboarding (UC12)', () => {
  it('shows the catalogue of onboarded applications', async () => {
    renderOffline(<Onboarding />)

    expect(await screen.findByText('Catalogue')).toBeInTheDocument()
    expect(screen.getAllByText(/SHAREPOINT/).length).toBeGreaterThan(0)
    expect(screen.getAllByText(/SERVICENOW/).length).toBeGreaterThan(0)

    // all apps start onboarded — nothing left to pick in the "not yet onboarded" picker
    expect(screen.getByText('Every catalogued application is onboarded.')).toBeInTheDocument()
  })
})
