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

// Screens that are intentionally always-mock (Evidence Reuse -> "Find similar evidence") can ask the
// shell to hide the "Backend unavailable" banner while they are mounted: it would be a misleading
// warning for expected behaviour. Reference-counted so overlapping mounts can't un-hide it early.
let bannerSuppressions = 0
const bannerListeners = new Set<() => void>()

function notifyBanner(): void {
  bannerListeners.forEach((l) => l())
}

/** Hide the global mock-data banner until the returned release function is called. */
export function suppressDataSourceBanner(): () => void {
  bannerSuppressions++
  notifyBanner()
  let released = false
  return () => {
    if (released) return
    released = true
    bannerSuppressions--
    notifyBanner()
  }
}

export function isDataSourceBannerSuppressed(): boolean {
  return bannerSuppressions > 0
}

export function subscribeBannerSuppression(fn: () => void): () => void {
  bannerListeners.add(fn)
  return () => bannerListeners.delete(fn)
}
