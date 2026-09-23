// Typed endpoint functions. Each tries the real backend first; on a connection
// failure (or when VITE_USE_MOCKS=true) it falls back to deterministic mock data
// and flips the global data source to 'mock'.

import { ApiError, apiBaseUrl, apiFetch, apiFetchMultipart, buildQuery } from './client'
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
  NationalRollup,
  ReportInfo,
  TrendReport,
  BulkIngestResponse,
  CheckDefView,
  CheckResultParams,
  CheckResultView,
  CompletenessReport,
  ComplianceReport,
  ControlFrameworks,
  EvidenceCompletenessItem,
  EvidenceCompletenessReport,
  ControlCompletenessRow,
  FrameworkCompletenessRow,
  ControlReuseResult,
  DeterministicQueryResult,
  EvidenceDashboard,
  EvaluationSummary,
  EvidenceQueryParams,
  EvidenceSummary,
  EvidenceVersionView,
  EvidenceLifecycleView,
  EvidenceLifecycleSummary,
  EvidenceView,
  AuditScheduleReport,
  GrcSyncStatus,
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
  RegulatoryFiling,
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
  // An AI-unavailable failure means the backend IS up and the data IS live — only a
  // configured AI/embedding service is unreachable. Falling back to mock here would
  // both mislabel a live backend as "unavailable" and (for endpoints keyed by a
  // specific record, e.g. evidence summary) substitute an unrelated fixture. Let it
  // propagate so the caller shows the real "AI service unavailable" message instead.
  if (e instanceof ApiError && e.aiUnavailable) {
    return false
  }
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
    tagMockSourced(() => mock.mockApplications),
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
    tagMockSourced(() => mock.mockEvidencePage(params)),
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

export interface BulkUploadMeta {
  applicationSlug: string
  framework: string
  controlId: string
  sourceSystem?: string
  technology?: string
  collectedBy?: string
}

/** Uploads files (or a single .zip, expanded server-side into one item per entry) via
 *  `POST /api/v1/evidence/bulk/upload` (multipart/form-data). Partial success is allowed —
 *  each file/entry reports its own outcome in the response. */
export function ingestBulkUpload(files: File[], meta: BulkUploadMeta): Promise<BulkIngestResponse> {
  return withFallback(
    () => {
      const form = new FormData()
      form.set('applicationSlug', meta.applicationSlug)
      form.set('framework', meta.framework)
      form.set('controlId', meta.controlId)
      if (meta.sourceSystem) form.set('sourceSystem', meta.sourceSystem)
      if (meta.technology) form.set('technology', meta.technology)
      if (meta.collectedBy) form.set('collectedBy', meta.collectedBy)
      files.forEach((f) => form.append('files', f))
      return apiFetchMultipart<BulkIngestResponse>('/api/v1/evidence/bulk/upload', form)
    },
    () => mock.mockBulkIngestFiles(files, meta),
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

export function getEvidenceCompleteness(applicationSlug?: string, framework?: string): Promise<EvidenceCompletenessReport> {
  return withFallback(
    () =>
      apiFetch<EvidenceCompletenessReport>(
        `/api/v1/insight/evidence-completeness${buildQuery({ applicationSlug, framework })}`,
      ),
    () => mock.mockEvidenceCompleteness(applicationSlug, framework),
    true,
  )
}

/** Evidence Completeness — Framework -> Control rollup: one row per framework. */
export function getEvidenceCompletenessFrameworks(applicationSlug?: string): Promise<FrameworkCompletenessRow[]> {
  return withFallback(
    () =>
      apiFetch<FrameworkCompletenessRow[]>(
        `/api/v1/insight/evidence-completeness/frameworks${buildQuery({ applicationSlug })}`,
      ),
    () => mock.mockEvidenceCompletenessFrameworks(applicationSlug),
    true,
  )
}

/** One row per control in the given framework. */
export function getEvidenceCompletenessControls(
  framework: string,
  applicationSlug?: string,
): Promise<ControlCompletenessRow[]> {
  return withFallback(
    () =>
      apiFetch<ControlCompletenessRow[]>(
        `/api/v1/insight/evidence-completeness/frameworks/${encodeURIComponent(framework)}/controls${buildQuery({ applicationSlug })}`,
      ),
    () => mock.mockEvidenceCompletenessControls(framework, applicationSlug),
    true,
  )
}

/** Per-evidence-item completeness records backing one control's score (drill-down). */
export function getEvidenceCompletenessControlEvidence(
  framework: string,
  controlId: string,
  applicationSlug?: string,
): Promise<EvidenceCompletenessItem[]> {
  return withFallback(
    () =>
      apiFetch<EvidenceCompletenessItem[]>(
        `/api/v1/insight/evidence-completeness/frameworks/${encodeURIComponent(framework)}/controls/${encodeURIComponent(controlId)}/evidence${buildQuery({ applicationSlug })}`,
      ),
    () => mock.mockEvidenceCompletenessControlEvidence(framework, controlId, applicationSlug),
    true,
  )
}

/** Repeatable `businessUnit` scopes the rollup to those units (e.g. a vertical). */
/** `?businessUnit=A&businessUnit=B` (empty when unscoped). */
function unitsQuery(units?: string[]): string {
  const qs = new URLSearchParams()
  ;(units ?? []).forEach((u) => qs.append('businessUnit', u))
  const q = qs.toString()
  return q ? `?${q}` : ''
}

export function getLeadershipDashboard(businessUnits?: string[]): Promise<LeadershipDashboard> {
  return withFallback(
    () => apiFetch<LeadershipDashboard>(`/api/v1/insight/leadership${unitsQuery(businessUnits)}`),
    () => mock.mockLeadershipDashboard(businessUnits),
    true,
  )
}

export function getComparison(
  applications?: string[],
  framework?: string,
  businessUnits?: string[],
): Promise<ComparisonReport> {
  const qs = new URLSearchParams()
  ;(businessUnits ?? []).forEach((b) => qs.append('businessUnit', b))
  ;(applications ?? []).forEach((a) => qs.append('applications', a))
  if (framework) qs.set('framework', framework)
  const q = qs.toString()
  return withFallback(
    () => apiFetch<ComparisonReport>(`/api/v1/insight/comparison${q ? `?${q}` : ''}`),
    () => mock.mockComparison(applications, framework, businessUnits),
    true,
  )
}

/** The merged national + enterprise dashboard (GET /insight/national/rollup) — the single source for the
 *  former /enterprise and /national endpoints, both deprecated. Optional business units scope every part. */
export function getNationalRollup(businessUnits?: string[]): Promise<NationalRollup> {
  return withFallback(
    () => apiFetch<NationalRollup>(`/api/v1/insight/national/rollup${unitsQuery(businessUnits)}`),
    () => mock.mockNationalRollup(businessUnits),
    true,
  )
}

/** Enterprise view (portfolio, business-unit / criticality cuts, top risks) of the merged rollup. */
export async function getEnterpriseDashboard(businessUnits?: string[]): Promise<EnterpriseDashboard> {
  const r = await getNationalRollup(businessUnits)
  return {
    generatedAt: r.generatedAt,
    portfolio: r.portfolio,
    byBusinessUnit: r.byBusinessUnit,
    byCriticality: r.byCriticality,
    topRisks: r.topRisks,
  }
}

export function getAuditPrep(applicationSlug?: string, framework?: string): Promise<AuditPrepReport> {
  return withFallback(
    () => apiFetch<AuditPrepReport>(`/api/v1/insight/audit-prep${buildQuery({ applicationSlug, framework })}`),
    () => mock.mockAuditPrep(applicationSlug, framework),
    true,
  )
}

/** `businessUnit` scopes points and `current` to one function; closure/collection come back empty. */
export function getTrend(businessUnit?: string | string[]): Promise<TrendReport> {
  const units = businessUnit === undefined ? undefined : [businessUnit].flat()
  return withFallback(
    () => apiFetch<TrendReport>(`/api/v1/insight/trend${unitsQuery(units)}`),
    () => mock.mockTrend(units),
    true,
  )
}

/** Trend scoped to one application: `closure` (approvals/rejections/avg review time/rejection
 *  trend) is computed only from this application's evidence lifecycle events — see App Owner
 *  dashboard Overview. `current`/`points` stay portfolio-wide (not tracked per application). */
export function getTrendForApplication(applicationSlug: string): Promise<TrendReport> {
  return withFallback(
    () => apiFetch<TrendReport>(`/api/v1/insight/trend?applicationSlug=${encodeURIComponent(applicationSlug)}`),
    () => mock.mockTrendForApplication(applicationSlug),
    true,
  )
}

/** App Owner dashboard — evidence lifecycle counts, rejection audit trail, Auditor SLA, pending aging. */
export function getEvidenceLifecycleSummary(applicationSlug: string): Promise<EvidenceLifecycleSummary> {
  return withFallback(
    () =>
      apiFetch<EvidenceLifecycleSummary>(
        `/api/v1/insight/evidence-lifecycle/summary?applicationSlug=${encodeURIComponent(applicationSlug)}`,
      ),
    () => mock.mockEvidenceLifecycleSummary(applicationSlug),
    true,
  )
}

/** Upcoming audit schedule — every scheduled audit plus a computed readyCount/totalCount. */
export function getAuditSchedule(): Promise<AuditScheduleReport> {
  return withFallback(
    () => apiFetch<AuditScheduleReport>('/api/v1/insight/audit-schedule'),
    () => mock.mockAuditSchedule(),
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

/** UC17 — regulator-ready filing (fixed cover-page schema), rendered inline as a preview. */
export function getRegulatoryFiling(applicationSlug?: string, framework?: string): Promise<RegulatoryFiling> {
  return withFallback(
    () =>
      apiFetch<RegulatoryFiling>(
        `/api/v1/reports/regulatory-filing${buildQuery({ applicationSlug, framework })}`,
      ),
    () => mock.mockRegulatoryFiling(applicationSlug, framework),
    true,
  )
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

/** Per-call timeout for the two similarity endpoints. Both run the backend's
 *  EvidenceEmbeddingIndexer.ensureIndexed(), which does a full synchronous reindex on the first
 *  call after every backend start (and whenever evidence has been ingested since). That can
 *  exceed apiFetch's 8s default, which would be misread as "backend offline" and flip the global
 *  mock banner. 30s is a client-side patience allowance for that known-slow cold start, not a
 *  claim about how long it should take. by-control and /reuse/controls never touch the indexer,
 *  so they keep the default. */
const REUSE_SIMILARITY_TIMEOUT_MS = 30_000

export function getEvidenceReuse(id: string, limit = 5, minScore = 0.3): Promise<ReuseResult> {
  return withFallback(
    () =>
      apiFetch<ReuseResult>(`/api/v1/insight/reuse/${id}${buildQuery({ limit, minScore })}`, {
        timeoutMs: REUSE_SIMILARITY_TIMEOUT_MS,
      }),
    tagMockSourced(() => mock.mockReuseByEvidence(id, limit, minScore)),
    true,
  )
}

export function searchEvidenceReuse(text: string, limit = 5, minScore = 0.3): Promise<ReuseResult> {
  return withFallback(
    () =>
      apiFetch<ReuseResult>('/api/v1/insight/reuse/search', {
        method: 'POST',
        body: { text, limit, minScore },
        timeoutMs: REUSE_SIMILARITY_TIMEOUT_MS,
      }),
    tagMockSourced(() => mock.mockReuseByText(text, limit, minScore)),
    true,
  )
}

// ---- reuse by control (cross-framework) ---------------------------

// Per-result provenance for the Evidence Reuse page's calls (reuse endpoints + the application / evidence pickers). withFallback's return shape is
// unchanged: results produced by the mock fallback are recorded in a WeakSet (keyed by the
// returned object), and `isMockSourced` reads it. The global data-source store can't be used
// for this — it is last-call-wins across the whole app, not per response.
const mockSourced = new WeakSet<object>()

/** True when `result` came from the mock fallback rather than the live backend. */
export function isMockSourced(result: unknown): boolean {
  return typeof result === 'object' && result !== null && mockSourced.has(result)
}

function tagMockSourced<T>(fallback: () => T): () => T {
  return () => {
    const out = fallback()
    if (typeof out === 'object' && out !== null) mockSourced.add(out)
    return out
  }
}

export function listReuseControls(): Promise<ControlFrameworks[]> {
  return withFallback(
    () => apiFetch<ControlFrameworks[]>('/api/v1/insight/reuse/controls'),
    tagMockSourced(() => mock.mockReuseControls()),
    true,
  )
}

export function getReuseByControl(controlId: string): Promise<ControlReuseResult> {
  return withFallback(
    () => apiFetch<ControlReuseResult>(`/api/v1/insight/reuse/by-control${buildQuery({ controlId })}`),
    tagMockSourced(() => mock.mockReuseByControl(controlId)),
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
