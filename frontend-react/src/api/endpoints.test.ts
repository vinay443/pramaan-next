import { afterEach, describe, expect, it, vi } from 'vitest'
import { getDataSource } from './dataSource'
import { getEvidenceSummary, listAgents, listApplications, listEvidence, runEvidenceQuery } from './endpoints'

function okJson(body: unknown): Response {
  return new Response(JSON.stringify(body), { status: 200, headers: { 'Content-Type': 'application/json' } })
}

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('endpoint mock fallback', () => {
  it('falls back to mock data and flips data source when the backend is unreachable', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')))

    const apps = await listApplications()

    expect(apps.map((a) => a.slug)).toEqual(['net-banking', 'mobile-banking', 'payments'])
    expect(getDataSource()).toBe('mock')
  })

  it('returns parsed live data and marks the source live on success', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        okJson({ items: [], page: 0, size: 20, totalItems: 0, totalPages: 1 }),
      ),
    )

    const page = await listEvidence({ applicationSlug: 'payments' })

    expect(page.totalItems).toBe(0)
    expect(getDataSource()).toBe('live')
    const call = (fetch as unknown as ReturnType<typeof vi.fn>).mock.calls[0][0] as string
    expect(call).toContain('/api/v1/evidence?applicationSlug=payments')
  })

  it('does NOT swallow real HTTP errors (non-offline)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(JSON.stringify({ message: "unknown query 'bogus'" }), { status: 400 }),
      ),
    )

    await expect(runEvidenceQuery('bogus')).rejects.toThrow(/unknown query/)
  })

  it('does NOT fall back to mock on an AI-unavailable (503) response — backend is live, only AI is down', async () => {
    // First, a genuine live success — establishes dataSource === 'live'.
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        okJson({ items: [], page: 0, size: 20, totalItems: 0, totalPages: 1 }),
      ),
    )
    await listEvidence({ applicationSlug: 'payments' })
    expect(getDataSource()).toBe('live')

    // Now the AI-dependent call fails with the backend's distinct 503 shape.
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(
          JSON.stringify({
            status: 503,
            error: 'AI Service Unavailable',
            message: 'The configured AI/embedding service is unreachable right now.',
            aiUnavailable: true,
          }),
          { status: 503 },
        ),
      ),
    )

    // Must reject with the backend's real message — not silently substitute a
    // hardcoded, unrelated fixture (the bug: evidence summary for a live evidenceId
    // used to come back as ev-001 / OS-SSH-ROOT-LOGIN / net-banking).
    await expect(getEvidenceSummary('a-real-evidence-id-from-postgres')).rejects.toThrow(
      /AI\/embedding service is unreachable/,
    )
    // The backend is up and evidence data is live — this specific AI failure must
    // not flip the global "Backend unavailable" banner.
    expect(getDataSource()).toBe('live')
  })

  it('listAgents falls back to shared fixtures when the backend returns 404', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(JSON.stringify({ status: 404, message: 'No endpoint api/v1/agents' }), { status: 404 }),
      ),
    )

    const agents = await listAgents()

    expect(agents.length).toBeGreaterThan(0)
    expect(agents[0].collectors.length).toBeGreaterThan(0)
    expect(getDataSource()).toBe('mock')
  })
})
