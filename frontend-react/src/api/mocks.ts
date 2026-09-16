// Adapts the SHARED prototype fixtures in contracts/mock-data/ to API response
// shapes. No dataset literals live here — only shaping logic. Used when the
// backend is unreachable (or VITE_USE_MOCKS=true).

import agentsRaw from '@mockdata/agents.json'
import applicationsRaw from '@mockdata/applications.json'
import evidenceRaw from '@mockdata/evidence.json'
import frameworksRaw from '@mockdata/frameworks.json'
import manifest from '@mockdata/manifest.json'
import resultsRaw from '@mockdata/results.json'
import runsRaw from '@mockdata/scheduler-runs.json'
import type {
  AdminRole,
  AdminUserUpsert,
  AdminUserView,
  AgentDescriptor,
  ApplicationView,
  BulkIngestResponse,
  CheckDefView,
  CheckResultParams,
  CheckResultView,
  CompletenessReport,
  ComplianceReport,
  ControlCompletenessRow,
  ControlCoverage,
  ControlFrameworks,
  ControlReuseEvidence,
  ControlReuseResult,
  ControlPosture,
  ControlStatus,
  Coverage,
  Criticality,
  CompletenessBand,
  DeterministicQueryResult,
  EvidenceCompletenessFactor,
  EvidenceCompletenessItem,
  EvidenceCompletenessReport,
  EvidenceDashboard,
  EvaluationSummary,
  EvidenceSummary,
  EvidenceQueryParams,
  EvidenceView,
  FrameworkCompletenessRow,
  FrameworkFiling,
  FrameworkPosture,
  AuditPrepReport,
  ComparisonReport,
  EnterpriseDashboard,
  EvidenceLifecycleView,
  GrcSyncStatus,
  IngestRequest,
  IntegrityReport,
  LeadershipDashboard,
  LifecycleAction,
  NationalDashboard,
  NationalRollup,
  RegionFrameworkRow,
  RegionGap,
  NlQueryResult,
  OnboardingPhaseKey,
  OnboardingPlan,
  OnboardingResult,
  OnboardingScanRequest,
  OnboardingScanView,
  RegulatoryFiling,
  ReportInfo,
  TrendReport,
  Page,
  ReuseResult,
  RunRequest,
  RunView,
  SimilarEvidence,
} from './types'

const NOW = manifest.referenceNow
const STALE_DAYS = manifest.freshness.staleAfterDays
const nowMs = Date.parse(NOW)
const dayMs = 86_400_000

export const mockApplications: ApplicationView[] = applicationsRaw.applications.map((a) => ({
  slug: a.slug,
  name: a.name,
  businessUnit: a.businessUnit,
  criticality: a.criticality as Criticality,
  owner: a.owner,
  technology: a.technology,
  autoCreated: false,
  active: true,
  createdAt: NOW,
  updatedAt: NOW,
}))

export function mockSetApplicationActive(slug: string, active: boolean): ApplicationView {
  const app = mockApplications.find((a) => a.slug === slug)
  if (!app) throw new Error(`Unknown application: ${slug}`)
  app.active = active
  app.updatedAt = new Date().toISOString()
  return app
}

// UC03 Controls_Framework_Mapping — mirrors backend evidence/control-frameworks.json
// so offline/mock evidence carries the same naming convention + framework tags.
const CONTROL_FRAMEWORKS: Record<string, string[]> = {
  'OS-BASELINE-VERSION': ['ITPP', 'ISO27001', 'IS'],
  'OS-SSH-ROOT-LOGIN': ['C-SITE', 'PCI_DSS', 'ISO27001', 'IS'],
  'OS-SSH-PASSWORD-AUTH': ['C-SITE', 'PCI_DSS', 'ISO27001'],
  'OS-AUDIT-LOGGING': ['PCI_DSS', 'ISO27001', 'IS', 'DPSC'],
  'DB-TLS-IN-TRANSIT': ['PCI_DSS', 'DPSC', 'ISO27001'],
  'DB-AUDIT-LOGGING': ['PCI_DSS', 'ISO27001', 'IS'],
  'DB-PASSWORD-STORAGE': ['C-SITE', 'PCI_DSS', 'ISO27001'],
  'DB-AUTH-NO-TRUST': ['C-SITE', 'PCI_DSS'],
  'MW-TLS-VERSION': ['PCI_DSS', 'DPSC', 'ISG'],
  'MW-BANNER-SUPPRESSION': ['C-SITE', 'ISG'],
  'MW-HSTS': ['DPSC', 'ISG', 'ISO27001'],
  'TLS-PROTOCOL-VERSION': ['PCI_DSS', 'DPSC', 'ISG'],
  'TLS-CERT-EXPIRY': ['C-SITE', 'ISG', 'ISO27001'],
  'TLS-KEY-STRENGTH': ['C-SITE', 'PCI_DSS', 'ISG'],
  'TLS-CERT-TRUST': ['DPSC', 'ISG'],
  'ITPP-CHG-02': ['ITPP', 'ISO27001', 'IS'],
  'DPSC-SDLC-04': ['DPSC', 'ISO27001', 'PCI_DSS'],
  'PCI-DSS-6.2': ['PCI_DSS', 'DPSC'],
}

function frameworksFor(controlId: string, primary: string): string[] {
  const out = [primary.toUpperCase()]
  for (const fw of CONTROL_FRAMEWORKS[controlId?.toUpperCase()] ?? []) if (!out.includes(fw)) out.push(fw)
  return out
}

function evidenceTypeFor(controlId: string, sourceSystem: string): string {
  const c = (controlId ?? '').toUpperCase()
  if (c.startsWith('OS-')) return 'HOST-CONFIG'
  if (c.startsWith('DB-')) return 'DB-CONFIG'
  if (c.startsWith('MW-')) return 'MIDDLEWARE-CONFIG'
  if (c.startsWith('TLS-')) return 'TLS-SCAN'
  if (c.includes('CHG') || c.includes('CHANGE')) return 'CHANGE-TICKET'
  if (c.includes('SDLC') || c.startsWith('PCI-DSS-6')) return 'CODE-REVIEW'
  return (sourceSystem ?? '').includes('AGENT') ? 'AGENT-SCAN' : 'GENERAL'
}

/** Stamp the UC03 canonical name + framework tags onto raw fixture evidence. */
function withNamingConvention(e: EvidenceView): EvidenceView {
  const frameworks = frameworksFor(e.controlId, e.framework)
  const evidenceType = evidenceTypeFor(e.controlId, e.sourceSystem)
  const date = (e.latest?.collectedAt ?? e.createdAt ?? '').slice(0, 10).replace(/-/g, '') || '00000000'
  const seq = String(e.currentVersion || 1).padStart(3, '0')
  const name = `${e.applicationSlug}_${e.controlId}_${evidenceType}_${date}_${seq}`
  return {
    ...e,
    title: name,
    tags: {
      ...e.tags,
      framework: frameworks[0],
      frameworks: frameworks.join(','),
      evidenceType,
      control: e.controlId,
      name,
      ...(e.title ? { sourceTitle: e.title } : {}),
    },
    // UC04 — mock evidence is trustworthy by default, mirroring a healthy repository
    // where every stored hash still matches its object. See the TAMPERED demo seam below.
    integrityStatus: 'VERIFIED',
  }
}

export const mockEvidence: EvidenceView[] = (evidenceRaw.evidence as unknown as EvidenceView[]).map(
  withNamingConvention,
)

// Demo seam for "Reuse by control": leave one record tagged to only its primary
// framework so the "Reuse for [framework]" action has something to add. Live
// evidence is normally stamped with the full UC03 framework set at ingestion.
{
  const partial = mockEvidence.find((e) => e.evidenceId === 'ev-002')
  if (partial) partial.tags = { ...partial.tags, frameworks: partial.framework }
}

// Demo seam for the UC04 integrity badge: one record's stored hash no longer matches
// its object, same as a real EvidenceQueryService.verify() mismatch, so the
// "tampered" state is visible offline instead of every record always reading VERIFIED.
{
  const tampered = mockEvidence.find((e) => e.evidenceId === 'ev-006')
  if (tampered) tampered.integrityStatus = 'TAMPERED'
}

function ageDays(iso: string | undefined): number {
  if (!iso) return Number.POSITIVE_INFINITY
  return (nowMs - Date.parse(iso)) / dayMs
}

export function mockEvidencePage(params: EvidenceQueryParams): Page<EvidenceView> {
  let items = mockEvidence.slice()
  const { applicationSlug, framework, controlId, sourceSystem, tag, technology, collectionMethod } = params
  if (applicationSlug) items = items.filter((e) => e.applicationSlug === applicationSlug)
  if (framework) items = items.filter((e) => e.framework === framework.toUpperCase())
  if (controlId) items = items.filter((e) => e.controlId === controlId.toUpperCase())
  if (sourceSystem) items = items.filter((e) => e.sourceSystem === sourceSystem.toUpperCase())
  if (technology) items = items.filter((e) => (e.tags.technology ?? '').toLowerCase() === technology.toLowerCase())
  if (collectionMethod)
    items = items.filter((e) => (e.tags.collectionMethod ?? '').toLowerCase() === collectionMethod.toLowerCase())
  if (tag) {
    const [k, v] = tag.split(':')
    items = items.filter((e) => (v ? e.tags[k] === v : k in e.tags))
  }
  const size = params.size ?? 20
  const page = params.page ?? 0
  const start = page * size
  return {
    items: items.slice(start, start + size),
    page,
    size,
    totalItems: items.length,
    totalPages: Math.max(1, Math.ceil(items.length / size)),
  }
}

export function mockEvidenceById(id: string): EvidenceView {
  return mockEvidence.find((e) => e.evidenceId === id) ?? mockEvidence[0]
}

export function mockVerify(id: string, version?: number): IntegrityReport {
  const ev = mockEvidenceById(id)
  return {
    evidenceId: id,
    version: version ?? ev.currentVersion,
    expectedSha256: ev.latest?.sha256 ?? 'a'.repeat(64),
    actualSha256: ev.latest?.sha256 ?? 'a'.repeat(64),
    intact: true,
    detail: 'sha-256 matches (mock)',
  }
}

export function mockQuery(name: string, appSlug?: string): DeterministicQueryResult {
  const scope = appSlug ? mockEvidence.filter((e) => e.applicationSlug === appSlug) : mockEvidence
  const base = { name, generatedAt: NOW }

  switch (name) {
    case 'source-breakdown': {
      const counts: Record<string, number> = {}
      scope.forEach((e) => (counts[e.sourceSystem] = (counts[e.sourceSystem] ?? 0) + 1))
      return {
        ...base,
        answerText: `Evidence is sourced from ${Object.keys(counts).length} system(s).`,
        counts,
        rows: Object.entries(counts).map(([sourceSystem, count]) => ({ sourceSystem, count })),
      }
    }
    case 'stale-evidence': {
      const stale = scope.filter((e) => ageDays(e.latest?.collectedAt) > STALE_DAYS)
      return {
        ...base,
        answerText: `${stale.length} evidence item(s) are older than ${STALE_DAYS} days.`,
        counts: { staleAfterDays: STALE_DAYS, stale: stale.length },
        rows: stale.map((e) => ({
          evidenceId: e.evidenceId,
          applicationSlug: e.applicationSlug,
          framework: e.framework,
          controlId: e.controlId,
          collectedAt: e.latest?.collectedAt,
        })),
      }
    }
    case 'freshness': {
      let fresh = 0
      let aging = 0
      let stale = 0
      scope.forEach((e) => {
        const d = ageDays(e.latest?.collectedAt)
        if (d <= 30) fresh += 1
        else if (d <= 90) aging += 1
        else stale += 1
      })
      return {
        ...base,
        answerText: `Freshness: ${fresh} fresh (<=30d), ${aging} aging (<=90d), ${stale} stale (>90d).`,
        counts: { fresh, aging, stale, unknown: 0 },
        rows: [{ fresh, aging, stale, unknown: 0 }],
      }
    }
    case 'latest-per-control':
      return {
        ...base,
        answerText: `${scope.length} control(s) have current evidence.`,
        counts: { controls: scope.length },
        rows: scope.map((e) => ({
          control: `${e.applicationSlug} / ${e.framework} / ${e.controlId}`,
          evidenceId: e.evidenceId,
          version: e.currentVersion,
          sha256: e.latest?.sha256,
          collectedAt: e.latest?.collectedAt,
        })),
      }
    case 'duplicates':
      return { ...base, answerText: '0 content hash(es) appear on more than one evidence version.', counts: { duplicateHashes: 0 }, rows: [] }
    default:
      return { ...base, answerText: `Unknown query '${name}' (mock).`, counts: {}, rows: [] }
  }
}

export function mockEvidenceDashboard(): EvidenceDashboard {
  const all = mockEvidence
  let fresh = 0
  let aging = 0
  let stale = 0
  let unknown = 0
  const bySourceCounts: Record<string, number> = {}
  for (const e of all) {
    const d = ageDays(e.latest?.collectedAt)
    if (d === Number.POSITIVE_INFINITY) unknown += 1
    else if (d <= 30) fresh += 1
    else if (d <= 90) aging += 1
    else stale += 1
    bySourceCounts[e.sourceSystem] = (bySourceCounts[e.sourceSystem] ?? 0) + 1
  }
  const versions = all.reduce((n, e) => n + e.currentVersion, 0)
  return {
    generatedAt: NOW,
    records: all.length,
    versions,
    applications: new Set(all.map((e) => e.applicationSlug)).size,
    frameworks: new Set(all.map((e) => e.framework)).size,
    sources: Object.keys(bySourceCounts).length,
    staleAfterDays: STALE_DAYS,
    freshness: { fresh, aging, stale, unknown },
    // mock object store mirrors persisted hashes -> everything intact
    integrity: { checked: all.length, intact: all.length, mismatch: 0, missingObject: 0 },
    duplicateHashes: 0,
    bySource: Object.entries(bySourceCounts)
      .sort(([a], [b]) => a.localeCompare(b))
      .map(([sourceSystem, count]) => ({ sourceSystem, count })),
  }
}

// ---- Use Case 12 — onboarding ----------------------------------------

const ONBOARD_SOURCES: Record<string, string[]> = {
  'net-banking': ['SIM_NGINX', 'SIM_POSTGRES', 'MOCK_GITHUB', 'SHAREPOINT', 'SERVICENOW'],
  'mobile-banking': ['SIM_POSTGRES', 'MOCK_GITHUB', 'MOCK_CONFLUENCE', 'SHAREPOINT', 'SERVICENOW'],
  payments: ['SIM_POSTGRES', 'SIM_MYSQL', 'MOCK_JIRA', 'SHAREPOINT', 'SERVICENOW'],
}

export function mockOnboardingPlan(): OnboardingPlan {
  const items = mockApplications.map((a) => ({
    slug: a.slug,
    name: a.name,
    criticality: a.criticality ?? 'MEDIUM',
    frameworks: mockFrameworksForApp(a.slug),
    sources: ONBOARD_SOURCES[a.slug] ?? ['SHAREPOINT', 'SERVICENOW'],
    unknownSources: [],
    status: (a.active ? 'EXISTS' : 'NEW') as 'EXISTS' | 'NEW',
  }))
  const existing = items.filter((i) => i.status === 'EXISTS').length
  return { total: items.length, toCreate: items.length - existing, existing, items }
}

function mockFrameworksForApp(slug: string): string[] {
  return [...new Set(mockEvidence.filter((e) => e.applicationSlug === slug).map((e) => e.framework))]
}

export function mockOnboardingResult(slugs?: string[]): OnboardingResult {
  const target = slugs && slugs.length ? slugs : mockApplications.map((a) => a.slug)
  let created = 0
  for (const slug of target) {
    const app = mockApplications.find((a) => a.slug === slug)
    if (app) {
      if (!app.active) created += 1
      app.active = true
    }
  }
  return { applied: target.length, created, updated: target.length - created, slugs: target, collectionRunId: null }
}

// ---- Staged onboarding scan (rich intake form -> 5-phase async scan) ------

const ONBOARDING_SCAN_PHASES: OnboardingPhaseKey[] = [
  'REGISTER_APPLICATION',
  'RESOLVE_FRAMEWORKS_CONTROLS',
  'VALIDATE_EVIDENCE_SOURCES',
  'TRIGGER_BASELINE_COLLECTION',
  'COMPUTE_INITIAL_POSTURE',
]

function onboardingScanPhaseMessage(phase: OnboardingPhaseKey): string {
  switch (phase) {
    case 'REGISTER_APPLICATION':
      return 'application registered and onboarded (mock)'
    case 'RESOLVE_FRAMEWORKS_CONTROLS':
      return 'controls resolved (mock)'
    case 'VALIDATE_EVIDENCE_SOURCES':
      return 'sources validated (mock)'
    case 'TRIGGER_BASELINE_COLLECTION':
      return 'baseline collection run triggered (mock)'
    case 'COMPUTE_INITIAL_POSTURE':
      return 'initial completeness/compliance computed (mock)'
    default:
      return 'done (mock)'
  }
}

let scanSeq = 900
const mockScans: OnboardingScanView[] = []
// Wall-clock start per mock scan, so repeated polls can walk it through the 5
// phases (mirrors what the real backend's async executor does).
const scanStartedAt = new Map<string, number>()

export function mockStartOnboardingScan(req: OnboardingScanRequest): OnboardingScanView {
  scanSeq += 1
  const scan: OnboardingScanView = {
    scanId: `scan-${scanSeq}`,
    applicationSlug: req.slug,
    status: 'PENDING',
    currentPhase: null,
    phases: ONBOARDING_SCAN_PHASES.map((phase) => ({ phase, status: 'PENDING', message: null })),
    schedulerRunId: null,
    completenessPct: null,
    compliancePct: null,
    message: null,
    createdAt: NOW,
    startedAt: null,
    finishedAt: null,
  }
  mockScans.unshift(scan)
  scanStartedAt.set(scan.scanId, Date.now())

  // Mirrors the backend's REGISTER_APPLICATION phase: upsert + onboard immediately
  // so the rest of the app (Applications, catalogue) reflects it without waiting.
  const existing = mockApplications.find((a) => a.slug === req.slug)
  if (existing) {
    existing.name = req.name
    existing.businessUnit = req.businessUnit
    existing.criticality = req.criticality ?? existing.criticality
    existing.owner = req.owner
    existing.technology = req.technology ?? existing.technology
    existing.active = true
    existing.updatedAt = NOW
  } else {
    mockApplications.unshift({
      slug: req.slug,
      name: req.name,
      businessUnit: req.businessUnit,
      criticality: req.criticality ?? 'MEDIUM',
      owner: req.owner,
      technology: req.technology ?? [],
      autoCreated: false,
      active: true,
      createdAt: NOW,
      updatedAt: NOW,
    })
  }
  return scan
}

function advanceMockOnboardingScan(scan: OnboardingScanView): OnboardingScanView {
  const started = scanStartedAt.get(scan.scanId)
  if (started === undefined || scan.status === 'COMPLETED' || scan.status === 'FAILED') return scan
  const stepMs = 1200
  const elapsed = Date.now() - started
  const doneCount = Math.min(ONBOARDING_SCAN_PHASES.length, Math.floor(elapsed / stepMs))

  scan.phases = ONBOARDING_SCAN_PHASES.map((phase, i) => {
    if (i < doneCount) return { phase, status: 'COMPLETED', message: onboardingScanPhaseMessage(phase) }
    if (i === doneCount) return { phase, status: 'RUNNING', message: null }
    return { phase, status: 'PENDING', message: null }
  })
  scan.currentPhase = ONBOARDING_SCAN_PHASES[Math.min(doneCount, ONBOARDING_SCAN_PHASES.length - 1)]

  if (doneCount === 0) {
    scan.status = 'PENDING'
  } else if (doneCount < ONBOARDING_SCAN_PHASES.length) {
    scan.status = 'RUNNING'
    scan.startedAt = scan.startedAt ?? new Date(started).toISOString()
  } else {
    scan.status = 'COMPLETED'
    scan.startedAt = scan.startedAt ?? new Date(started).toISOString()
    scan.finishedAt = new Date().toISOString()
    scan.schedulerRunId = scan.schedulerRunId ?? `run-${scan.scanId}`
    scan.completenessPct = scan.completenessPct ?? 16.7
    scan.compliancePct = scan.compliancePct ?? 11.1
    scan.message = `application '${scan.applicationSlug}' onboarded (mock)`
  }
  return scan
}

export function mockOnboardingScanById(id: string): OnboardingScanView {
  const scan = mockScans.find((s) => s.scanId === id) ?? mockScans[0]
  return advanceMockOnboardingScan(scan)
}

// ---- UC14 comparison / UC16 enterprise / UC20 national --------------

export function mockComparison(applications?: string[], framework?: string): ComparisonReport {
  const apps = applications && applications.length >= 2
    ? applications
    : mockApplications.map((a) => a.slug)
  const reports = apps.map((slug) => ({ slug, r: mockCompliance(slug, framework) }))

  const fwMap = new Map<string, Record<string, number>>()
  for (const { slug, r } of reports) {
    for (const f of r.byFramework) {
      const m = fwMap.get(f.framework) ?? {}
      m[slug] = f.compliancePct
      fwMap.set(f.framework, m)
    }
  }
  const frameworks = [...fwMap.entries()].sort().map(([framework, byApp]) => {
    const vals = Object.values(byApp)
    const min = Math.min(...vals)
    const max = Math.max(...vals)
    return { framework, compliancePctByApp: byApp, minPct: min, maxPct: max, spreadPct: round1(max - min) }
  })

  const ctrl = new Map<string, { framework: string; controlId: string; statusByApp: Record<string, string> }>()
  const gaps: ComparisonReport['gaps'] = []
  for (const { slug, r } of reports) {
    for (const c of r.controls) {
      const key = `${c.framework}|${c.controlId}`
      const row = ctrl.get(key) ?? { framework: c.framework, controlId: c.controlId, statusByApp: {} }
      row.statusByApp[slug] = c.status
      ctrl.set(key, row)
      if (c.status !== 'COMPLIANT')
        gaps.push({ applicationSlug: slug, framework: c.framework, controlId: c.controlId, status: c.status })
    }
  }
  const controls = [...ctrl.values()].map((row) => ({
    ...row,
    consistent: new Set(Object.values(row.statusByApp)).size <= 1,
  }))
  return { generatedAt: NOW, framework: framework ?? null, applications: apps, frameworks, controls, gaps }
}

export function mockEnterprise(): EnterpriseDashboard {
  const portfolio = mockLeadershipDashboard()
  const byMeta = new Map(mockApplications.map((a) => [a.slug, a]))
  const groupBy = (keyOf: (slug: string, crit: string) => string) => {
    const acc = new Map<string, number[]>()
    for (const p of portfolio.byApplication) {
      const k = keyOf(p.applicationSlug, p.criticality)
      const a = acc.get(k) ?? [0, 0, 0, 0]
      a[0]++
      a[1] += p.expected
      a[2] += p.compliant
      a[3] += p.covered
      acc.set(k, a)
    }
    return [...acc.entries()].sort().map(([key, a]) => ({
      key,
      applications: a[0],
      expected: a[1],
      compliant: a[2],
      compliancePct: a[1] === 0 ? 0 : round1((100 * a[2]) / a[1]),
      completenessPct: a[1] === 0 ? 0 : round1((100 * a[3]) / a[1]),
    }))
  }
  const weight: Record<string, number> = { CRITICAL: 4, HIGH: 3, MEDIUM: 2, LOW: 1 }
  const topRisks = [...portfolio.byApplication]
    .sort(
      (x, y) =>
        (weight[y.criticality] ?? 2) - (weight[x.criticality] ?? 2) || x.compliancePct - y.compliancePct,
    )
    .slice(0, 5)
  return {
    generatedAt: NOW,
    portfolio,
    byBusinessUnit: groupBy((slug) => byMeta.get(slug)?.businessUnit ?? 'Unassigned'),
    byCriticality: groupBy((_slug, crit) => crit || 'MEDIUM'),
    topRisks,
  }
}

const NAT_REGIONS: Record<string, string[]> = {
  North: ['net-banking'],
  West: ['payments'],
  South: ['mobile-banking'],
  East: [],
  Central: [],
}

export function mockNational(): NationalDashboard {
  const byApp = new Map(mockLeadershipDashboard().byApplication.map((p) => [p.applicationSlug, p]))
  let te = 0
  let tc = 0
  let tcov = 0
  const regions = Object.entries(NAT_REGIONS).map(([region, slugs]) => {
    let expected = 0
    let compliant = 0
    let covered = 0
    const apps: string[] = []
    for (const s of slugs) {
      const p = byApp.get(s)
      if (!p) continue
      apps.push(s)
      expected += p.expected
      compliant += p.compliant
      covered += p.covered
    }
    te += expected
    tc += compliant
    tcov += covered
    const pct = expected === 0 ? 0 : round1((100 * compliant) / expected)
    return {
      region,
      applications: apps,
      expected,
      compliant,
      compliancePct: pct,
      completenessPct: expected === 0 ? 0 : round1((100 * covered) / expected),
      rag: (pct >= 85 ? 'GREEN' : pct >= 60 ? 'AMBER' : 'RED') as 'GREEN' | 'AMBER' | 'RED',
    }
  })
  return {
    generatedAt: NOW,
    nationalCompliancePct: te === 0 ? 0 : round1((100 * tc) / te),
    nationalCompletenessPct: te === 0 ? 0 : round1((100 * tcov) / te),
    applications: regions.flatMap((r) => r.applications).length,
    regions,
  }
}

/** Region x framework breakdown + regions ranked by gap to the national average — distinct from mockNational()'s flat table. */
export function mockNationalRollup(): NationalRollup {
  const national = mockNational()
  const byRegionFramework: RegionFrameworkRow[] = []
  const acc = new Map<string, [number, number]>() // "region|framework" -> [expected, compliant]
  for (const [region, slugs] of Object.entries(NAT_REGIONS)) {
    for (const slug of slugs) {
      const cr = mockCompliance(slug)
      for (const f of cr.byFramework) {
        const key = `${region}|${f.framework}`
        const [e, c] = acc.get(key) ?? [0, 0]
        acc.set(key, [e + f.expected, c + f.compliant])
      }
    }
  }
  for (const [key, [expected, compliant]] of [...acc.entries()].sort()) {
    const [region, framework] = key.split('|')
    byRegionFramework.push({
      region,
      framework,
      expected,
      compliant,
      compliancePct: expected === 0 ? 0 : round1((100 * compliant) / expected),
    })
  }

  const nationalPct = national.nationalCompliancePct
  const laggingRegions: RegionGap[] = [...national.regions]
    .map((r) => ({
      region: r.region,
      compliancePct: r.compliancePct,
      gapVsNationalPct: round1(r.compliancePct - nationalPct),
      rag: r.rag,
    }))
    .sort((a, b) => a.gapVsNationalPct - b.gapVsNationalPct)

  return {
    generatedAt: NOW,
    nationalCompliancePct: national.nationalCompliancePct,
    nationalCompletenessPct: national.nationalCompletenessPct,
    applications: national.applications,
    byRegionFramework,
    laggingRegions,
  }
}

// ---- UC18 audit prep ------------------------------------------------

export function mockAuditPrep(applicationSlug?: string, framework?: string): AuditPrepReport {
  const apps = applicationSlug ? [applicationSlug] : mockApplications.map((a) => a.slug)
  const findings: AuditPrepReport['findings'] = []
  let pctSum = 0
  for (const slug of apps) {
    const cp = mockCompleteness(slug, framework)
    const cr = mockCompliance(slug, framework)
    pctSum += (cp.completenessPct + cr.compliancePct) / 2
    for (const c of cp.controls) {
      if (c.coverage === 'MISSING')
        findings.push({ category: 'EVIDENCE_GAP', severity: 'HIGH', applicationSlug: slug, framework: c.framework, controlId: c.controlId, detail: 'no current evidence held', action: 'collect evidence for this control' })
      else if (c.coverage === 'STALE')
        findings.push({ category: 'EVIDENCE_STALE', severity: 'MEDIUM', applicationSlug: slug, framework: c.framework, controlId: c.controlId, detail: 'evidence is past the freshness window', action: 'refresh the evidence' })
    }
    for (const c of cr.controls) {
      if (c.status === 'NON_COMPLIANT')
        findings.push({ category: 'NON_COMPLIANT', severity: 'HIGH', applicationSlug: slug, framework: c.framework, controlId: c.controlId, detail: c.detail, action: 'remediate the failing check then re-collect evidence' })
      else if (c.status === 'PARTIALLY_COMPLIANT')
        findings.push({ category: 'PARTIAL', severity: 'MEDIUM', applicationSlug: slug, framework: c.framework, controlId: c.controlId, detail: c.detail, action: 'resolve the warning' })
    }
  }
  const bySeverity: Record<string, number> = { HIGH: 0, MEDIUM: 0, LOW: 0 }
  findings.forEach((f) => (bySeverity[f.severity] += 1))
  const readiness = apps.length === 0 ? 0 : round1(pctSum / apps.length)
  const narrative =
    `[mock-ai] Readiness ${readiness}% for ${apps.join(', ')}. ` +
    `${bySeverity.HIGH} high / ${bySeverity.MEDIUM} medium findings. ` +
    findings.slice(0, 3).map((f) => `${f.controlId}: ${f.action}`).join('; ')
  return {
    generatedAt: NOW,
    scope: applicationSlug ?? 'portfolio',
    readinessScore: readiness,
    totalFindings: findings.length,
    bySeverity,
    findings,
    narrative,
    model: 'mock-chat:v1',
    simulated: true,
  }
}

// ---- UC19 trend ---------------------------------------------------

export function mockTrend(): TrendReport {
  const weeks = 8
  const points = Array.from({ length: weeks }, (_, i) => {
    const progress = i / (weeks - 1)
    const compliancePct = round1(42 + progress * 34)
    const expected = 54
    const evidenceCount = Math.round(12 + progress * 60)
    return {
      takenAt: new Date(nowMs - (weeks - i) * 7 * dayMs).toISOString(),
      expected,
      compliant: Math.round((expected * compliancePct) / 100),
      compliancePct,
      completenessPct: round1(55 + progress * 30),
      approvedEvidence: Math.round(6 + progress * 18),
      openFindings: expected - Math.round((expected * compliancePct) / 100),
      evidenceCount,
      integrityChecked: evidenceCount,
      integrityIntact: Math.round(evidenceCount * (0.9 + progress * 0.1)),
    }
  })
  const live = mockLeadershipDashboard()
  const current = {
    takenAt: NOW,
    expected: live.expected,
    compliant: live.compliant,
    compliancePct: live.compliancePct,
    completenessPct: live.completenessPct,
    approvedEvidence: 0,
    openFindings: Math.max(0, live.expected - live.compliant),
    evidenceCount: mockEvidence.length,
    integrityChecked: mockEvidence.length,
    integrityIntact: mockEvidence.length,
  }
  return {
    generatedAt: NOW,
    current,
    points,
    closure: {
      approvals: 21,
      rejections: 3,
      resubmissions: 4,
      avgDaysToApprove: 2.4,
      approvalsByWeek: { '2026-W34': 4, '2026-W35': 6, '2026-W36': 11 },
    },
    collection: mockRuns
      .filter((r) => r.finishedAt)
      .map((r) => ({ at: r.finishedAt as string, ingested: r.ingested, duplicates: r.duplicates, failed: r.failed })),
  }
}

// ---- Predefined technical query catalogue (compact offline sample) -----

const MOCK_PREDEFINED: import('./types').PredefinedQueryItem[] = [
  { controlId: 'DB-001', technology: 'PostgreSQL', controlName: 'SSL Enabled', command: 'SHOW ssl;', frameworks: ['DB Baselining', 'PCI DSS', 'DPSC'], evidenceType: 'SQL Output', executableNow: false, runtimeStatus: 'Dependency Missing', controlFamily: 'Encryption in Transit' },
  { controlId: 'DB-014', technology: 'PostgreSQL', controlName: 'Password Encryption', command: 'SHOW password_encryption;', frameworks: ['DB Baselining', 'CSITE'], evidenceType: 'SQL Output', executableNow: false, runtimeStatus: 'Dependency Missing', controlFamily: 'Authentication' },
  { controlId: 'ORA-003', technology: 'Oracle', controlName: 'Audit Trail Enabled', command: "SELECT value FROM v$parameter WHERE name='audit_trail';", frameworks: ['DB Baselining', 'RBI Cyber Security'], evidenceType: 'SQL Output', executableNow: false, runtimeStatus: 'Connector Missing', controlFamily: 'Audit Logging' },
  { controlId: 'LNX-007', technology: 'Linux', controlName: 'SSH Root Login Disabled', command: 'grep -i "^PermitRootLogin" /etc/ssh/sshd_config', frameworks: ['OS Baselining', 'CSITE'], evidenceType: 'Shell Command', executableNow: true, runtimeStatus: 'Ready', controlFamily: 'Remote Access' },
  { controlId: 'LNX-021', technology: 'Linux', controlName: 'Auditd Running', command: 'systemctl is-active auditd', frameworks: ['OS Baselining'], evidenceType: 'Shell Command', executableNow: true, runtimeStatus: 'Ready', controlFamily: 'Audit Logging' },
  { controlId: 'WIN-004', technology: 'Windows', controlName: 'Password History', command: 'Get-ADDefaultDomainPasswordPolicy | Select PasswordHistoryCount', frameworks: ['OS Baselining', 'MBSS'], evidenceType: 'PowerShell', executableNow: false, runtimeStatus: 'Configuration Required', controlFamily: 'Authentication' },
  { controlId: 'NGX-002', technology: 'NGINX', controlName: 'TLS Protocols', command: 'nginx -T | grep ssl_protocols', frameworks: ['Middleware Baselining', 'PCI DSS'], evidenceType: 'Shell Command', executableNow: true, runtimeStatus: 'Ready', controlFamily: 'Encryption in Transit' },
  { controlId: 'TOM-005', technology: 'Tomcat', controlName: 'Shutdown Port Disabled', command: 'grep -i shutdown server.xml', frameworks: ['Middleware Baselining'], evidenceType: 'Shell Command', executableNow: false, runtimeStatus: 'Configuration Required', controlFamily: 'Hardening' },
  { controlId: 'K8S-009', technology: 'Kubernetes', controlName: 'Pod Security Admission', command: 'kubectl get ns -o json', frameworks: ['Container Platform Baselining'], evidenceType: 'API', executableNow: false, runtimeStatus: 'Connector Missing', controlFamily: 'Workload Security' },
  { controlId: 'TRV-001', technology: 'Trivy', controlName: 'Image Vulnerability Scan', command: 'trivy image --severity HIGH,CRITICAL', frameworks: ['VAPT', 'AppSec'], evidenceType: 'API', executableNow: true, runtimeStatus: 'Ready', controlFamily: 'Vulnerability Management' },
  { controlId: 'GTL-001', technology: 'GitLeaks', controlName: 'Secret Scan', command: 'gitleaks detect --report-format json', frameworks: ['AppSec', 'AI SDLC'], evidenceType: 'API', executableNow: true, runtimeStatus: 'Ready', controlFamily: 'Secrets Management' },
  { controlId: 'SNQ-002', technology: 'SonarQube', controlName: 'Quality Gate Status', command: 'GET /api/qualitygates/project_status', frameworks: ['AppSec', 'ASST'], evidenceType: 'API', executableNow: false, runtimeStatus: 'Connector Missing', controlFamily: 'Code Quality' },
]

function pqFilter(technology?: string, framework?: string, controlFamily?: string) {
  return MOCK_PREDEFINED.filter(
    (q) =>
      (!technology || q.technology === technology) &&
      (!framework || q.frameworks.includes(framework)) &&
      (!controlFamily || q.controlFamily === controlFamily),
  )
}

export function mockPredefinedQueryCatalog(technology?: string, framework?: string, controlFamily?: string) {
  const items = pqFilter(technology, framework, controlFamily)
  return {
    total: items.length,
    technologies: [...new Set(MOCK_PREDEFINED.map((q) => q.technology))].sort(),
    frameworks: [...new Set(MOCK_PREDEFINED.flatMap((q) => q.frameworks))].sort(),
    controlFamilies: [...new Set(MOCK_PREDEFINED.map((q) => q.controlFamily))].sort(),
    items,
  }
}

function pqApp(applicationSlug?: string): string {
  const active = mockApplications.filter((a) => a.active)
  if (applicationSlug) {
    if (!active.some((a) => a.slug === applicationSlug)) {
      throw new Error(`Application '${applicationSlug}' is not onboarded — onboard it before running predefined queries.`)
    }
    return applicationSlug
  }
  if (active.length === 0) throw new Error('No applications onboarded — onboard at least one application before running predefined queries.')
  return active[0].slug
}

export function mockRunPredefinedQuery(controlId: string, applicationSlug?: string) {
  const q = MOCK_PREDEFINED.find((x) => x.controlId === controlId)
  if (!q) throw new Error(`Unknown predefined query: ${controlId}`)
  const app = pqApp(applicationSlug)
  return {
    controlId, technology: q.technology, applicationSlug: app, mode: 'SIMULATED',
    outcome: 'CREATED', evidenceId: `ev-pq-${controlId}`, sha256: 'a'.repeat(64), error: null,
    outputPreview: `simulated result for ${q.technology} / ${controlId}: compliant`,
  }
}

export function mockRunAllPredefinedQueries(
  technology?: string, framework?: string, controlFamily?: string, applicationSlug?: string,
) {
  const app = pqApp(applicationSlug)
  const items = pqFilter(technology, framework, controlFamily)
  const results = items.map((q) => ({
    controlId: q.controlId, technology: q.technology, applicationSlug: app, mode: 'SIMULATED',
    outcome: 'CREATED', evidenceId: `ev-pq-${q.controlId}`, sha256: 'a'.repeat(64), error: null,
    outputPreview: `simulated result for ${q.technology} / ${q.controlId}: compliant`,
  }))
  return {
    received: items.length, ingested: items.length, duplicates: 0, failed: 0,
    applicationSlug: app, mode: 'SIMULATED',
    message: `ran ${items.length} predefined queries for '${app}' (SIMULATED): ${items.length} ingested, 0 duplicates, 0 failed`,
    results,
  }
}

export const mockReports: ReportInfo[] = [
  { name: 'compliance-summary', title: 'Portfolio & enterprise compliance summary', formats: ['json', 'csv'], params: [] },
  { name: 'evidence-register', title: 'Evidence register (all records)', formats: ['json', 'csv'], params: ['applicationSlug', 'framework'] },
  { name: 'gap-report', title: 'Cross-application control gap report', formats: ['json', 'csv'], params: ['framework'] },
  { name: 'audit-readiness', title: 'AI-assisted audit-readiness checklist', formats: ['json', 'csv'], params: ['applicationSlug', 'framework'] },
  { name: 'pan-india', title: 'National / pan-India compliance report', formats: ['json', 'csv'], params: [] },
  { name: 'regulatory-filing', title: 'Regulator-ready compliance filing', formats: ['json', 'csv'], params: ['applicationSlug', 'framework'] },
]

// ---- UC17 regulator-ready filing (fixed cover-page schema) --------------

const REGULATOR_BY_FRAMEWORK: Record<string, string> = {
  PCI_DSS: 'PCI Security Standards Council',
  ITPP: 'Internal IT Policy & Procedures Board',
  DPSC: 'Data Protection Supervisory Council',
}
const DEFAULT_REGULATOR = 'Compliance Authority'
const FILING_PERIOD_DAYS = 90

export function mockRegulatoryFiling(applicationSlug?: string, framework?: string): RegulatoryFiling {
  const scoped = !!applicationSlug
  const now = NOW
  const periodStart = new Date(Date.parse(now) - FILING_PERIOD_DAYS * dayMs).toISOString()

  let byFramework: FrameworkPosture[]
  let applicationsInScope: number
  let expected: number
  let compliant: number
  let evidenceRecords: number
  let openGaps: number

  if (scoped) {
    const cr = mockCompliance(applicationSlug!, framework)
    byFramework = cr.byFramework
    applicationsInScope = 1
    expected = cr.expected
    compliant = cr.compliant
    evidenceRecords = mockEvidencePage({ applicationSlug, framework, page: 0, size: 100_000 }).totalItems
    openGaps = cr.controls.filter((c) => c.status !== 'COMPLIANT').length
  } else {
    const portfolio = mockLeadershipDashboard()
    byFramework = framework
      ? portfolio.byFramework.filter((f) => f.framework.toUpperCase() === framework.toUpperCase())
      : portfolio.byFramework
    applicationsInScope = portfolio.applications
    expected = byFramework.reduce((s, f) => s + f.expected, 0)
    compliant = byFramework.reduce((s, f) => s + f.compliant, 0)
    evidenceRecords = mockEvidenceDashboard().records
    openGaps = byFramework.reduce((s, f) => s + f.nonCompliant + f.missingEvidence, 0)
  }

  const compliancePct = expected === 0 ? 0 : round1((100 * compliant) / expected)
  const filings: FrameworkFiling[] = byFramework.map((f) => ({
    framework: f.framework,
    regulator: REGULATOR_BY_FRAMEWORK[f.framework.toUpperCase()] ?? DEFAULT_REGULATOR,
    expected: f.expected,
    compliant: f.compliant,
    nonCompliant: f.nonCompliant,
    missingEvidence: f.missingEvidence,
    compliancePct: f.compliancePct,
  }))

  const scope = scoped ? applicationSlug! : 'PORTFOLIO'
  const scopedFramework = framework ? framework.toUpperCase() : 'ALL'
  const reportId = `REG-${now.slice(0, 10).replace(/-/g, '')}-${shortDigest(`${scope}|${scopedFramework}|${now}`)}`
  const regulator = filings.length === 1 ? filings[0].regulator : 'Multiple Regulatory Bodies'

  return {
    reportId,
    title: 'Regulatory Compliance Filing',
    regulator,
    scope,
    framework: scopedFramework,
    periodStart,
    periodEnd: now,
    generatedAt: now,
    preparedBy: 'Pramaan Next (automated)',
    applicationsInScope,
    controlsExpected: expected,
    controlsCompliant: compliant,
    compliancePct,
    evidenceRecords,
    openGaps,
    frameworks: filings,
    attestation:
      `This filing reflects deterministic evidence and control-verdict data held by Pramaan Next as of ${now}. ` +
      'Figures are computed, not model-generated.',
  }
}

// ---- Use Case 13 — evidence lifecycle --------------------------------

const LIFECYCLE: Map<string, EvidenceLifecycleView> = new Map()

function baseLifecycle(evidenceId: string): EvidenceLifecycleView {
  const ev = mockEvidenceById(evidenceId)
  const collectedAt = ev.latest?.collectedAt ?? NOW
  const age = Math.max(0, Math.round(ageDays(collectedAt)))
  return {
    evidenceId,
    applicationSlug: ev.applicationSlug,
    controlId: ev.controlId,
    state: (ev.lifecycleState as string) || 'DRAFT',
    effectiveState: (ev.lifecycleState as string) || 'DRAFT',
    expired: false,
    reviewedBy: null,
    reviewedAt: null,
    note: null,
    retentionDays: 365,
    ageDays: age,
    expiresAt: new Date(Date.parse(collectedAt) + 365 * dayMs).toISOString(),
    currentVersion: ev.currentVersion,
    history: [
      { fromState: null, toState: 'DRAFT', action: 'INGESTED', actor: null, note: 'evidence ingested', occurredAt: collectedAt },
    ],
  }
}

export function mockLifecycle(evidenceId: string): EvidenceLifecycleView {
  return LIFECYCLE.get(evidenceId) ?? baseLifecycle(evidenceId)
}

const NEXT_STATE: Record<LifecycleAction, string> = {
  SUBMIT: 'SUBMITTED',
  APPROVE: 'APPROVED',
  REJECT: 'REJECTED',
  RETIRE: 'SUPERSEDED',
  RESET: 'DRAFT',
}

export function mockLifecycleTransition(
  evidenceId: string,
  action: LifecycleAction,
  actor?: string,
  note?: string,
): EvidenceLifecycleView {
  const cur = mockLifecycle(evidenceId)
  const to = NEXT_STATE[action]
  const next: EvidenceLifecycleView = {
    ...cur,
    state: to,
    effectiveState: to,
    reviewedBy: action === 'APPROVE' || action === 'REJECT' ? actor ?? null : cur.reviewedBy,
    reviewedAt: action === 'APPROVE' || action === 'REJECT' ? NOW : cur.reviewedAt,
    note: note ?? cur.note,
    history: [
      ...cur.history,
      { fromState: cur.state, toState: to, action, actor: actor ?? null, note: note ?? null, occurredAt: NOW },
    ],
  }
  LIFECYCLE.set(evidenceId, next)
  return next
}

/** Mock for the multipart bulk-upload endpoint: builds one `IngestRequest` per selected
 *  file (a real .zip is not expanded client-side — that's server-only behavior — so in
 *  mock mode a .zip is just ingested as a single opaque item, same as any other file). */
export function mockBulkIngestFiles(
  files: File[],
  meta: { applicationSlug: string; framework: string; controlId: string; sourceSystem?: string },
): BulkIngestResponse {
  const items: IngestRequest[] = files.map((f) => ({
    applicationSlug: meta.applicationSlug,
    controlId: meta.controlId,
    framework: meta.framework,
    sourceSystem: meta.sourceSystem || 'BULK_UPLOAD',
    sourceObjectId: f.name,
    title: f.name,
    contentText: f.name,
  }))
  return mockBulkIngest(items)
}

export function mockBulkIngest(items: IngestRequest[]): BulkIngestResponse {
  const results = items.map((it, i) => ({
    evidenceId: `bulk-${String(i).padStart(3, '0')}`,
    evidenceKey: `${it.applicationSlug}|${it.framework}|${it.controlId}|${it.sourceSystem}|-`,
    applicationSlug: it.applicationSlug,
    controlId: it.controlId,
    framework: it.framework,
    sourceSystem: it.sourceSystem,
    outcome: i === 1 ? 'DUPLICATE' : 'CREATED',
    version: 1,
    sha256: 'b'.repeat(64),
    sizeBytes: (it.contentText ?? it.contentBase64 ?? '').length,
    sourceObjectId: it.sourceObjectId ?? it.title ?? null,
  }))
  const duplicates = results.filter((r) => r.outcome === 'DUPLICATE').length
  return { received: items.length, created: results.length - duplicates, newVersions: 0, duplicates, failed: 0, results, errors: [] }
}

export const mockRuns: RunView[] = runsRaw.runs.map((r) => ({ ...r })) as unknown as RunView[]
export const mockSources: string[] = runsRaw.sources

let runSeq = 900
// Wall-clock start per mock run, so repeated polls can walk it through
// PENDING -> RUNNING -> COMPLETED (mirrors what the real backend does).
const runStartedAt = new Map<string, number>()
const runEvidenceAdded = new Set<string>()

export function mockStartRun(req: RunRequest): RunView {
  if (!mockApplications.some((a) => a.active)) {
    throw new Error(
      'No applications onboarded — onboard at least one application before running the scheduler.',
    )
  }
  runSeq += 1
  const run: RunView = {
    runId: `run-${runSeq}`,
    trigger: 'MANUAL',
    status: 'PENDING',
    requestedBy: req.requestedBy ?? 'ui',
    applications: req.applications ?? [],
    frameworks: req.frameworks ?? [],
    sources: req.sources ?? mockSources,
    received: 0,
    ingested: 0,
    duplicates: 0,
    failed: 0,
    message: 'queued (mock)',
    perSource: {},
    createdAt: NOW,
    startedAt: null,
    finishedAt: null,
  }
  mockRuns.unshift(run)
  runStartedAt.set(run.runId, Date.now())
  return run
}

/** Progress a mock run based on elapsed wall-clock time since it was started. */
function advanceMockRun(run: RunView): RunView {
  const started = runStartedAt.get(run.runId)
  if (started === undefined || run.status === 'COMPLETED' || run.status === 'FAILED') return run
  const elapsed = Date.now() - started
  if (elapsed < 2000) {
    run.status = 'PENDING'
  } else if (elapsed < 6000) {
    run.status = 'RUNNING'
    run.startedAt = run.startedAt ?? new Date(started + 2000).toISOString()
    run.received = 6
    run.ingested = 3
    run.message = 'collecting (mock)'
  } else {
    run.status = 'COMPLETED'
    run.startedAt = run.startedAt ?? new Date(started + 2000).toISOString()
    run.finishedAt = new Date().toISOString()
    run.received = 8
    run.ingested = 6
    run.duplicates = 2
    run.message = 'completed (mock)'
    if (!runEvidenceAdded.has(run.runId) && mockEvidence.length > 0) {
      runEvidenceAdded.add(run.runId)
      const seed = mockEvidence[0]
      const copy = JSON.parse(JSON.stringify(seed)) as EvidenceView
      copy.evidenceId = `ev-${run.runId}`
      copy.tags = { ...copy.tags, collectionMethod: 'scheduled' }
      mockEvidence.unshift(copy)
    }
  }
  return run
}

export function mockRunById(id: string): RunView {
  const run = mockRuns.find((r) => r.runId === id) ?? mockRuns[0]
  return advanceMockRun(run)
}

/** Walk every in-flight mock run forward — used by the runs list endpoint. */
export function mockRunsList(): RunView[] {
  mockRuns.forEach(advanceMockRun)
  return mockRuns
}

// Per-check PASS / WARNING / FAIL rows from the shared fixtures.
export const mockResults = resultsRaw.results
export const mockResultStatusValues = resultsRaw.statusValues

// ---- control results (deterministic rule evaluation) --------------------

const catalogRaw = agentsRaw.collectorCatalog

export const mockChecks: CheckDefView[] = catalogRaw.flatMap((c) =>
  c.checks.map((ck) => ({
    checkId: ck.id,
    collector: c.type,
    technology: c.technologies[0] ?? '',
    controlId: ck.controlId,
    framework: ck.framework,
    title: ck.title,
  })),
)

function checkResultViews(): CheckResultView[] {
  return resultsRaw.results.map((r) => {
    const ev = mockEvidence.find((e) => e.evidenceId === r.evidenceId)
    return {
      id: r.id,
      evidenceId: r.evidenceId,
      evidenceVersion: ev?.currentVersion ?? 1,
      applicationSlug: r.application,
      checkId: r.checkId,
      controlId: r.controlId,
      framework: r.framework,
      sourceSystem: ev?.sourceSystem ?? '',
      status: r.status,
      observed: r.observed,
      expected: r.expected,
      detail: r.detail,
      collectedAt: r.collectedAt,
    }
  })
}

export function mockCheckResultsPage(params: CheckResultParams): Page<CheckResultView> {
  let items = checkResultViews()
  const { applicationSlug, framework, controlId, sourceSystem, status } = params
  if (applicationSlug) items = items.filter((r) => r.applicationSlug === applicationSlug)
  if (framework) items = items.filter((r) => r.framework === framework.toUpperCase())
  if (controlId) items = items.filter((r) => r.controlId === controlId.toUpperCase())
  if (sourceSystem) items = items.filter((r) => r.sourceSystem === sourceSystem.toUpperCase())
  if (status) items = items.filter((r) => r.status === status)
  const size = params.size ?? 20
  const page = params.page ?? 0
  const start = page * size
  return {
    items: items.slice(start, start + size),
    page,
    size,
    totalItems: items.length,
    totalPages: Math.max(1, Math.ceil(items.length / size)),
  }
}

export function mockEvaluate(params: CheckResultParams = {}): EvaluationSummary {
  const rows = mockCheckResultsPage({ ...params, page: 0, size: 10_000 }).items
  const byStatus: Record<string, number> = {}
  rows.forEach((r) => (byStatus[r.status] = (byStatus[r.status] ?? 0) + 1))
  return {
    evaluatedAt: NOW,
    evidenceEvaluated: new Set(rows.map((r) => r.evidenceId)).size,
    resultsWritten: rows.length,
    byStatus,
  }
}

// ---- Phase 2: completeness / compliance / reuse / summary / NL query ----
//
// The expected-control catalog for the mock is frameworks.json's `controls`.
// (The live backend uses phase2/control-catalog.json, which is a superset.)

const CONTROL_CATALOG = frameworksRaw.controls as Array<{
  id: string
  framework: string
  title: string
}>

function evidenceFor(app: string, framework: string, controlId: string): EvidenceView | undefined {
  return mockEvidence.find(
    (e) =>
      e.applicationSlug === app &&
      e.framework === framework &&
      e.controlId === controlId,
  )
}

function appSlugs(slug?: string): string[] {
  return slug ? [slug] : mockApplications.map((a) => a.slug)
}

function round1(v: number): number {
  return Math.round(v * 10) / 10
}

export function mockCompleteness(applicationSlug: string, framework?: string): CompletenessReport {
  const cat = CONTROL_CATALOG.filter((c) => !framework || c.framework === framework.toUpperCase())
  const controls: ControlCoverage[] = cat
    .map((c) => {
      const ev = evidenceFor(applicationSlug, c.framework, c.id)
      if (!ev || !ev.latest) {
        return {
          framework: c.framework,
          controlId: c.id,
          title: c.title,
          coverage: 'MISSING' as Coverage,
          evidenceId: null,
          currentVersion: null,
          lastCollectedAt: null,
          ageDays: null,
        }
      }
      const age = Math.floor(ageDays(ev.latest.collectedAt))
      return {
        framework: c.framework,
        controlId: c.id,
        title: c.title,
        coverage: (age > STALE_DAYS ? 'STALE' : 'COVERED') as Coverage,
        evidenceId: ev.evidenceId,
        currentVersion: ev.currentVersion,
        lastCollectedAt: ev.latest.collectedAt,
        ageDays: age,
      }
    })
    .sort((a, b) => a.framework.localeCompare(b.framework) || a.controlId.localeCompare(b.controlId))

  const covered = controls.filter((c) => c.coverage === 'COVERED').length
  const stale = controls.filter((c) => c.coverage === 'STALE').length
  const missing = controls.filter((c) => c.coverage === 'MISSING').length
  return {
    applicationSlug,
    framework: framework ?? null,
    generatedAt: NOW,
    staleAfterDays: STALE_DAYS,
    expected: controls.length,
    covered,
    stale,
    missing,
    completenessPct: controls.length === 0 ? 0 : round1((100 * covered) / controls.length),
    controls,
  }
}

// ---- per-evidence-item completeness (mirrors backend/insight/EvidenceCompletenessService) ----

const EC_WEIGHT_INTEGRITY = 25
const EC_WEIGHT_CONTENT = 20
const EC_WEIGHT_FRESHNESS = 20
const EC_WEIGHT_CONTROL_MAPPING = 15
const EC_WEIGHT_COLLECTED_BY = 10
const EC_WEIGHT_TECHNOLOGY = 10
const EC_MIN_SUBSTANTIVE_BYTES = 10

function ecBand(score: number): CompletenessBand {
  if (score >= 90) return 'COMPLETE'
  if (score >= 60) return 'PARTIAL'
  return 'INCOMPLETE'
}

function isControlMapped(controlId: string): boolean {
  return controlId?.toUpperCase() in CONTROL_FRAMEWORKS
}

function scoreEvidenceCompleteness(e: EvidenceView): EvidenceCompletenessItem {
  const factors: EvidenceCompletenessFactor[] = []
  let score = 0
  const ok = (factor: string, detail: string) => factors.push({ factor, status: 'ok', detail })
  const fail = (factor: string, detail: string) => factors.push({ factor, status: 'fail', detail })

  ok('Application', `Application recorded: ${e.applicationSlug}.`)
  ok('Framework', `Framework recorded: ${e.framework}.`)
  ok('Source system', `Source system recorded: ${e.sourceSystem}.`)

  const integrityStatus = e.integrityStatus ?? 'UNKNOWN'
  if (!e.latest) {
    fail('Integrity', 'No version recorded — nothing to verify.')
    fail('Evidence content', 'No evidence content has ever been collected.')
  } else if (integrityStatus === 'VERIFIED') {
    score += EC_WEIGHT_INTEGRITY
    ok('Integrity', 'SHA-256 matches the recorded hash.')
    if (e.latest.sizeBytes >= EC_MIN_SUBSTANTIVE_BYTES) {
      score += EC_WEIGHT_CONTENT
      ok('Evidence content', `Content is present and non-trivial (${e.latest.sizeBytes} bytes).`)
    } else {
      fail('Evidence content', `Content is empty or looks like a placeholder (${e.latest.sizeBytes} bytes).`)
    }
  } else if (integrityStatus === 'TAMPERED') {
    fail('Integrity', 'Recorded hash does not match current content — integrity check failed.')
    fail('Evidence content', 'Content cannot be trusted: the integrity check on it failed.')
  } else {
    fail('Integrity', 'Stored object could not be verified against the object store.')
    fail('Evidence content', 'Content is unavailable — cannot confirm it is non-trivial.')
  }

  let ageInDays: number | null = null
  if (e.latest) {
    ageInDays = Math.floor(ageDays(e.latest.collectedAt))
    if (ageInDays <= STALE_DAYS) {
      score += EC_WEIGHT_FRESHNESS
      ok('Freshness', `Last collected ${ageInDays} day(s) ago.`)
    } else {
      fail('Freshness', `Last collected ${ageInDays} days ago, exceeds the ${STALE_DAYS}-day threshold.`)
    }
  } else {
    fail('Freshness', 'No collection date recorded.')
  }

  const collectedBy = e.latest?.collectedBy
  if (collectedBy && collectedBy.trim()) {
    score += EC_WEIGHT_COLLECTED_BY
    ok('Collected by', `Recorded as "${collectedBy}".`)
  } else {
    fail('Collected by', 'Missing: collector identity not recorded.')
  }

  const technology = e.tags.technology
  const technologyKnown = !!technology && technology.toLowerCase() !== 'unknown'
  if (technologyKnown) {
    score += EC_WEIGHT_TECHNOLOGY
    ok('Technology', `Recorded as "${technology}".`)
  } else {
    fail('Technology', 'Missing: technology not recorded.')
  }

  const mapped = isControlMapped(e.controlId)
  if (mapped) {
    score += EC_WEIGHT_CONTROL_MAPPING
    ok('Control mapping', `Control ${e.controlId} is mapped to a known framework set.`)
  } else {
    fail('Control mapping', `Control ${e.controlId} is not in the control-framework catalogue (orphaned/unmapped).`)
  }

  return {
    evidenceId: e.evidenceId,
    applicationSlug: e.applicationSlug,
    framework: e.framework,
    controlId: e.controlId,
    sourceSystem: e.sourceSystem,
    collectedBy: collectedBy ?? null,
    technology: technologyKnown ? technology : null,
    currentVersion: e.currentVersion,
    lastCollectedAt: e.latest?.collectedAt ?? null,
    ageDays: ageInDays,
    sha256: e.latest?.sha256 ?? null,
    integrityStatus,
    completenessPct: score,
    band: ecBand(score),
    factors,
  }
}

export function mockEvidenceCompleteness(applicationSlug?: string, framework?: string): EvidenceCompletenessReport {
  let scope = mockEvidence.slice()
  if (applicationSlug) scope = scope.filter((e) => e.applicationSlug === applicationSlug)
  if (framework) scope = scope.filter((e) => e.framework === framework.toUpperCase())

  const items = scope
    .map(scoreEvidenceCompleteness)
    .sort(
      (a, b) =>
        a.applicationSlug.localeCompare(b.applicationSlug) ||
        a.controlId.localeCompare(b.controlId) ||
        a.evidenceId.localeCompare(b.evidenceId),
    )

  const completeCount = items.filter((i) => i.band === 'COMPLETE').length
  const partialCount = items.filter((i) => i.band === 'PARTIAL').length
  const incompleteCount = items.length - completeCount - partialCount
  const avgCompletenessPct = items.length === 0 ? 0 : round1(items.reduce((s, i) => s + i.completenessPct, 0) / items.length)

  return {
    applicationSlug: applicationSlug ?? null,
    framework: framework ?? null,
    generatedAt: NOW,
    staleAfterDays: STALE_DAYS,
    totalItems: items.length,
    avgCompletenessPct,
    completeCount,
    partialCount,
    incompleteCount,
    items,
  }
}

// ---- framework/control rollup (mirrors backend/insight/EvidenceCompletenessService rollup) ----

function rollupKey(framework: string, controlId: string): string {
  return `${framework.toUpperCase()}|${controlId.toUpperCase()}`
}

function groupByRollupKey(items: EvidenceCompletenessItem[]): Map<string, EvidenceCompletenessItem[]> {
  const byKey = new Map<string, EvidenceCompletenessItem[]>()
  for (const i of items) {
    const key = rollupKey(i.framework, i.controlId)
    const list = byKey.get(key)
    if (list) list.push(i)
    else byKey.set(key, [i])
  }
  return byKey
}

export function mockEvidenceCompletenessFrameworks(applicationSlug?: string): FrameworkCompletenessRow[] {
  const byKey = groupByRollupKey(mockEvidenceCompleteness(applicationSlug).items)

  const byFramework = new Map<string, typeof CONTROL_CATALOG>()
  for (const c of CONTROL_CATALOG) {
    const list = byFramework.get(c.framework)
    if (list) list.push(c)
    else byFramework.set(c.framework, [c])
  }

  return Array.from(byFramework.entries())
    .map(([framework, controls]) => {
      let evaluated = 0
      const perControlAvg: number[] = []
      for (const c of controls) {
        const mapped = byKey.get(rollupKey(framework, c.id)) ?? []
        if (mapped.length > 0) {
          evaluated++
          perControlAvg.push(mapped.reduce((s, i) => s + i.completenessPct, 0) / mapped.length)
        }
      }
      const avgCompletenessPct =
        perControlAvg.length === 0 ? null : round1(perControlAvg.reduce((s, v) => s + v, 0) / perControlAvg.length)
      return {
        framework,
        totalControls: controls.length,
        controlsEvaluated: evaluated,
        controlsNotEvaluated: controls.length - evaluated,
        avgCompletenessPct,
      }
    })
    .sort((a, b) => a.framework.localeCompare(b.framework))
}

export function mockEvidenceCompletenessControls(
  framework: string,
  applicationSlug?: string,
): ControlCompletenessRow[] {
  const byControl = new Map<string, EvidenceCompletenessItem[]>()
  for (const i of mockEvidenceCompleteness(applicationSlug, framework).items) {
    const key = i.controlId.toUpperCase()
    const list = byControl.get(key)
    if (list) list.push(i)
    else byControl.set(key, [i])
  }

  return CONTROL_CATALOG.filter((c) => c.framework === framework.toUpperCase())
    .map((c) => {
      const mapped = byControl.get(c.id.toUpperCase()) ?? []
      const evaluated = mapped.length > 0
      const completenessPct = evaluated
        ? round1(mapped.reduce((s, i) => s + i.completenessPct, 0) / mapped.length)
        : null
      return { controlId: c.id, title: c.title, evaluated, completenessPct, evidenceCount: mapped.length }
    })
    .sort((a, b) => a.controlId.localeCompare(b.controlId))
}

export function mockEvidenceCompletenessControlEvidence(
  framework: string,
  controlId: string,
  applicationSlug?: string,
): EvidenceCompletenessItem[] {
  return mockEvidenceCompleteness(applicationSlug, framework).items.filter(
    (i) => i.controlId.toUpperCase() === controlId.toUpperCase(),
  )
}

function postureFor(app: string, framework: string, controlId: string): ControlStatus {
  const ev = evidenceFor(app, framework, controlId)
  if (!ev) return 'MISSING_EVIDENCE'
  const verdicts = resultsRaw.results.filter(
    (r) => r.application === app && r.framework === framework && r.controlId === controlId,
  )
  if (verdicts.length === 0) return 'NOT_ASSESSED'
  if (verdicts.some((v) => v.status === 'FAIL')) return 'NON_COMPLIANT'
  if (verdicts.some((v) => v.status === 'WARNING')) return 'PARTIALLY_COMPLIANT'
  if (verdicts.some((v) => v.status === 'PASS')) return 'COMPLIANT'
  return 'NOT_ASSESSED'
}

export function mockCompliance(applicationSlug: string, framework?: string): ComplianceReport {
  const cat = CONTROL_CATALOG.filter((c) => !framework || c.framework === framework.toUpperCase())
  const controls: ControlPosture[] = cat
    .map((c) => ({
      framework: c.framework,
      controlId: c.id,
      status: postureFor(applicationSlug, c.framework, c.id),
      detail: c.title,
    }))
    .sort((a, b) => a.framework.localeCompare(b.framework) || a.controlId.localeCompare(b.controlId))

  const byFwMap = new Map<string, ControlPosture[]>()
  controls.forEach((c) => byFwMap.set(c.framework, [...(byFwMap.get(c.framework) ?? []), c]))
  const count = (list: ControlPosture[], s: ControlStatus) => list.filter((c) => c.status === s).length
  const byFramework: FrameworkPosture[] = [...byFwMap.entries()]
    .sort()
    .map(([fw, list]) => ({
      framework: fw,
      expected: list.length,
      compliant: count(list, 'COMPLIANT'),
      partiallyCompliant: count(list, 'PARTIALLY_COMPLIANT'),
      nonCompliant: count(list, 'NON_COMPLIANT'),
      notAssessed: count(list, 'NOT_ASSESSED'),
      missingEvidence: count(list, 'MISSING_EVIDENCE'),
      compliancePct: list.length === 0 ? 0 : round1((100 * count(list, 'COMPLIANT')) / list.length),
    }))

  const compliant = controls.filter((c) => c.status === 'COMPLIANT').length
  return {
    applicationSlug,
    generatedAt: NOW,
    expected: controls.length,
    compliant,
    compliancePct: controls.length === 0 ? 0 : round1((100 * compliant) / controls.length),
    byFramework,
    controls,
  }
}

export function mockLeadershipDashboard(): LeadershipDashboard {
  const apps = mockApplications
  let expected = 0
  let compliant = 0
  let covered = 0
  let stale = 0
  let missing = 0
  const fw = new Map<string, [number, number, number, number, number, number]>()

  const byApplication = apps.map((a) => {
    const cr = mockCompliance(a.slug)
    const cp = mockCompleteness(a.slug)
    const nonCompliant = cr.controls.filter((c) => c.status === 'NON_COMPLIANT').length
    const missingEvidence = cr.controls.filter((c) => c.status === 'MISSING_EVIDENCE').length
    expected += cr.expected
    compliant += cr.compliant
    covered += cp.covered
    stale += cp.stale
    missing += cp.missing
    for (const f of cr.byFramework) {
      const acc = fw.get(f.framework) ?? [0, 0, 0, 0, 0, 0]
      acc[0] += f.expected
      acc[1] += f.compliant
      acc[2] += f.partiallyCompliant
      acc[3] += f.nonCompliant
      acc[4] += f.notAssessed
      acc[5] += f.missingEvidence
      fw.set(f.framework, acc)
    }
    return {
      applicationSlug: a.slug,
      name: a.name,
      criticality: a.criticality ?? 'MEDIUM',
      expected: cr.expected,
      compliant: cr.compliant,
      compliancePct: cr.compliancePct,
      covered: cp.covered,
      missing: cp.missing,
      completenessPct: cp.completenessPct,
      nonCompliant,
      missingEvidence,
    }
  })

  const verdicts: Record<string, number> = { PASS: 0, WARNING: 0, FAIL: 0, NOT_APPLICABLE: 0 }
  for (const r of mockCheckResultsPage({ size: 1000 }).items) {
    verdicts[r.status] = (verdicts[r.status] ?? 0) + 1
  }

  return {
    generatedAt: NOW,
    applications: apps.length,
    expected,
    compliant,
    compliancePct: expected === 0 ? 0 : round1((100 * compliant) / expected),
    covered,
    stale,
    missing,
    completenessPct: expected === 0 ? 0 : round1((100 * covered) / expected),
    checkVerdicts: verdicts,
    byApplication,
    byFramework: [...fw.entries()].sort().map(([framework, a]) => ({
      framework,
      expected: a[0],
      compliant: a[1],
      partiallyCompliant: a[2],
      nonCompliant: a[3],
      notAssessed: a[4],
      missingEvidence: a[5],
      compliancePct: a[0] === 0 ? 0 : round1((100 * a[1]) / a[0]),
    })),
  }
}

function tokens(e: EvidenceView): Set<string> {
  const family = e.controlId.split('-')[0]
  const raw = [
    e.applicationSlug,
    e.framework,
    e.controlId,
    family,
    ...Object.keys(e.tags),
    ...Object.values(e.tags),
  ]
    .join(' ')
    .toLowerCase()
    .match(/[a-z0-9]+/g)
  return new Set(raw ?? [])
}

function jaccard(a: Set<string>, b: Set<string>): number {
  let inter = 0
  a.forEach((t) => b.has(t) && inter++)
  const union = a.size + b.size - inter
  return union === 0 ? 0 : inter / union
}

function reuseMatches(
  queryTokens: Set<string>,
  limit: number,
  minScore: number,
  excludeId: string | undefined,
  queryApp: string | undefined,
  queryControl: string | undefined,
  querySha?: string | null,
): SimilarEvidence[] {
  return mockEvidence
    .filter((e) => e.evidenceId !== excludeId)
    .map((e) => {
      const score = round3(jaccard(queryTokens, tokens(e)))
      const crossApplication = !!queryApp && queryApp !== e.applicationSlug
      const sameControl = !!queryControl && queryControl === e.controlId
      const exactDuplicate = !!querySha && (e.latest?.sha256 ?? null) === querySha
      let reuseHint = 'related evidence'
      if (exactDuplicate) reuseHint = 'exact SHA-256 duplicate — reuse instead of re-collecting'
      else if (sameControl && crossApplication)
        reuseHint = `same control already evidenced for ${e.applicationSlug} — candidate for reuse`
      else if (sameControl) reuseHint = 'prior evidence for the same control'
      else if (score >= 0.6) reuseHint = 'near-identical content — check for a shared/common control'
      return {
        evidenceId: e.evidenceId,
        applicationSlug: e.applicationSlug,
        framework: e.framework,
        controlId: e.controlId,
        sha256: e.latest?.sha256 ?? null,
        score,
        crossApplication,
        sameControl,
        exactDuplicate,
        reuseHint,
      }
    })
    .filter((m) => m.score >= minScore)
    .sort((a, b) => b.score - a.score || a.evidenceId.localeCompare(b.evidenceId))
    .slice(0, Math.max(1, limit))
}

function round3(v: number): number {
  return Math.round(v * 1000) / 1000
}

export function mockReuseByEvidence(
  evidenceId: string,
  limit = 5,
  minScore = 0.3,
): ReuseResult {
  const target = mockEvidenceById(evidenceId)
  const querySha = target.latest?.sha256 ?? null
  const exactDuplicates: SimilarEvidence[] = mockEvidence
    .filter((e) => e.evidenceId !== evidenceId && !!querySha && (e.latest?.sha256 ?? null) === querySha)
    .map((e) => ({
      evidenceId: e.evidenceId,
      applicationSlug: e.applicationSlug,
      framework: e.framework,
      controlId: e.controlId,
      sha256: e.latest?.sha256 ?? null,
      score: 1,
      crossApplication: e.applicationSlug !== target.applicationSlug,
      sameControl: e.controlId === target.controlId,
      exactDuplicate: true,
      reuseHint: 'byte-identical evidence already held — reuse instead of re-collecting',
    }))
  return {
    queryEvidenceId: evidenceId,
    queryText: null,
    embeddingModel: 'mock-embed:v1(dim=256)',
    vectorStore: 'memory',
    indexed: mockEvidence.length,
    querySha256: querySha,
    exactDuplicates,
    matches: reuseMatches(
      tokens(target),
      limit,
      minScore,
      evidenceId,
      target.applicationSlug,
      target.controlId,
      querySha,
    ),
  }
}

export function mockReuseByText(text: string, limit = 5, minScore = 0.3): ReuseResult {
  const t = new Set((text.toLowerCase().match(/[a-z0-9]+/g) ?? []))
  return {
    queryEvidenceId: null,
    queryText: text,
    embeddingModel: 'mock-embed:v1(dim=256)',
    vectorStore: 'memory',
    indexed: mockEvidence.length,
    querySha256: null,
    exactDuplicates: [],
    matches: reuseMatches(t, limit, minScore, undefined, undefined, undefined, null),
  }
}

// ---- Reuse by control (cross-framework) ------------------------------

/** UC03 control -> frameworks catalogue — mirrors ControlFrameworkCatalog.all(). */
export function mockReuseControls(): ControlFrameworks[] {
  return Object.entries(CONTROL_FRAMEWORKS)
    .map(([controlId, frameworks]) => ({ controlId, frameworks: [...frameworks] }))
    .sort((a, b) => a.controlId.localeCompare(b.controlId))
}

export function mockReuseByControl(controlId: string): ControlReuseResult {
  const cid = (controlId ?? '').trim().toUpperCase()
  const frameworks = CONTROL_FRAMEWORKS[cid] ?? []
  const evidence: ControlReuseEvidence[] = mockEvidence
    .filter((e) => e.controlId.toUpperCase() === cid)
    .map((e) => ({
      evidenceId: e.evidenceId,
      applicationSlug: e.applicationSlug,
      controlId: e.controlId,
      sourceSystem: e.sourceSystem,
      collectionMethod: e.tags.collectionMethod ?? null,
      collectedAt: e.latest?.collectedAt ?? e.createdAt ?? null,
      sha256: e.latest?.sha256 ?? null,
      mappedFrameworks: (e.tags.frameworks ?? e.framework)
        .split(',')
        .map((f) => f.trim().toUpperCase())
        .filter(Boolean),
    }))
  return { controlId: cid, frameworks: [...frameworks], evidence }
}

/** Merge an extra framework into a record's `frameworks` tag (UC03 tagging path). */
export function mockAddEvidenceFramework(evidenceId: string, framework: string): EvidenceView {
  const ev = mockEvidence.find((e) => e.evidenceId === evidenceId)
  if (!ev) throw new Error(`Unknown evidence: ${evidenceId}`)
  const fw = framework.trim().toUpperCase()
  const allowed = CONTROL_FRAMEWORKS[ev.controlId.toUpperCase()]
  if (allowed && !allowed.map((f) => f.toUpperCase()).includes(fw)) {
    throw new Error(`${fw} is not a framework that control ${ev.controlId} maps to`)
  }
  const current = (ev.tags.frameworks ?? ev.framework).split(',').map((f) => f.trim().toUpperCase()).filter(Boolean)
  if (!current.includes(fw)) current.push(fw)
  ev.tags = { ...ev.tags, frameworks: current.join(','), framework: current[0] }
  ev.updatedAt = new Date().toISOString()
  return ev
}

export function mockSummary(evidenceId: string): EvidenceSummary {
  const ev = mockEvidenceById(evidenceId)
  const verdicts = resultsRaw.results.filter((r) => r.evidenceId === ev.evidenceId)
  const groundedOn = [
    `application: ${ev.applicationSlug}`,
    `framework: ${ev.framework}`,
    `control: ${ev.controlId}`,
    `source system: ${ev.sourceSystem}`,
    ...(ev.latest ? [`collected at: ${ev.latest.collectedAt}`, `current version: ${ev.currentVersion}`] : []),
    ...Object.entries(ev.tags).map(([k, v]) => `tag ${k}: ${v}`),
    ...verdicts.map((v) => `check ${v.checkId} -> ${v.status} (${v.detail})`),
  ]
  const summary =
    '[mock-ai] Summary based only on the supplied evidence:\n' +
    groundedOn.map((g) => `- ${g}`).join('\n')
  return {
    evidenceId: ev.evidenceId,
    applicationSlug: ev.applicationSlug,
    framework: ev.framework,
    controlId: ev.controlId,
    model: 'mock-chat:v1',
    simulated: true,
    modelGenerated: false,
    summary,
    groundedOn,
    generatedAt: NOW,
  }
}

interface RouteHit {
  matchedQuery: string
  interpretedAs: string
}

function routeNl(q: string): RouteHit {
  const s = q.toLowerCase()
  const has = (...n: string[]) => n.some((x) => s.includes(x))
  if (has('missing', 'gap', 'not covered', 'incomplete', 'completeness', 'coverage'))
    return { matchedQuery: 'completeness', interpretedAs: 'which expected controls lack current evidence' }
  if (has('compliance', 'compliant', 'posture', 'pass rate', 'how are we doing', 'audit ready'))
    return { matchedQuery: 'compliance', interpretedAs: 'overall compliance posture' }
  if (has('stale', 'out of date', 'outdated', 'expired', 'too old'))
    return { matchedQuery: 'stale-evidence', interpretedAs: 'evidence older than the freshness window' }
  if (has('fresh', 'how recent', 'how old', 'age of'))
    return { matchedQuery: 'freshness', interpretedAs: 'evidence freshness distribution' }
  if (has('duplicate', 'identical', 'same content'))
    return { matchedQuery: 'duplicates', interpretedAs: 'evidence content that is duplicated' }
  if (has('latest', 'current evidence', 'most recent'))
    return { matchedQuery: 'latest-per-control', interpretedAs: 'the current evidence per control' }
  if (
    has(
      'what evidence',
      'which evidence',
      'have evidence',
      'any evidence',
      'show me evidence',
      'find evidence',
      'list evidence',
      'is there evidence',
      'evidence for ',
      'evidence about',
      'evidence on ',
      'evidence do we',
      'evidence supporting',
      'evidence showing',
    )
  )
    return {
      matchedQuery: 'evidence-lookup',
      interpretedAs: 'evidence records matching the question (semantic retrieval)',
    }
  // Explicit, not a catch-all — mirrors NlQueryService.route() so an unrecognised
  // question reports itself as unsupported instead of silently becoming a source query.
  if (has('come from', 'comes from', 'came from', 'where does', 'where do', 'source', 'sources',
      'which system', 'what system', 'collected from', 'origin'))
    return { matchedQuery: 'source-breakdown', interpretedAs: 'which systems evidence comes from' }
  return { matchedQuery: 'unsupported', interpretedAs: 'no supported question type matched' }
}

/** Mirrors NlQueryService.SUPPORTED_QUESTION_TYPES. */
export const MOCK_SUPPORTED_QUESTION_TYPES = [
  'Missing / incomplete control coverage — e.g. "which controls are missing evidence?"',
  'Overall compliance posture — e.g. "what is our compliance posture?"',
  'Stale evidence — e.g. "what evidence is out of date?"',
  'Evidence freshness — e.g. "how recent is our evidence?"',
  'Duplicate evidence — e.g. "is any evidence duplicated?"',
  'Latest evidence per control — e.g. "what is the current evidence?"',
  'Evidence source breakdown — e.g. "where does our evidence come from?"',
  'Evidence lookup by topic — e.g. "what evidence do we have for SSH root login?"',
]

const MOCK_UNSUPPORTED_TEXT =
  "This question isn't supported. Question routing is a deterministic keyword " +
  'router, not a general-purpose model — it answers only the question types ' +
  'listed in supportedQuestionTypes.'

export function mockNlQuery(question: string, applicationSlug?: string): NlQueryResult {
  const hit = routeNl(question)
  let answer: Record<string, unknown>
  if (hit.matchedQuery === 'unsupported') {
    return {
      question,
      interpretedAs: hit.interpretedAs,
      matchedQuery: 'unsupported',
      answer: {
        supported: false,
        supportedQuestionTypes: MOCK_SUPPORTED_QUESTION_TYPES,
        answerText: MOCK_UNSUPPORTED_TEXT,
      },
      narrative: MOCK_UNSUPPORTED_TEXT,
      model: 'mock-chat:v1',
      simulated: true,
      modelGenerated: false,
      supported: false,
      supportedQuestionTypes: MOCK_SUPPORTED_QUESTION_TYPES,
      generatedAt: NOW,
    }
  }
  if (hit.matchedQuery === 'completeness') {
    const apps = appSlugs(applicationSlug)
    const rs = apps.map((a) => mockCompleteness(a))
    const expected = rs.reduce((n, r) => n + r.expected, 0)
    const covered = rs.reduce((n, r) => n + r.covered, 0)
    answer = {
      applications: apps,
      expectedControls: expected,
      withCurrentEvidence: covered,
      missingOrStale: expected - covered,
      completenessPct: expected === 0 ? 0 : round1((100 * covered) / expected),
      answerText: `${covered} of ${expected} expected controls have current evidence across ${apps.length} application(s).`,
    }
  } else if (hit.matchedQuery === 'compliance') {
    const apps = appSlugs(applicationSlug)
    const rs = apps.map((a) => mockCompliance(a))
    const expected = rs.reduce((n, r) => n + r.expected, 0)
    const compliant = rs.reduce((n, r) => n + r.compliant, 0)
    answer = {
      applications: apps,
      expectedControls: expected,
      compliantControls: compliant,
      compliancePct: expected === 0 ? 0 : round1((100 * compliant) / expected),
      answerText: `${compliant} of ${expected} expected controls are compliant across ${apps.length} application(s).`,
    }
  } else if (hit.matchedQuery === 'evidence-lookup') {
    const rr = mockReuseByText(question, 6, 0.1)
    const hits = rr.matches.filter((m) => !applicationSlug || m.applicationSlug === applicationSlug)
    answer = {
      vectorStore: rr.vectorStore,
      embeddingModel: rr.embeddingModel,
      indexed: rr.indexed,
      retrieved: hits.map((m) => ({
        evidenceId: m.evidenceId,
        applicationSlug: m.applicationSlug,
        framework: m.framework,
        controlId: m.controlId,
        score: m.score,
      })),
      evidenceCitations: hits.map((m) => m.evidenceId),
      grounded: hits.length > 0,
      answerText:
        hits.length === 0
          ? 'No evidence found in the ECS repository.'
          : `${hits.length} evidence record(s) match: ${[
              ...new Set(hits.map((m) => `${m.applicationSlug}/${m.controlId}`)),
            ].join(', ')}.`,
    }
  } else {
    const r = mockQuery(hit.matchedQuery, applicationSlug)
    answer = { answerText: r.answerText, counts: r.counts, rowCount: r.rows.length }
  }
  const narrative =
    '[mock-ai] ' +
    Object.entries(answer)
      .map(([k, v]) => `${k}: ${JSON.stringify(v)}`)
      .join('; ')
  return {
    question,
    interpretedAs: hit.interpretedAs,
    matchedQuery: hit.matchedQuery,
    answer: { ...answer, supported: true },
    narrative,
    model: 'mock-chat:v1',
    simulated: true,
    modelGenerated: false,
    supported: true,
    supportedQuestionTypes: MOCK_SUPPORTED_QUESTION_TYPES,
    generatedAt: NOW,
  }
}

const catalog = agentsRaw.collectorCatalog
export const mockAgents: AgentDescriptor[] = agentsRaw.agents.map((a) => {
  const collectors = catalog.filter((c) => a.collectors.includes(c.type))
  return {
    agentId: a.agentId,
    agentName: a.agentName,
    host: a.host,
    environment: a.environment,
    simulation: a.simulation,
    generatedAt: NOW,
    collectors,
    totalChecks: collectors.reduce((n, c) => n + c.checks.length, 0),
  }
})

// ---- Use Case 5 — ECS Admin -------------------------------------------

export const mockAdminRoles: AdminRole[] = [
  { id: 'ADMIN', description: 'Full administrative access — manage users, roles and applications' },
  { id: 'AUDITOR', description: 'Read-only access to evidence, controls and compliance posture' },
  { id: 'APP_OWNER', description: 'Owns one or more applications; uploads and attests evidence' },
  { id: 'COMPLIANCE_OFFICER', description: 'Reviews compliance posture and approves evidence' },
  { id: 'VIEWER', description: 'Read-only dashboards' },
]

export const mockAdminUsers: AdminUserView[] = [
  {
    username: 'admin',
    displayName: 'Platform Admin',
    email: 'admin@pramaan.local',
    roles: ['ADMIN'],
    active: true,
    createdAt: NOW,
    updatedAt: NOW,
  },
  {
    username: 'lead.auditor',
    displayName: 'Lead Auditor',
    email: 'auditor@pramaan.local',
    roles: ['AUDITOR', 'VIEWER'],
    active: true,
    createdAt: NOW,
    updatedAt: NOW,
  },
  {
    username: 'netbanking.owner',
    displayName: 'Net Banking Owner',
    email: 'owner.netbanking@pramaan.local',
    roles: ['APP_OWNER'],
    active: true,
    createdAt: NOW,
    updatedAt: NOW,
  },
]

// ---- outbound GRC sync (evidence + control-status summary) --------------
// Mirrors GrcSyncService: mock only, deterministic reference derived from the
// current portfolio snapshot rather than a real network call.

let grcSync: GrcSyncStatus | undefined

function shortDigest(input: string): string {
  let h = 0
  for (let i = 0; i < input.length; i++) {
    h = (Math.imul(31, h) + input.charCodeAt(i)) | 0
  }
  return (h >>> 0).toString(16).padStart(8, '0')
}

export function mockGrcStatus(): GrcSyncStatus {
  return (
    grcSync ?? {
      everSynced: false,
      lastSyncedAt: null,
      lastOutcome: null,
      externalReference: null,
      mock: true,
      endpoint: 'https://grc.example.com/api/ingest',
      evidenceRecords: 0,
      controlsEvaluated: 0,
      compliancePct: 0,
      detail: 'no sync has run yet',
    }
  )
}

export function mockGrcSync(): GrcSyncStatus {
  const evidence = mockEvidenceDashboard()
  const posture = mockLeadershipDashboard()
  const reference = `grc-${shortDigest(`${evidence.records}:${posture.expected}:${posture.compliancePct}:${NOW}`)}`
  grcSync = {
    everSynced: true,
    lastSyncedAt: NOW,
    lastOutcome: 'SUCCESS',
    externalReference: reference,
    mock: true,
    endpoint: 'https://grc.example.com/api/ingest',
    evidenceRecords: evidence.records,
    controlsEvaluated: posture.expected,
    compliancePct: posture.compliancePct,
    detail: `synced ${evidence.records} evidence record(s) and ${posture.expected} control result(s) (mock)`,
  }
  return grcSync
}

export function mockAdminUpsert(body: AdminUserUpsert): AdminUserView {
  return {
    username: body.username.trim().toLowerCase(),
    displayName: body.displayName.trim(),
    email: body.email && body.email.trim() ? body.email.trim() : null,
    roles: [...new Set(body.roles.map((r) => r.trim().toUpperCase()).filter(Boolean))],
    active: body.active ?? true,
    createdAt: NOW,
    updatedAt: NOW,
  }
}
