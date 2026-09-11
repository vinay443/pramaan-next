// Typed endpoint functions. Each tries the real backend first; on a connection
// failure (or when VITE_USE_MOCKS=true) it falls back to deterministic mock data
// and flips the global data source to 'mock'.

import { ApiError, apiBaseUrl, apiFetch, buildQuery } from './client'
import { mocksForced, setDataSource } from './dataSource'
import type * as MockModule from './mocks'
import type {
  AdminRole,
  AdminUserUpsert,
  AdminUserView,
  AgentDescriptor,
  ApplicationUpsert,
  ApplicationView,
  AuditPrepReport,
  ComparisonReport,
  EnterpriseDashboard,
  NationalDashboard,
  ReportInfo,
  TrendReport,
  BulkIngestResponse,
  CheckDefView,
  CheckResultParams,
  CheckResultView,
  CompletenessReport,
  ComplianceReport,
  ControlFrameworks,
  ControlReuseResult,
  DeterministicQueryResult,
  EvidenceDashboard,
  EvaluationSummary,
  EvidenceQueryParams,
  EvidenceSummary,
  EvidenceVersionView,
  EvidenceLifecycleView,
  EvidenceView,
  GrcSyncStatus,
  IngestRequest,
  IntegrityReport,
  LeadershipDashboard,
  LifecycleAction,
  NlQueryResult,
  OnboardingPlan,
  OnboardingResult,
  OnboardingScanRequest,
  OnboardingScanView,
  Page,
  PredefinedQueryCatalog,
  PredefinedQueryRunResult,
  PredefinedQueryRunSummary,
  ReuseResult,
  RunRequest,
  RunView,
} from './types'

// Mock data is a large module (it eagerly pulls in every contracts/mock-data/*.json
// fixture and derives a lot from them). Loading it on boot competes with the app's
// own module graph for the dev-server's cold transform budget and slows first paint.
// So we import it LAZILY — only the first time we actually fall back to it — and the
// `mock` proxy below is what the per-endpoint fallbacks reference.
let mockModule: typeof MockModule | null = null
async function loadMocks(): Promise<typeof MockModule> {
  if (!mockModule) mockModule = await import('./mocks')
  return mockModule
}
const mock = new Proxy(
  {},
  {
    get(_t, prop) {
      if (!mockModule) throw new Error(`mock data accessed before load: ${String(prop)}`)
      return (mockModule as Record<string | symbol, unknown>)[prop]
    },
  },
) as typeof MockModule

/** Vitest sets import.meta.env.TEST; the cold-start retry is dev-only noise there. */
const RETRY_FIRST_PROBE = (() => {
  try {
    return !import.meta.env?.TEST
  } catch {
    return true
  }
})()
let firstProbePending = true

function isRecoverable(e: unknown, fallbackOnHttpError: boolean): e is ApiError {
  return e instanceof ApiError && (e.offline || (fallbackOnHttpError && e.status >= 400))
}

async function withFallback<T>(
  live: () => Promise<T>,
  fallback: () => T,
  /** Also fall back on any HTTP error (not just an unreachable backend). Used for
   *  endpoints a Phase 1 backend may not implement yet, e.g. GET /api/v1/agents. */
  fallbackOnHttpError = false,
): Promise<T> {
  if (mocksForced()) {
    await loadMocks()
    setDataSource('mock')
    return fallback()
  }
  try {
    const out = await live()
    firstProbePending = false
    setDataSource('live')
    return out
  } catch (e) {
    if (!isRecoverable(e, fallbackOnHttpError)) {
      firstProbePending = false
      throw e
    }
    // The very first request of the session, against an unreachable backend, may
    // just be racing a dev-server cold start (Vite still transforming, /api proxy
    // not ready). Retry once with a short backoff before declaring it down, so the
    // "backend unavailable" banner never flashes when the backend is actually up.
    if (e.offline && firstProbePending && RETRY_FIRST_PROBE) {
      await new Promise((r) => setTimeout(r, 600))
      try {
        const out = await live()
        firstProbePending = false
        setDataSource('live')
        return out
      } catch (retryErr) {
        if (!isRecoverable(retryErr, fallbackOnHttpError)) {
          firstProbePending = false
          throw retryErr
        }
      }
    }
    firstProbePending = false
    await loadMocks()
    setDataSource('mock')
    return fallback()
  }
}

// ---- applications ---------------------------------------------------------

export function listApplications(): Promise<ApplicationView[]> {
  return withFallback(
    () => apiFetch<ApplicationView[]>('/api/v1/applications'),
    () => mock.mockApplications,
  )
}

export function getApplication(slug: string): Promise<ApplicationView> {
  return withFallback(
    () => apiFetch<ApplicationView>(`/api/v1/applications/${encodeURIComponent(slug)}`),
    () => mock.mockApplications.find((a) => a.slug === slug) ?? mock.mockApplications[0],
  )
}

export function getOnboardingPlan(): Promise<OnboardingPlan> {
  return withFallback(
    () => apiFetch<OnboardingPlan>('/api/v1/onboarding/plan'),
    () => mock.mockOnboardingPlan(),
    true,
  )
}

export function applyOnboarding(slugs?: string[], collect = false): Promise<OnboardingResult> {
  const qs = new URLSearchParams()
  ;(slugs ?? []).forEach((s) => qs.append('slugs', s))
  if (collect) qs.set('collect', 'true')
  const q = qs.toString()
  return withFallback(
    () => apiFetch<OnboardingResult>(`/api/v1/onboarding/apply${q ? `?${q}` : ''}`, { method: 'POST' }),
    () => mock.mockOnboardingResult(slugs),
    true,
  )
}

/** Staged onboarding: rich intake form -> 5-phase async scan (register app, resolve
 *  frameworks/controls, validate sources, trigger baseline collection, compute initial
 *  posture). A Phase 1 backend may not implement this yet — fall back to mock fixtures. */
export function startOnboardingScan(req: OnboardingScanRequest): Promise<OnboardingScanView> {
  return withFallback(
    () => apiFetch<OnboardingScanView>('/api/v1/onboarding/scans', { method: 'POST', body: req }),
    () => mock.mockStartOnboardingScan(req),
    true,
  )
}

/** Poll a staged onboarding scan by id. */
export function getOnboardingScan(scanId: string): Promise<OnboardingScanView> {
  return withFallback(
    () => apiFetch<OnboardingScanView>(`/api/v1/onboarding/scans/${encodeURIComponent(scanId)}`),
    () => mock.mockOnboardingScanById(scanId),
    true,
  )
}

export function upsertApplication(body: ApplicationUpsert, update = false): Promise<ApplicationView> {
  return withFallback(
    () =>
      apiFetch<ApplicationView>(
        update ? `/api/v1/applications/${encodeURIComponent(body.slug)}` : '/api/v1/applications',
        { method: update ? 'PUT' : 'POST', body },
      ),
    () => {
      const existing = mock.mockApplications.find((a) => a.slug === body.slug)
      if (existing) {
        Object.assign(existing, body, { active: true, updatedAt: new Date().toISOString() })
        return existing
      }
      return {
        ...body,
        criticality: body.criticality ?? 'MEDIUM',
        technology: body.technology ?? [],
        autoCreated: false,
        active: true,
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
      }
    },
  )
}

/** UC12 — deboard: stop the scheduler treating this app as onboarded (row + evidence retained). */
export function deboardApplication(slug: string): Promise<ApplicationView> {
  return withFallback(
    () => apiFetch<ApplicationView>(`/api/v1/applications/${encodeURIComponent(slug)}/deboard`, { method: 'POST' }),
    () => mock.mockSetApplicationActive(slug, false),
  )
}

/** UC12 — re-onboard a previously deboarded app. */
export function onboardApplication(slug: string): Promise<ApplicationView> {
  return withFallback(
    () => apiFetch<ApplicationView>(`/api/v1/applications/${encodeURIComponent(slug)}/onboard`, { method: 'POST' }),
    () => mock.mockSetApplicationActive(slug, true),
  )
}

// ---- Use Case 5 — ECS Admin (users, roles) ------------------------------
// A Phase 1 backend may not implement /api/v1/admin/* — fall back to fixtures.

export function listAdminRoles(): Promise<AdminRole[]> {
  return withFallback(
    () => apiFetch<AdminRole[]>('/api/v1/admin/roles'),
    () => mock.mockAdminRoles,
    true,
  )
}

export function listAdminUsers(): Promise<AdminUserView[]> {
  return withFallback(
    () => apiFetch<AdminUserView[]>('/api/v1/admin/users'),
    () => mock.mockAdminUsers,
    true,
  )
}

export function upsertAdminUser(body: AdminUserUpsert, update = false): Promise<AdminUserView> {
  return withFallback(
    () =>
      apiFetch<AdminUserView>(
        update ? `/api/v1/admin/users/${encodeURIComponent(body.username)}` : '/api/v1/admin/users',
        { method: update ? 'PUT' : 'POST', body },
      ),
    () => mock.mockAdminUpsert(body),
    true,
  )
}

export function setAdminUserActive(username: string, value: boolean): Promise<AdminUserView> {
  return withFallback(
    () =>
      apiFetch<AdminUserView>(
        `/api/v1/admin/users/${encodeURIComponent(username)}/active${buildQuery({ value })}`,
        { method: 'PUT' },
      ),
    () => ({ ...mock.mockAdminUsers.find((u) => u.username === username)!, active: value }),
    true,
  )
}

export function deleteAdminUser(username: string): Promise<void> {
  return withFallback(
    () => apiFetch<void>(`/api/v1/admin/users/${encodeURIComponent(username)}`, { method: 'DELETE' }),
    () => undefined,
    true,
  )
}

// ---- evidence -----------------------------------------------------------

export function getEvidenceDashboard(): Promise<EvidenceDashboard> {
  return withFallback(
    () => apiFetch<EvidenceDashboard>('/api/v1/evidence/dashboard'),
    () => mock.mockEvidenceDashboard(),
    true,
  )
}

export function listEvidence(params: EvidenceQueryParams): Promise<Page<EvidenceView>> {
  return withFallback(
    () => apiFetch<Page<EvidenceView>>(`/api/v1/evidence${buildQuery(params as Record<string, unknown>)}`),
    () => mock.mockEvidencePage(params),
  )
}

export function getEvidence(id: string): Promise<EvidenceView> {
  return withFallback(
    () => apiFetch<EvidenceView>(`/api/v1/evidence/${id}`),
    () => mock.mockEvidenceById(id),
  )
}

export function getEvidenceVersions(id: string): Promise<EvidenceVersionView[]> {
  return withFallback(
    () => apiFetch<EvidenceVersionView[]>(`/api/v1/evidence/${id}/versions`),
    () => {
      const ev = mock.mockEvidenceById(id)
      return ev.latest ? [ev.latest] : []
    },
  )
}

export function getEvidenceLifecycle(id: string): Promise<EvidenceLifecycleView> {
  return withFallback(
    () => apiFetch<EvidenceLifecycleView>(`/api/v1/evidence/${id}/lifecycle`),
    () => mock.mockLifecycle(id),
    true,
  )
}

export function transitionEvidenceLifecycle(
  id: string,
  action: LifecycleAction,
  actor?: string,
  note?: string,
): Promise<EvidenceLifecycleView> {
  return withFallback(
    () =>
      apiFetch<EvidenceLifecycleView>(`/api/v1/evidence/${id}/lifecycle`, {
        method: 'POST',
        body: { action, actor, note },
      }),
    () => mock.mockLifecycleTransition(id, action, actor, note),
    true,
  )
}

export function verifyEvidence(id: string, version?: number): Promise<IntegrityReport> {
  return withFallback(
    () => apiFetch<IntegrityReport>(`/api/v1/evidence/${id}/verify${buildQuery({ version })}`),
    () => mock.mockVerify(id, version),
  )
}

export function runEvidenceQuery(
  name: string,
  params: Pick<EvidenceQueryParams, 'applicationSlug' | 'framework' | 'controlId' | 'sourceSystem'> = {},
): Promise<DeterministicQueryResult> {
  return withFallback(
    () => apiFetch<DeterministicQueryResult>(`/api/v1/evidence/query/${name}${buildQuery(params as Record<string, unknown>)}`),
    () => mock.mockQuery(name, params.applicationSlug),
  )
}

export function ingestBulk(items: IngestRequest[]): Promise<BulkIngestResponse> {
  return withFallback(
    () => apiFetch<BulkIngestResponse>('/api/v1/evidence/bulk', { method: 'POST', body: items }),
    () => mock.mockBulkIngest(items),
  )
}

// ---- outbound GRC sync --------------------------------------------------
// A Phase 1 backend may not implement /api/v1/grc/* yet — fall back to mock fixtures.

export function getGrcStatus(): Promise<GrcSyncStatus> {
  return withFallback(
    () => apiFetch<GrcSyncStatus>('/api/v1/grc/status'),
    () => mock.mockGrcStatus(),
    true,
  )
}

export function triggerGrcSync(): Promise<GrcSyncStatus> {
  return withFallback(
    () => apiFetch<GrcSyncStatus>('/api/v1/grc/sync', { method: 'POST' }),
    () => mock.mockGrcSync(),
    true,
  )
}

// ---- scheduler --------------------------------------------------------

export function listRuns(page = 0, size = 20): Promise<Page<RunView>> {
  return withFallback(
    () => apiFetch<Page<RunView>>(`/api/v1/scheduler/runs${buildQuery({ page, size })}`),
    () => {
      const all = mock.mockRunsList()
      return { items: all.slice(page * size, page * size + size), page, size, totalItems: all.length, totalPages: Math.max(1, Math.ceil(all.length / size)) }
    },
  )
}

export function getRun(runId: string): Promise<RunView> {
  return withFallback(
    () => apiFetch<RunView>(`/api/v1/scheduler/runs/${runId}`),
    () => mock.mockRunById(runId),
  )
}

export function startRun(req: RunRequest): Promise<RunView> {
  return withFallback(
    () => apiFetch<RunView>('/api/v1/scheduler/runs', { method: 'POST', body: req }),
    () => mock.mockStartRun(req),
  )
}

export function retryRun(runId: string): Promise<RunView> {
  return withFallback(
    () => apiFetch<RunView>(`/api/v1/scheduler/runs/${runId}/retry`, { method: 'POST' }),
    () => mock.mockStartRun(mock.mockRunById(runId)),
  )
}

export function listSources(): Promise<string[]> {
  return withFallback(
    () => apiFetch<string[]>('/api/v1/scheduler/sources'),
    () => mock.mockSources,
  )
}

// ---- agents ----------------------------------------------------------

export function listAgents(): Promise<AgentDescriptor[]> {
  return withFallback(
    () => apiFetch<AgentDescriptor[]>('/api/v1/agents'),
    () => mock.mockAgents,
    true, // /api/v1/agents may be unimplemented in a Phase 1 backend — fall back to shared fixtures
  )
}

// ---- control results (deterministic rule evaluation) -----------------

export function listChecks(): Promise<CheckDefView[]> {
  return withFallback(
    () => apiFetch<CheckDefView[]>('/api/v1/checks'),
    () => mock.mockChecks,
  )
}

export function listCheckResults(params: CheckResultParams): Promise<Page<CheckResultView>> {
  return withFallback(
    () => apiFetch<Page<CheckResultView>>(`/api/v1/check-results${buildQuery(params as Record<string, unknown>)}`),
    () => mock.mockCheckResultsPage(params),
  )
}

export function evaluateChecks(
  body: Pick<CheckResultParams, 'applicationSlug' | 'framework' | 'controlId' | 'sourceSystem'> = {},
): Promise<EvaluationSummary> {
  return withFallback(
    () => apiFetch<EvaluationSummary>('/api/v1/checks/evaluate', { method: 'POST', body }),
    () => mock.mockEvaluate(body),
  )
}

// ---- Phase 2 insight (completeness / compliance / reuse / summary / NL) ----
// A Phase 1 backend has no /api/v1/insight/* — fall back to shared mock fixtures.

export function getCompleteness(applicationSlug: string, framework?: string): Promise<CompletenessReport> {
  return withFallback(
    () =>
      apiFetch<CompletenessReport>(
        `/api/v1/insight/completeness${buildQuery({ applicationSlug, framework })}`,
      ),
    () => mock.mockCompleteness(applicationSlug, framework),
    true,
  )
}

export function getLeadershipDashboard(): Promise<LeadershipDashboard> {
  return withFallback(
    () => apiFetch<LeadershipDashboard>('/api/v1/insight/leadership'),
    () => mock.mockLeadershipDashboard(),
    true,
  )
}

export function getComparison(applications?: string[], framework?: string): Promise<ComparisonReport> {
  const qs = new URLSearchParams()
  ;(applications ?? []).forEach((a) => qs.append('applications', a))
  if (framework) qs.set('framework', framework)
  const q = qs.toString()
  return withFallback(
    () => apiFetch<ComparisonReport>(`/api/v1/insight/comparison${q ? `?${q}` : ''}`),
    () => mock.mockComparison(applications, framework),
    true,
  )
}

export function getEnterpriseDashboard(): Promise<EnterpriseDashboard> {
  return withFallback(
    () => apiFetch<EnterpriseDashboard>('/api/v1/insight/enterprise'),
    () => mock.mockEnterprise(),
    true,
  )
}

export function getNationalDashboard(): Promise<NationalDashboard> {
  return withFallback(
    () => apiFetch<NationalDashboard>('/api/v1/insight/national'),
    () => mock.mockNational(),
    true,
  )
}

export function getAuditPrep(applicationSlug?: string, framework?: string): Promise<AuditPrepReport> {
  return withFallback(
    () => apiFetch<AuditPrepReport>(`/api/v1/insight/audit-prep${buildQuery({ applicationSlug, framework })}`),
    () => mock.mockAuditPrep(applicationSlug, framework),
    true,
  )
}

export function getTrend(): Promise<TrendReport> {
  return withFallback(
    () => apiFetch<TrendReport>('/api/v1/insight/trend'),
    () => mock.mockTrend(),
    true,
  )
}

export function listReports(): Promise<ReportInfo[]> {
  return withFallback(
    () => apiFetch<ReportInfo[]>('/api/v1/reports'),
    () => mock.mockReports,
    true,
  )
}

/** Report URL for the current backend, so the UI can link to a JSON/CSV download. */
export function reportUrl(name: string, format: 'json' | 'csv', applicationSlug?: string, framework?: string): string {
  return `${apiBaseUrl()}/api/v1/reports/${encodeURIComponent(name)}${buildQuery({ format, applicationSlug, framework })}`
}

export function getCompliance(applicationSlug: string, framework?: string): Promise<ComplianceReport> {
  return withFallback(
    () =>
      apiFetch<ComplianceReport>(
        `/api/v1/insight/compliance${buildQuery({ applicationSlug, framework })}`,
      ),
    () => mock.mockCompliance(applicationSlug, framework),
    true,
  )
}

// ---- predefined technical queries ---------------------------------------

export function listPredefinedQueries(
  f: { technology?: string; framework?: string; controlFamily?: string } = {},
): Promise<PredefinedQueryCatalog> {
  return withFallback(
    () => apiFetch<PredefinedQueryCatalog>(`/api/v1/predefined-queries${buildQuery(f)}`),
    () => mock.mockPredefinedQueryCatalog(f.technology, f.framework, f.controlFamily),
  )
}

export function runPredefinedQuery(controlId: string, applicationSlug?: string): Promise<PredefinedQueryRunResult> {
  return withFallback(
    () =>
      apiFetch<PredefinedQueryRunResult>(
        `/api/v1/predefined-queries/${encodeURIComponent(controlId)}/run${buildQuery({ applicationSlug })}`,
        { method: 'POST' },
      ),
    () => mock.mockRunPredefinedQuery(controlId, applicationSlug),
  )
}

export function runAllPredefinedQueries(
  f: { technology?: string; framework?: string; controlFamily?: string; applicationSlug?: string } = {},
): Promise<PredefinedQueryRunSummary> {
  return withFallback(
    () =>
      apiFetch<PredefinedQueryRunSummary>(`/api/v1/predefined-queries/run-all${buildQuery(f)}`, {
        method: 'POST',
      }),
    () => mock.mockRunAllPredefinedQueries(f.technology, f.framework, f.controlFamily, f.applicationSlug),
  )
}

export function getEvidenceSummary(id: string): Promise<EvidenceSummary> {
  return withFallback(
    () => apiFetch<EvidenceSummary>(`/api/v1/insight/evidence/${id}/summary`),
    () => mock.mockSummary(id),
    true,
  )
}

export function getEvidenceReuse(id: string, limit = 5, minScore = 0.3): Promise<ReuseResult> {
  return withFallback(
    () => apiFetch<ReuseResult>(`/api/v1/insight/reuse/${id}${buildQuery({ limit, minScore })}`),
    () => mock.mockReuseByEvidence(id, limit, minScore),
    true,
  )
}

export function searchEvidenceReuse(text: string, limit = 5, minScore = 0.3): Promise<ReuseResult> {
  return withFallback(
    () => apiFetch<ReuseResult>('/api/v1/insight/reuse/search', { method: 'POST', body: { text, limit, minScore } }),
    () => mock.mockReuseByText(text, limit, minScore),
    true,
  )
}

// ---- reuse by control (cross-framework) ---------------------------

export function listReuseControls(): Promise<ControlFrameworks[]> {
  return withFallback(
    () => apiFetch<ControlFrameworks[]>('/api/v1/insight/reuse/controls'),
    () => mock.mockReuseControls(),
    true,
  )
}

export function getReuseByControl(controlId: string): Promise<ControlReuseResult> {
  return withFallback(
    () => apiFetch<ControlReuseResult>(`/api/v1/insight/reuse/by-control${buildQuery({ controlId })}`),
    () => mock.mockReuseByControl(controlId),
    true,
  )
}

export function addEvidenceFramework(id: string, framework: string): Promise<EvidenceView> {
  return withFallback(
    () => apiFetch<EvidenceView>(`/api/v1/evidence/${id}/frameworks`, { method: 'POST', body: { framework } }),
    () => mock.mockAddEvidenceFramework(id, framework),
  )
}

export function askNlQuery(question: string, applicationSlug?: string): Promise<NlQueryResult> {
  return withFallback(
    () => apiFetch<NlQueryResult>('/api/v1/insight/nl-query', { method: 'POST', body: { question, applicationSlug } }),
    () => mock.mockNlQuery(question, applicationSlug),
    true,
  )
}
