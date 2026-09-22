import { getEvidenceLifecycleSummary, getTrendForApplication, listEvidence } from '../api/endpoints'
import { useAsync } from './useAsync'

/**
 * Shared data source for every App Owner dashboard tab (Overview, Controls, Evidence,
 * Findings, Remediation) — one place fetching evidence rows, closure trend and the
 * lifecycle summary for the owner's application, instead of each tab hitting these
 * endpoints ad hoc. The Compliance tab reuses ComplianceView / the portfolio comparison
 * fetch directly (already shared components), so it isn't part of this hook.
 */
export function useAppOwnerData(applicationSlug: string | undefined) {
  const evidence = useAsync(
    () => (applicationSlug ? listEvidence({ applicationSlug, size: 500 }) : Promise.resolve(undefined)),
    [applicationSlug],
  )
  const trend = useAsync(
    () => (applicationSlug ? getTrendForApplication(applicationSlug) : Promise.resolve(undefined)),
    [applicationSlug],
  )
  const lifecycle = useAsync(
    () => (applicationSlug ? getEvidenceLifecycleSummary(applicationSlug) : Promise.resolve(undefined)),
    [applicationSlug],
  )
  return {
    evidence,
    trend,
    lifecycle,
    loading: evidence.loading || trend.loading || lifecycle.loading,
  }
}
