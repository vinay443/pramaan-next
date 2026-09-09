import { afterEach, describe, expect, it, vi } from 'vitest'
import { screen } from '@testing-library/react'
import { AuditPrep } from './AuditPrep'
import { renderOffline } from '../test/render'

afterEach(() => vi.unstubAllGlobals())

describe('AuditPrep (UC18)', () => {
  it('shows a readiness score, a grounded narrative and a checklist (mock)', async () => {
    renderOffline(<AuditPrep />)
    expect(await screen.findByText('Readiness score', { selector: '.stat-label' })).toBeInTheDocument()
    expect(screen.getByText(/^\[mock-ai\]/)).toBeInTheDocument()
    expect(screen.getByText(/^Checklist —/)).toBeInTheDocument()
  })
})
