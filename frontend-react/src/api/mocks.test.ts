import { describe, expect, it } from 'vitest'
import {
  mockAgents,
  mockBulkIngest,
  mockCompleteness,
  mockCompliance,
  mockEvidenceSummaryReport,
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

  it('NL query output is flagged as a prompt digest, not model-generated', () => {
    expect(mockNlQuery('which controls are missing?').modelGenerated).toBe(false)
  })

  it('evidence summaries are deterministic, rule-based and derived from the mock check results', () => {
    const expected: Record<string, string> = {
      'ev-001': 'PASS',
      'ev-002': 'WARNING',
      'ev-003': 'WARNING',
      'ev-004': 'FAIL',
      'ev-005': 'FAIL',
      'ev-006': 'NOT_APPLICABLE',
      'ev-008': 'NOT_ASSESSED',
    }
    for (const [id, verdict] of Object.entries(expected)) {
      const s = mockSummary(id)
      expect(s.verdict, id).toBe(verdict)
      expect(s.method).toBe('DETERMINISTIC_RULES')
      expect(s.model).toBe('deterministic-rules/v1')
      expect(s.modelGenerated).toBe(false)
      expect(s.facts.length).toBeGreaterThan(0)
      expect(s.facts.every((f) => f.pointer.length > 0)).toBe(true)
      expect(s.groundedOn).toHaveLength(s.facts.length)
      expect(JSON.stringify(s)).not.toMatch(/mock-ai|prompt digest|null|undefined/)
      expect(mockSummary(id)).toEqual(s) // same input, same output
    }
    const fail = mockSummary('ev-004')
    expect(fail.contentKind).toBe('CHECK_BUNDLE')
    expect(fail.headline).toContain('1 of 2 checks passed, 1 failed.')
    expect(fail.findings[0]).toMatchObject({ severity: 'FAIL', pointer: '/findings/0' })
    expect(mockSummary('ev-008').checks).toEqual([])
    expect(mockSummary('ev-008').contentKind).toBe('DOCUMENT')
  })

  it('summary health covers stale and mismatch cases, control descriptions and versions', () => {
    const tampered = mockSummary('ev-006')
    expect(tampered.health).toMatchObject({ integrity: 'MISMATCH', freshness: 'STALE' })
    expect(tampered.headline).toContain('does not match the recorded SHA-256')
    expect(mockSummary('ev-001').health).toMatchObject({ integrity: 'INTACT', freshness: 'CURRENT', versionCount: 2 })
    expect(mockSummary('ev-002').control).toMatchObject({ source: 'LIBRARY', description: expect.any(String) })
    expect(mockSummary('ev-003').control.description).toBe('Only approved TLS versions may be enabled.')

    const older = mockSummary('ev-001', 1)
    expect(older).toMatchObject({ version: 1, verdict: 'NOT_ASSESSED' })
    expect(() => mockSummary('ev-001', 9)).toThrow(/no version 9/)
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

describe('evidence-summary report (offline)', () => {
  it('lists expected controls incl. MISSING ones, worst-of verdicts and consistent totals', () => {
    const r = mockEvidenceSummaryReport()
    expect(r.controls.length).toBe(r.totals.controlsExpected)
    expect(r.totals.controlsMissing).toBeGreaterThan(0)
    expect(r.totals.controlsWithEvidence + r.totals.controlsMissing).toBe(r.totals.controlsExpected)
    const missing = r.controls.find((c) => c.status === 'MISSING')
    expect(missing?.verdict).toBeNull()
    expect(missing?.evidence).toEqual([])

    const tls = r.controls.find((c) => c.application === 'payments' && c.controlId === 'TLS-CERT-EXPIRY')
    expect(tls?.verdict).toBe('FAIL')
    expect(tls?.evidence[0].evidenceId).toBe('ev-004')
    expect(tls?.evidence[0].headline).toBe(mockSummary('ev-004').headline)

    const byVerdict = r.totals.byVerdict
    expect(Object.values(byVerdict).reduce((a, b) => a + b, 0)).toBe(r.totals.evidenceRecords)
    expect(byVerdict.FAIL).toBeGreaterThan(0)
    expect(JSON.stringify(r)).not.toMatch(/mock-ai|prompt digest/)
  })

  it('filters by application and framework and is ordered deterministically', () => {
    const p = mockEvidenceSummaryReport('payments', 'C-SITE')
    expect(p.controls.every((c) => c.application === 'payments' && c.framework === 'C-SITE')).toBe(true)
    expect(p.scope).toEqual({ applicationSlug: 'payments', framework: 'C-SITE' })
    const all = mockEvidenceSummaryReport()
    const apps = all.controls.map((c) => c.application)
    expect(apps).toEqual([...apps].sort())
    expect(mockEvidenceSummaryReport()).toEqual(all)
  })
})
