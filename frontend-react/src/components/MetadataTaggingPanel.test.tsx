import { afterEach, describe, expect, it, vi } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MetadataTaggingPanel } from './MetadataTaggingPanel'
import { renderOffline } from '../test/render'
import type { EvidenceView } from '../api/types'

afterEach(() => vi.unstubAllGlobals())

const ev001: EvidenceView = {
  evidenceId: 'ev-001',
  evidenceKey: 'net-banking|C-SITE|OS-SSH-ROOT-LOGIN|AGENT_OS_LINUX|agent_os_linux-os-ssh-root-login',
  applicationSlug: 'net-banking',
  controlId: 'OS-SSH-ROOT-LOGIN',
  framework: 'C-SITE',
  sourceSystem: 'AGENT_OS_LINUX',
  currentVersion: 2,
  createdAt: '2026-08-01T08:00:00Z',
  updatedAt: '2026-09-04T08:00:00Z',
  tags: { technology: 'linux', collectionMethod: 'scheduled' },
}

describe('MetadataTaggingPanel', () => {
  it('shows the canonical UC03 tags and the currently mapped framework', async () => {
    renderOffline(<MetadataTaggingPanel evidence={ev001} onUpdated={vi.fn()} />)

    expect(screen.getByText('linux')).toBeInTheDocument()
    expect(screen.getByText('scheduled')).toBeInTheDocument()
    expect(screen.getByText('C-SITE')).toBeInTheDocument()
  })

  it('offers only frameworks the control maps to that are not yet mapped', async () => {
    renderOffline(<MetadataTaggingPanel evidence={ev001} onUpdated={vi.fn()} />)

    const select = await screen.findByRole('combobox')
    const optionLabels = Array.from(select.querySelectorAll('option')).map((o) => o.textContent)
    expect(optionLabels).toEqual(expect.arrayContaining(['PCI_DSS', 'ISO27001', 'IS']))
    expect(optionLabels).not.toContain('C-SITE')
  })

  it('maps the evidence to an additional framework and reports the update', async () => {
    const onUpdated = vi.fn()
    renderOffline(<MetadataTaggingPanel evidence={ev001} onUpdated={onUpdated} />)

    const select = await screen.findByRole('combobox')
    await userEvent.selectOptions(select, 'PCI_DSS')
    await userEvent.click(screen.getByRole('button', { name: 'Map framework' }))

    await waitFor(() => expect(onUpdated).toHaveBeenCalledTimes(1))
    const updated = onUpdated.mock.calls[0][0]
    expect(updated.evidenceId).toBe('ev-001')
    expect(updated.tags.frameworks.split(',')).toContain('PCI_DSS')
  })
})
