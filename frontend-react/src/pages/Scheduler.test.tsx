import { afterEach, describe, expect, it, vi } from 'vitest'
import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Scheduler } from './Scheduler'
import { renderOffline } from '../test/render'

afterEach(() => vi.unstubAllGlobals())

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
})
