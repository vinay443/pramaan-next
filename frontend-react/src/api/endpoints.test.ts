import { afterEach, describe, expect, it, vi } from 'vitest'
import { getDataSource } from './dataSource'
import { listAgents, listApplications, listEvidence, runEvidenceQuery } from './endpoints'

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
