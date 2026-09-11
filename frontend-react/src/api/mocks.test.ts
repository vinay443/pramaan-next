import { describe, expect, it } from 'vitest'
import {
  mockAgents,
  mockBulkIngest,
  mockCompleteness,
  mockCompliance,
  mockEvidencePage,
  mockNlQuery,
  mockQuery,
  mockResults,
  mockReuseByText,
  mockSummary,
} from './mocks'

describe('shared fixtures', () => {
  it('agents are built from the shared collector catalog (16 checks)', () => {
    const nb = mockAgents.find((a) => a.agentId === 'agent-nb-01')
    expect(nb?.totalChecks).toBe(16)
    expect(nb?.collectors.map((c) => c.type)).toEqual(['os', 'database', 'middleware', 'tls'])
  })

  it('results include PASS, WARNING and FAIL', () => {
    const statuses = new Set(mockResults.map((r) => r.status))
    expect(statuses.has('PASS')).toBe(true)
    expect(statuses.has('WARNING')).toBe(true)
    expect(statuses.has('FAIL')).toBe(true)
  })
})

describe('Phase 2 mock adapters', () => {
  it('completeness counts covered vs missing deterministically', () => {
    const r = mockCompleteness('net-banking')
    expect(r.expected).toBe(7)
    expect(r.covered + r.stale + r.missing).toBe(r.expected)
    expect(r.covered).toBe(2)
    expect(r.controls.find((c) => c.controlId === 'OS-SSH-ROOT-LOGIN')?.coverage).toBe('COVERED')
  })

  it('compliance maps FAIL verdicts to NON_COMPLIANT', () => {
    const r = mockCompliance('payments')
    expect(r.controls.find((c) => c.controlId === 'TLS-CERT-EXPIRY')?.status).toBe('NON_COMPLIANT')
    expect(r.controls.find((c) => c.controlId === 'PCI-DSS-6.2')?.status).toBe('MISSING_EVIDENCE')
    expect(r.byFramework.length).toBeGreaterThan(0)
  })

  it('NL query routing is deterministic and keyword-based', () => {
    expect(mockNlQuery('which controls are missing?').matchedQuery).toBe('completeness')
    expect(mockNlQuery('how is our compliance posture?').matchedQuery).toBe('compliance')
    expect(mockNlQuery('what evidence is stale?').matchedQuery).toBe('stale-evidence')
    expect(mockNlQuery('where does our evidence come from?').matchedQuery).toBe('source-breakdown')
    expect(mockNlQuery('which controls are missing?').narrative.startsWith('[mock-ai]')).toBe(true)
  })

  // UC-P2-4: source-breakdown used to be the catch-all, which meant every unrecognised
  // question silently got a source answer. An unmatched question now says so.
  it('an unmatched NL question is reported as unsupported, not silently rerouted', () => {
    const r = mockNlQuery('anything else')
    expect(r.matchedQuery).toBe('unsupported')
    expect(r.supported).toBe(false)
    expect(r.supportedQuestionTypes.length).toBeGreaterThan(0)
    expect(r.narrative).toContain("isn't supported")
    expect(r.answer).not.toHaveProperty('counts')
  })

  it('mock AI output is flagged as a prompt digest, not model-generated', () => {
    expect(mockNlQuery('which controls are missing?').modelGenerated).toBe(false)
    expect(mockSummary('ev-001').modelGenerated).toBe(false)
  })

  it('summary is grounded only in the evidence record', () => {
    const s = mockSummary('ev-001')
    expect(s.simulated).toBe(true)
    expect(s.summary).toContain('[mock-ai]')
    expect(s.groundedOn).toContain('control: OS-SSH-ROOT-LOGIN')
  })

  it('reuse-by-text returns scored matches highest first', () => {
    const r = mockReuseByText('ssh root login disabled linux', 5, 0)
    expect(r.matches.length).toBeGreaterThan(0)
    for (let i = 1; i < r.matches.length; i++) {
      expect(r.matches[i - 1].score).toBeGreaterThanOrEqual(r.matches[i].score)
    }
  })
})

describe('mock data', () => {
  it('filters evidence deterministically by application', () => {
    const all = mockEvidencePage({})
    const nb = mockEvidencePage({ applicationSlug: 'net-banking' })
    expect(all.totalItems).toBe(6)
    expect(nb.items.every((e) => e.applicationSlug === 'net-banking')).toBe(true)
    expect(nb.totalItems).toBe(2)
  })

  it('paginates', () => {
    const p0 = mockEvidencePage({ size: 4, page: 0 })
    const p1 = mockEvidencePage({ size: 4, page: 1 })
    expect(p0.items).toHaveLength(4)
    expect(p1.items).toHaveLength(2)
    expect(p0.totalPages).toBe(2)
  })

  it('bulk ingest marks the second identical item as duplicate', () => {
    const res = mockBulkIngest([
      { applicationSlug: 'a', controlId: 'C-1', framework: 'ITPP', sourceSystem: 'S', contentText: 'x' },
      { applicationSlug: 'a', controlId: 'C-1', framework: 'ITPP', sourceSystem: 'S', contentText: 'x' },
    ])
    expect(res.received).toBe(2)
    expect(res.duplicates).toBe(1)
    expect(res.created).toBe(1)
  })

  it('freshness query returns numeric buckets', () => {
    const q = mockQuery('freshness')
    expect(q.counts).toMatchObject({ fresh: 3, aging: 1, stale: 2 })
  })
})
