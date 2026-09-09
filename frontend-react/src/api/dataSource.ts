// Tracks whether the UI is currently showing live backend data or mock fallback.
//
// Starts as 'unknown' — NOT 'mock' — so a cold/hard reload doesn't flash the
// "backend unavailable" banner before the first request has even had a chance to
// land. The banner is only shown once a real request has genuinely failed and we
// have actually fallen back to mock data (see endpoints.ts `withFallback`).

export type DataSource = 'unknown' | 'live' | 'mock'

let current: DataSource = 'unknown'
const listeners = new Set<(s: DataSource) => void>()

export function getDataSource(): DataSource {
  return current
}

export function setDataSource(next: DataSource): void {
  if (next === current) return
  current = next
  listeners.forEach((l) => l(current))
}

export function subscribeDataSource(fn: (s: DataSource) => void): () => void {
  listeners.add(fn)
  return () => listeners.delete(fn)
}

/** True when mocks are forced via env (useful for demos / tests). */
export function mocksForced(): boolean {
  try {
    return import.meta.env?.VITE_USE_MOCKS === 'true'
  } catch {
    return false
  }
}
