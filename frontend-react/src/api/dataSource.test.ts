import { afterEach, describe, expect, it, vi } from 'vitest'
import { getDataSource } from './dataSource'
import { listApplications, listEvidence } from './endpoints'

function okJson(body: unknown): Response {
  return new Response(JSON.stringify(body), { status: 200, headers: { 'Content-Type': 'application/json' } })
}

afterEach(() => vi.unstubAllGlobals())

describe('data source state (cold-reload banner false positive)', () => {
  it('starts as "unknown" — never "mock" — before any request has run', () => {
    // The Layout only shows the "backend unavailable" banner when this is 'mock',
    // so a fresh load must not report 'mock' before a request has actually failed.
    expect(getDataSource()).toBe('unknown')
  })

  it('goes straight to "live" on a successful first request (no mock flash)', async () => {
    const transitions: string[] = [getDataSource()]
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(okJson({ items: [], page: 0, size: 20, totalItems: 0, totalPages: 1 })),
    )

    await listEvidence({ applicationSlug: 'payments' })
    transitions.push(getDataSource())

    expect(transitions).toEqual(['unknown', 'live'])
    expect(transitions).not.toContain('mock')
  })

  it('only reports "mock" after a request genuinely fails', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')))

    const apps = await listApplications()

    expect(apps.length).toBeGreaterThan(0)
    expect(getDataSource()).toBe('mock')
  })
})
