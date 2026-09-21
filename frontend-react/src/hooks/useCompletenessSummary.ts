import { useMemo } from 'react'
import { getEvidenceCompletenessFrameworks } from '../api/endpoints'
import type { FrameworkCompletenessRow } from '../api/types'
import { useAsync } from './useAsync'

export type FrameworkStatus = 'EVALUATED' | 'PARTIAL' | 'NOT_EVALUATED'

export function frameworkStatus(row: FrameworkCompletenessRow): FrameworkStatus {
  if (row.controlsEvaluated === 0) return 'NOT_EVALUATED'
  if (row.controlsEvaluated >= row.totalControls) return 'EVALUATED'
  return 'PARTIAL'
}

/** Framework completeness rollup — shared by the Evidence Completeness page and the
 *  Auditor dashboard summary. Same aggregation as the full-page view, scoped to a
 *  single application when `applicationSlug` is given (all applications otherwise). */
export function useCompletenessSummary(applicationSlug?: string) {
  const frameworksReq = useAsync(() => getEvidenceCompletenessFrameworks(applicationSlug), [applicationSlug])
  const frameworks = frameworksReq.data ?? []

  const totals = useMemo(() => {
    const totalControls = frameworks.reduce((s, f) => s + f.totalControls, 0)
    const evaluated = frameworks.reduce((s, f) => s + f.controlsEvaluated, 0)
    const notEvaluated = frameworks.reduce((s, f) => s + f.controlsNotEvaluated, 0)
    const weightedSum = frameworks.reduce((s, f) => s + (f.avgCompletenessPct ?? 0) * f.controlsEvaluated, 0)
    const avgCompletenessPct = evaluated === 0 ? null : Math.round((weightedSum / evaluated) * 10) / 10
    const evaluatedFrameworks = frameworks.filter((f) => frameworkStatus(f) === 'EVALUATED').length
    const partialFrameworks = frameworks.filter((f) => frameworkStatus(f) === 'PARTIAL').length
    const notEvaluatedFrameworks = frameworks.filter((f) => frameworkStatus(f) === 'NOT_EVALUATED').length
    return {
      totalControls,
      evaluated,
      notEvaluated,
      avgCompletenessPct,
      evaluatedFrameworks,
      partialFrameworks,
      notEvaluatedFrameworks,
    }
  }, [frameworks])

  return { ...frameworksReq, frameworks, totals }
}
