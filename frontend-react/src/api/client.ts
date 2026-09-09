// Minimal typed fetch wrapper for the Pramaan backend.

export class ApiError extends Error {
  status: number
  /** True when the backend could not be reached at all (network / DNS / refused). */
  offline: boolean
  body?: unknown

  constructor(message: string, opts: { status?: number; offline?: boolean; body?: unknown } = {}) {
    super(message)
    this.name = 'ApiError'
    this.status = opts.status ?? 0
    this.offline = opts.offline ?? false
    this.body = opts.body
  }
}

export function apiBaseUrl(): string {
  try {
    const v = import.meta.env?.VITE_API_BASE_URL
    if (typeof v === 'string' && v.length > 0) return v.replace(/\/$/, '')
  } catch {
    /* not in a Vite context (tests) */
  }
  return ''
}

export function buildQuery(params: Record<string, unknown>): string {
  const q = new URLSearchParams()
  for (const [k, v] of Object.entries(params)) {
    if (v === undefined || v === null || v === '') continue
    q.set(k, String(v))
  }
  const s = q.toString()
  return s ? `?${s}` : ''
}

export interface RequestOptions {
  method?: string
  body?: unknown
  signal?: AbortSignal
  timeoutMs?: number
}

export async function apiFetch<T>(path: string, opts: RequestOptions = {}): Promise<T> {
  const url = `${apiBaseUrl()}${path}`
  const controller = new AbortController()
  const timeout = setTimeout(() => controller.abort(), opts.timeoutMs ?? 8000)

  let res: Response
  try {
    res = await fetch(url, {
      method: opts.method ?? 'GET',
      headers: opts.body !== undefined ? { 'Content-Type': 'application/json', Accept: 'application/json' } : { Accept: 'application/json' },
      body: opts.body !== undefined ? JSON.stringify(opts.body) : undefined,
      signal: opts.signal ?? controller.signal,
    })
  } catch (e) {
    throw new ApiError(`Cannot reach backend at ${url}`, { offline: true, body: e })
  } finally {
    clearTimeout(timeout)
  }

  const text = await res.text()
  const parsed = text ? safeJson(text) : undefined

  if (!res.ok) {
    const msg =
      parsed && typeof parsed === 'object' && parsed !== null && 'message' in parsed
        ? String((parsed as { message: unknown }).message)
        : `${res.status} ${res.statusText}`
    throw new ApiError(msg, { status: res.status, body: parsed })
  }
  return parsed as T
}

function safeJson(text: string): unknown {
  try {
    return JSON.parse(text)
  } catch {
    return text
  }
}
