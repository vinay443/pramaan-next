import { afterEach, describe, expect, it, vi } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { EvidenceRepository } from './EvidenceRepository'
import { renderOffline } from '../test/render'

afterEach(() => vi.unstubAllGlobals())

describe('EvidenceRepository', () => {
  it('lists mock evidence and filters by application', async () => {
    renderOffline(<EvidenceRepository />)

    // all six mock rows visible via total count
    expect(await screen.findByText('Results — 6')).toBeInTheDocument()

    await userEvent.type(screen.getByLabelText('Application'), 'payments')

    await waitFor(() => expect(screen.getByText('Results — 3')).toBeInTheDocument())
    expect(screen.getByText('TLS-CERT-EXPIRY')).toBeInTheDocument()
    expect(screen.queryByText('OS-SSH-ROOT-LOGIN')).not.toBeInTheDocument()
  })

  it('filters by the technology facet (UC3) across scheduler- and upload-sourced evidence', async () => {
    renderOffline(<EvidenceRepository />)
    expect(await screen.findByText('Results — 6')).toBeInTheDocument()

    await userEvent.type(screen.getByLabelText('Technology'), 'postgresql')
    await waitFor(() => expect(screen.getByText('Results — 1')).toBeInTheDocument())
    expect(screen.getByText('DB-TLS-IN-TRANSIT')).toBeInTheDocument()
  })

  it('filters by the collection-method facet', async () => {
    renderOffline(<EvidenceRepository />)
    expect(await screen.findByText('Results — 6')).toBeInTheDocument()

    await userEvent.selectOptions(screen.getByLabelText('Collection method'), 'scheduled')
    await waitFor(() => expect(screen.getByText('Results — 5')).toBeInTheDocument())
  })

  it('offers "predefined-query" as a collection-method option (backend already supports it)', async () => {
    renderOffline(<EvidenceRepository />)
    const select = (await screen.findByLabelText('Collection method')) as HTMLSelectElement
    const options = [...select.options].map((o) => o.value)
    expect(options).toContain('predefined-query')

    await userEvent.selectOptions(select, 'predefined-query')
    expect(select.value).toBe('predefined-query')
  })
})
