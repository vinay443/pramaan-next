import { useMemo } from 'react'
import { listCheckResults } from '../api/endpoints'
import type { CheckResultParams } from '../api/types'
import { useAsync } from './useAsync'

/** Status counts over the current check-results page — shared by the Control Results
 *  page and the Auditor dashboard summary. Same shape/limits as the full-page view:
 *  counts reflect the loaded page (`size`), not a separate aggregate endpoint. */
export function useControlResultsSummary(params: CheckResultParams = {}, deps: unknown[] = []) {
  const query = useMemo(() => ({ page: 0, size: 100, ...params }), deps) // eslint-disable-line react-hooks/exhaustive-deps
  const { data, loading, error, reload } = useAsync(() => listCheckResults(query), [JSON.stringify(query)])

  const counts = useMemo(() => {
    const c: Record<string, number> = { PASS: 0, WARNING: 0, FAIL: 0, NOT_APPLICABLE: 0 }
    ;(data?.items ?? []).forEach((r) => (c[r.status] = (c[r.status] ?? 0) + 1))
    return c
  }, [data])

  return { data, loading, error, reload, counts }
}
