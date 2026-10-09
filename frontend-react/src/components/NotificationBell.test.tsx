import { afterEach, describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { NotificationBell } from './NotificationBell'

afterEach(() => vi.unstubAllGlobals())

const failure = {
  id: 'n1',
  createdAt: '2026-10-09T10:00:00Z',
  type: 'RUN_FAILED',
  severity: 'ERROR',
  title: 'Run failed: Jira pull',
  message: 'Run r-1 (SCHEDULED, schedule Jira pull): received=0 ingested=0 duplicates=0 failed=1. MOCK_JIRA: AUTH_FAILED - Authentication failed for MOCK_JIRA',
  runId: 'r-1',
  scheduleName: 'Jira pull',
  recipientRole: 'APP',
  channel: 'IN_APP',
  readAt: null,
}

describe('NotificationBell', () => {
  it('shows the unread count and lists the failure notification with its run link and channel', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: true,
        status: 200,
        statusText: 'OK',
        text: async () =>
          JSON.stringify({ items: [failure, { ...failure, id: 'n2', channel: 'EMAIL_SIMULATED', readAt: failure.createdAt }], unreadCount: 1 }),
      }),
    )
    render(
      <MemoryRouter>
        <NotificationBell />
      </MemoryRouter>,
    )

    const bell = await screen.findByRole('button', { name: 'Notifications, 1 unread' })
    expect(bell).toHaveTextContent('1')
    await userEvent.click(bell)

    expect((await screen.findAllByText('Run failed: Jira pull', { selector: 'strong' })).length).toBeGreaterThan(0)
    expect(screen.getAllByText(/MOCK_JIRA: AUTH_FAILED/)).toHaveLength(2)
    expect(screen.getByText(/In-app/)).toBeInTheDocument()
    expect(screen.getByText(/Email \(simulated\)/)).toBeInTheDocument()
    expect(screen.getAllByRole('link', { name: 'Open run' })[0]).toHaveAttribute('href', '/scheduler?run=r-1')
    expect(screen.getAllByRole('button', { name: 'Mark read' })).toHaveLength(1) // only the unread in-app row
  })
})
