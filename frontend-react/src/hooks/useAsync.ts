import { useCallback, useEffect, useRef, useState } from 'react'

export interface AsyncState<T> {
  data: T | undefined
  loading: boolean
  error: string | undefined
  reload: () => void
}

export interface AsyncOptions<T> {
  /** While this returns true for the latest data, refetch every `pollMs`
   *  (default 2500ms) without toggling `loading`. Polling stops as soon as it
   *  returns false, and never runs before the first successful load. */
  pollWhile?: (data: T | undefined) => boolean
  pollMs?: number
}

/** Runs an async function on mount and whenever `deps` change. */
export function useAsync<T>(
  fn: () => Promise<T>,
  deps: unknown[],
  options: AsyncOptions<T> = {},
): AsyncState<T> {
  const [data, setData] = useState<T>()
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string>()
  const [nonce, setNonce] = useState(0)
  const mounted = useRef(true)

  const fnRef = useRef(fn)
  fnRef.current = fn
  const dataRef = useRef<T>()
  dataRef.current = data
  const { pollWhile, pollMs = 2500 } = options

  useEffect(() => {
    mounted.current = true
    return () => {
      mounted.current = false
    }
  }, [])

  useEffect(() => {
    setLoading(true)
    setError(undefined)
    fn()
      .then((d) => {
        if (mounted.current) setData(d)
      })
      .catch((e: unknown) => {
        if (mounted.current) setError(e instanceof Error ? e.message : String(e))
      })
      .finally(() => {
        if (mounted.current) setLoading(false)
      })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, nonce])

  // Background polling — silent refetch (no loading flicker) while `pollWhile`
  // holds for the current data.
  useEffect(() => {
    if (!pollWhile || loading || error) return
    if (!pollWhile(dataRef.current)) return
    const id = setInterval(() => {
      if (!mounted.current || !pollWhile(dataRef.current)) return
      fnRef.current()
        .then((d) => {
          if (mounted.current) setData(d)
        })
        .catch((e: unknown) => {
          if (mounted.current) setError(e instanceof Error ? e.message : String(e))
        })
    }, pollMs)
    return () => clearInterval(id)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [pollWhile, pollMs, loading, error, data, nonce])

  const reload = useCallback(() => setNonce((n) => n + 1), [])
  return { data, loading, error, reload }
}
