import { afterEach, describe, expect, it, vi } from 'vitest'
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Admin } from './Admin'
import { renderOffline } from '../test/render'

afterEach(() => vi.unstubAllGlobals())

describe('Admin (UC05)', () => {
  it('lists seeded personas, the role catalogue and onboarded applications (mock)', async () => {
    renderOffline(<Admin />)

    expect(await screen.findByText('admin')).toBeInTheDocument()
    expect(screen.getByText('lead.auditor')).toBeInTheDocument()
    // role catalogue
    expect(screen.getByText('COMPLIANCE_OFFICER')).toBeInTheDocument()
    // applications reused from the shared fixtures
    expect(screen.getByText('net-banking')).toBeInTheDocument()
  })

  it('creates a new persona with roles', async () => {
    renderOffline(<Admin />)
    await screen.findByText('admin')

    await userEvent.click(screen.getByText('+ New user'))
    await userEvent.type(screen.getByLabelText('Username'), 'jane.doe')
    await userEvent.type(screen.getByLabelText('Display name'), 'Jane Doe')
    await userEvent.click(screen.getByLabelText('AUDITOR'))

    await userEvent.click(screen.getByRole('button', { name: 'Save' }))

    await waitFor(() =>
      expect(screen.queryByRole('button', { name: 'Save' })).not.toBeInTheDocument(),
    )
  })

  it('blocks save when no role is selected', async () => {
    renderOffline(<Admin />)
    await screen.findByText('admin')

    await userEvent.click(screen.getByText('+ New user'))
    await userEvent.type(screen.getByLabelText('Username'), 'no.roles')
    await userEvent.type(screen.getByLabelText('Display name'), 'No Roles')
    await userEvent.click(screen.getByRole('button', { name: 'Save' }))

    const form = screen.getByText('New user').closest('section') as HTMLElement
    expect(within(form).getByText(/at least one role/i)).toBeInTheDocument()
  })
})
