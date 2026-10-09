import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import userEvent from '@testing-library/user-event'
import { Scheduler } from './Scheduler'
import { renderOffline } from '../test/render'
import { PERSONA_STORAGE_KEY } from './PersonaLogin'

// The Scheduler is role-gated on the persona picked at login; default these tests to App Owner.
beforeEach(() => sessionStorage.setItem(PERSONA_STORAGE_KEY, 'APP'))
afterEach(() => {
  vi.unstubAllGlobals()
  sessionStorage.removeItem(PERSONA_STORAGE_KEY)
})

describe('Scheduler', () => {
  it('runs collection across everything with a single button — no per-app/source toggles', async () => {
    renderOffline(<Scheduler />)

    expect(await screen.findByRole('button', { name: 'Run Collection' })).toBeInTheDocument()
    // The manual selection UI is gone.
    expect(screen.queryByText(/Sources \(/)).not.toBeInTheDocument()
    expect(screen.queryByRole('checkbox')).not.toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Run Collection' }))

    // A run detail section opens for the newly triggered run.
    expect(await screen.findByRole('heading', { name: /^Run run-/ })).toBeInTheDocument()
    expect(screen.getByLabelText('Collection pipeline')).toBeInTheDocument()
  })

  it('shows the pipeline stepper for a completed run with counts from existing run fields', async () => {
    renderOffline(<Scheduler />, '/?run=run-001')

    const pipeline = await screen.findByLabelText('Collection pipeline')
    expect(within(pipeline).getByLabelText('Evidence stored: done')).toBeInTheDocument()
    expect(within(pipeline).getByLabelText('Queued: done')).toBeInTheDocument()
    // run-001: ingested=8
    expect(within(pipeline).getByText('8 ingested')).toBeInTheDocument()
  })

  it('marks a stage failed for a failed run', async () => {
    renderOffline(<Scheduler />, '/?run=run-003')

    const pipeline = await screen.findByLabelText('Collection pipeline')
    // run-003: received=0 -> the source-polling stage is where it failed.
    expect(within(pipeline).getByLabelText('Sources polled: failed')).toBeInTheDocument()
    expect(within(pipeline).getByLabelText('Complete: failed')).toBeInTheDocument()
  })

  it('creates a schedule: validates required fields, then lists it Active with a next-run time', async () => {
    renderOffline(<Scheduler />)

    await screen.findByRole('heading', { name: 'Create schedule' })
    await userEvent.click(screen.getByRole('button', { name: 'Create schedule' }))
    expect(await screen.findByText('Name is required')).toBeInTheDocument()
    expect(screen.getByText('Frequency is required')).toBeInTheDocument()

    await userEvent.type(screen.getByLabelText(/^Name/), 'Nightly Jira')
    await screen.findByRole('option', { name: 'MOCK_JIRA' })
    await userEvent.selectOptions(screen.getByLabelText(/^Source/), 'MOCK_JIRA')
    const appSelect = screen.getByLabelText(/^Application/)
    await userEvent.selectOptions(appSelect, within(appSelect).getAllByRole('option')[1])
    await userEvent.selectOptions(screen.getByLabelText(/^Evidence type/), 'CHANGE-TICKET')
    await userEvent.selectOptions(screen.getByLabelText(/^Frequency/), 'DAILY')
    await userEvent.click(screen.getByRole('button', { name: 'Create schedule' }))

    const row = (await screen.findByText('Nightly Jira', { selector: 'td' })).closest('tr') as HTMLElement
    expect(within(row).getByText('Active')).toBeInTheDocument()
    expect(within(row).getAllByRole('cell')[5].textContent).not.toBe('—')
  })

  it('shows backend start time with active schedule count and offers the MINUTELY test frequency', async () => {
    renderOffline(<Scheduler />)

    expect(await screen.findByText(/Backend started .* active schedule/)).toBeInTheDocument()
    expect(await screen.findByRole('option', { name: 'Every minute (testing)' })).toBeInTheDocument()
  })

  it('is view-only for an Auditor: manage controls are absent or disabled, data stays visible', async () => {
    sessionStorage.setItem(PERSONA_STORAGE_KEY, 'AUDITOR')
    renderOffline(<Scheduler />)

    expect(await screen.findByText('Your role (Auditor) has view-only access to the Scheduler.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Create schedule' })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Run Collection' })).toBeDisabled()
    expect(await screen.findByLabelText('Credential state for MOCK_JIRA')).toBeDisabled()
    // read-only data is still shown
    expect(screen.getByRole('heading', { name: 'Schedules' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Runs' })).toBeInTheDocument()
    for (const b of screen.queryAllByRole('button', { name: /^(Retry|Run now|Activate|Deactivate)$/ })) {
      expect(b).toBeDisabled()
    }
  })

  it('shows queue wait, duration and an SLA badge per run, plus a summary over the listed runs', async () => {
    renderOffline(<Scheduler />)

    expect(await screen.findByText(/SLA 60s per run/)).toBeInTheDocument()
    const summary = await screen.findByLabelText('Run summary')
    expect(summary.textContent).toMatch(/[0-9]+ runs · [0-9]+ of [0-9]+ finished within SLA · slowest .* · average/)
    expect(screen.getByRole('columnheader', { name: 'Queue wait' })).toBeInTheDocument()
    expect(screen.getByRole('columnheader', { name: 'SLA' })).toBeInTheDocument()
  })

  it('shows Security events (rows as plain text) to an Auditor only', async () => {
    const rows = [
      {
        id: 'e1',
        occurredAt: '2026-10-09T10:00:00Z',
        eventType: 'INPUT_REJECTED',
        outcome: 'BLOCKED',
        role: 'APP',
        actor: 'App Owner',
        method: 'POST',
        path: '/api/v1/schedules',
        detail: 'name contains characters that are not allowed: <script>alert(1)</script>',
      },
    ]
    // only the security endpoint is "live"; everything else is offline and falls back to mock data
    vi.stubGlobal(
      'fetch',
      vi.fn((url: string) =>
        String(url).includes('/api/v1/security/events')
          ? Promise.resolve({ ok: true, status: 200, statusText: 'OK', text: async () => JSON.stringify(rows) })
          : Promise.reject(new TypeError('offline in test')),
      ),
    )
    sessionStorage.setItem(PERSONA_STORAGE_KEY, 'AUDITOR')
    const { unmount } = render(<MemoryRouter><Scheduler /></MemoryRouter>)

    expect(await screen.findByRole('heading', { name: 'Security events' })).toBeInTheDocument()
    expect(await screen.findByText('INPUT_REJECTED', { selector: 'td' })).toBeInTheDocument()
    expect(screen.getByText(/<script>alert\(1\)<\/script>/)).toBeInTheDocument() // text, not markup
    expect(document.querySelector('script')).toBeNull()
    expect(screen.getByText('BLOCKED')).toBeInTheDocument()
    unmount()

    sessionStorage.setItem(PERSONA_STORAGE_KEY, 'APP')
    render(<MemoryRouter><Scheduler /></MemoryRouter>)
    await screen.findByRole('heading', { name: 'Create schedule' })
    expect(screen.queryByRole('heading', { name: 'Security events' })).not.toBeInTheDocument()
  })

  it('says so when the run in ?run= is unknown or tampered', async () => {
    renderOffline(<Scheduler />, '/?run=%3Cimg%20src%3Dx%3E')
    expect(await screen.findByText(/Run not found \/ invalid run id/)).toBeInTheDocument()
  })

  it('shows "Already running" with a way to open the active run when Run now hits a 409', async () => {
    const job = {
      id: 's1', name: 'Busy job', sourceSystem: 'MOCK_JIRA', application: 'payments', evidenceType: 'GENERAL',
      frequency: 'DAILY', active: true, createdAt: '2026-10-09T10:00:00Z', nextRunAt: '2026-10-10T10:00:00Z', version: 3,
    }
    const reply = (status: number, body: unknown) =>
      Promise.resolve({ ok: status < 400, status, statusText: String(status), text: async () => JSON.stringify(body) })
    vi.stubGlobal(
      'fetch',
      vi.fn((url: string, init?: { method?: string }) => {
        if (String(url).endsWith('/api/v1/schedules/s1/run-now') && init?.method === 'POST') {
          return reply(409, { error: 'RUN_IN_PROGRESS', message: 'Schedule Busy job already has a run in progress', runId: 'run-001' })
        }
        if (String(url).endsWith('/api/v1/schedules')) return reply(200, [job])
        return Promise.reject(new TypeError('offline in test'))
      }),
    )
    render(<MemoryRouter><Scheduler /></MemoryRouter>)

    const row = (await screen.findByText('Busy job', { selector: 'td' })).closest('tr') as HTMLElement
    await userEvent.click(within(row).getByRole('button', { name: 'Run now' }))

    expect(await screen.findByText('Already running')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Open the active run' })).toBeInTheDocument()
    expect(await screen.findByRole('heading', { name: 'Run run-001' })).toBeInTheDocument() // jumped to the active run
  })

  it('Source connections panel lets the tester simulate an invalid credential', async () => {
    renderOffline(<Scheduler />)

    expect(await screen.findByText('Simulate source credential state', { selector: 'th' })).toBeInTheDocument()
    await userEvent.selectOptions(await screen.findByLabelText('Credential state for MOCK_JIRA'), 'INVALID')

    expect(await screen.findByText('INVALID', { selector: '.pill' })).toBeInTheDocument()
    await userEvent.selectOptions(screen.getByLabelText('Credential state for MOCK_JIRA'), 'VALID')
  })

  it('Run now opens the new SCHEDULED run with the schedule name and per-source connectivity', async () => {
    renderOffline(<Scheduler />)

    await screen.findByRole('heading', { name: 'Create schedule' })
    await userEvent.type(screen.getByLabelText(/^Name/), 'Jira pull')
    await screen.findByRole('option', { name: 'MOCK_JIRA' })
    await userEvent.selectOptions(screen.getByLabelText(/^Source/), 'MOCK_JIRA')
    const appSelect = screen.getByLabelText(/^Application/)
    await userEvent.selectOptions(appSelect, within(appSelect).getAllByRole('option')[1])
    await userEvent.selectOptions(screen.getByLabelText(/^Evidence type/), 'CHANGE-TICKET')
    await userEvent.selectOptions(screen.getByLabelText(/^Frequency/), 'HOURLY')
    await userEvent.click(screen.getByRole('button', { name: 'Create schedule' }))

    const row = (await screen.findByText('Jira pull', { selector: 'td' })).closest('tr') as HTMLElement
    await userEvent.click(within(row).getByRole('button', { name: 'Run now' }))

    expect(await screen.findByRole('heading', { name: /^Run run-/ })).toBeInTheDocument()
    const audit = await screen.findByLabelText('Run audit')
    expect(within(audit).getByText('Jira pull')).toBeInTheDocument()
    expect(within(audit).getByText('HOURLY')).toBeInTheDocument()
    expect(within(audit).getByText('SCHEDULED')).toBeInTheDocument()
    expect(within(audit).getAllByText('App Owner')).toHaveLength(2) // triggered by + schedule created by
    expect(await screen.findByText('CONNECTED')).toBeInTheDocument()
  })
})
