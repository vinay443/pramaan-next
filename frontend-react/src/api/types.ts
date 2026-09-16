// Wire types mirroring contracts/openapi/pramaan-backend.yaml.
// Keep field names identical to the contract.

export type Criticality = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'

export interface ApplicationUpsert {
  slug: string
  name: string
  businessUnit?: string
  criticality?: Criticality
  owner?: string
  technology?: string[]
}

export interface ApplicationView extends ApplicationUpsert {
  autoCreated: boolean
  /** Onboarded (true) vs deboarded (false). */
  active: boolean
  createdAt: string
  updatedAt: string
}

// ---- Use Case 5 — ECS Admin (users, roles, applications) ----------------

export interface AdminRole {
  id: string
  description: string
}

export interface AdminUserUpsert {
  username: string
  displayName: string
  email?: string
  roles: string[]
  active?: boolean
}

export interface AdminUserView {
  username: string
  displayName: string
  email?: string | null
  roles: string[]
  active: boolean
  createdAt: string
  updatedAt: string
}

export type IngestOutcome = 'CREATED' | 'NEW_VERSION' | 'DUPLICATE'

export interface IngestRequest {
  applicationSlug: string
  controlId: string
  framework: string
  sourceSystem: string
  sourceObjectId?: string
  title?: string
  contentType?: string
  contentBase64?: string
  contentText?: string
  collectedAt?: string
  collectedBy?: string
  metadata?: Record<string, string>
  tags?: Record<string, string>
}

export interface IngestResult {
  evidenceId: string
  evidenceKey: string
  applicationSlug: string
  controlId: string
  framework: string
  sourceSystem: string
  outcome: IngestOutcome | string
  version: number
  sha256: string
  sizeBytes: number
  /** Echoes the request's source object id — for a bulk file upload, the uploaded filename —
   *  so a DUPLICATE (or any other) outcome can be mapped back to the specific file. */
  sourceObjectId?: string | null
}

export interface BulkError {
  index: number
  message: string
}

export interface BulkIngestResponse {
  received: number
  created: number
  newVersions: number
  duplicates: number
  failed: number
  results: IngestResult[]
  errors: BulkError[]
}

export interface EvidenceVersionView {
  version: number
  sha256: string
  contentType: string
  sizeBytes: number
  objectKey: string
  collectedAt: string
  collectedBy?: string
  ingestionRunId?: string | null
  metadata: Record<string, string>
}

export type EvidenceLifecycleState =
  | 'DRAFT'
  | 'SUBMITTED'
  | 'APPROVED'
  | 'REJECTED'
  | 'EXPIRED'
  | 'SUPERSEDED'

export interface EvidenceView {
  evidenceId: string
  evidenceKey: string
  applicationSlug: string
  controlId: string
  framework: string
  sourceSystem: string
  sourceObjectId?: string | null
  title?: string | null
  currentVersion: number
  lifecycleState?: EvidenceLifecycleState | string
  createdAt: string
  updatedAt: string
  tags: Record<string, string>
  latest?: EvidenceVersionView | null
  integrityStatus?: IntegrityStatus
}

/** UC04 hash-integrity verdict for a record's current version (SHA-256 recomputed from the object store). */
export type IntegrityStatus = 'VERIFIED' | 'TAMPERED' | 'UNKNOWN'

export type LifecycleAction = 'SUBMIT' | 'APPROVE' | 'REJECT' | 'RETIRE' | 'RESET'

export interface LifecycleEventView {
  fromState?: string | null
  toState: string
  action: string
  actor?: string | null
  note?: string | null
  occurredAt: string
}

export interface EvidenceLifecycleView {
  evidenceId: string
  applicationSlug: string
  controlId: string
  state: string
  effectiveState: string
  expired: boolean
  reviewedBy?: string | null
  reviewedAt?: string | null
  note?: string | null
  retentionDays: number
  ageDays?: number | null
  expiresAt?: string | null
  currentVersion: number
  history: LifecycleEventView[]
}

// ---- outbound GRC sync (evidence + control-status summary) --------------

export type GrcSyncOutcome = 'SUCCESS' | 'FAILED'

export interface GrcSyncStatus {
  everSynced: boolean
  lastSyncedAt?: string | null
  lastOutcome?: GrcSyncOutcome | string | null
  externalReference?: string | null
  mock: boolean
  endpoint: string
  evidenceRecords: number
  controlsEvaluated: number
  compliancePct: number
  detail: string
}

export interface OnboardingPlanItem {
  slug: string
  name: string
  criticality: string
  frameworks: string[]
  sources: string[]
  unknownSources: string[]
  status: 'NEW' | 'EXISTS'
}

export interface OnboardingPlan {
  total: number
  toCreate: number
  existing: number
  items: OnboardingPlanItem[]
}

export interface OnboardingResult {
  applied: number
  created: number
  updated: number
  slugs: string[]
  collectionRunId?: string | null
}

// ---- Staged onboarding scan (rich intake form -> 5-phase async scan) ------
// Mirrors backend-java com.pramaan.backend.onboarding.OnboardingScanDtos.

export type OnboardingPhaseKey =
  | 'REGISTER_APPLICATION'
  | 'RESOLVE_FRAMEWORKS_CONTROLS'
  | 'VALIDATE_EVIDENCE_SOURCES'
  | 'TRIGGER_BASELINE_COLLECTION'
  | 'COMPUTE_INITIAL_POSTURE'

export type OnboardingScanStatus = 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED'

export interface OnboardingScanRequest {
  slug: string
  name: string
  businessUnit?: string
  criticality?: Criticality
  owner?: string
  technology?: string[]
  dbTechnology?: string[]
  middlewareTechnology?: string[]
  osTechnology?: string[]
  frameworks?: string[]
  sources?: string[]
  customerFacing?: boolean
  internetFacing?: boolean
  environment?: string
  hostingCloud?: string
  dataClassification?: string
  authType?: string
  drRequired?: boolean
  backupRequired?: boolean
  objectStorageLocation?: string
  cmdbIdentifier?: string
  requestedBy?: string
}

export interface OnboardingScanPhase {
  phase: OnboardingPhaseKey | string
  status: OnboardingScanStatus | string
  message?: string | null
}

export interface OnboardingScanView {
  scanId: string
  applicationSlug: string
  status: OnboardingScanStatus | string
  currentPhase?: string | null
  phases: OnboardingScanPhase[]
  schedulerRunId?: string | null
  completenessPct?: number | null
  compliancePct?: number | null
  message?: string | null
  createdAt: string
  startedAt?: string | null
  finishedAt?: string | null
}

// ---- UC14 cross-application comparison --------------------------------

export interface ComparisonReport {
  generatedAt: string
  framework?: string | null
  applications: string[]
  frameworks: Array<{
    framework: string
    compliancePctByApp: Record<string, number>
    minPct: number
    maxPct: number
    spreadPct: number
  }>
  controls: Array<{
    framework: string
    controlId: string
    statusByApp: Record<string, string>
    consistent: boolean
  }>
  gaps: Array<{ applicationSlug: string; framework: string; controlId: string; status: string }>
}

// ---- UC16 enterprise + UC20 national dashboards ----------------------

export interface GroupPosture {
  key: string
  applications: number
  expected: number
  compliant: number
  compliancePct: number
  completenessPct: number
}

export interface EnterpriseDashboard {
  generatedAt: string
  portfolio: LeadershipDashboard
  byBusinessUnit: GroupPosture[]
  byCriticality: GroupPosture[]
  topRisks: AppPosture[]
}

export interface RegionPosture {
  region: string
  applications: string[]
  expected: number
  compliant: number
  compliancePct: number
  completenessPct: number
  rag: 'GREEN' | 'AMBER' | 'RED'
}

export interface NationalDashboard {
  generatedAt: string
  nationalCompliancePct: number
  nationalCompletenessPct: number
  applications: number
  regions: RegionPosture[]
}

// ---- national rollup (region x framework breakdown + lagging-region ranking) ----

export interface RegionFrameworkRow {
  region: string
  framework: string
  expected: number
  compliant: number
  compliancePct: number
}

/** gapVsNationalPct is negative when the region trails the national average. */
export interface RegionGap {
  region: string
  compliancePct: number
  gapVsNationalPct: number
  rag: 'GREEN' | 'AMBER' | 'RED'
}

export interface NationalRollup {
  generatedAt: string
  nationalCompliancePct: number
  nationalCompletenessPct: number
  applications: number
  byRegionFramework: RegionFrameworkRow[]
  laggingRegions: RegionGap[]
}

// ---- UC18 AI-assisted audit preparation ------------------------------

export interface PrepFinding {
  category: string
  severity: 'HIGH' | 'MEDIUM' | 'LOW'
  applicationSlug: string
  framework: string
  controlId: string
  detail: string
  action: string
}

export interface AuditPrepReport {
  generatedAt: string
  scope: string
  readinessScore: number
  totalFindings: number
  bySeverity: Record<string, number>
  findings: PrepFinding[]
  narrative: string
  model: string
  simulated: boolean
}

// ---- UC19 compliance trend & closure --------------------------------

export interface TrendPoint {
  takenAt: string
  expected: number
  compliant: number
  compliancePct: number
  completenessPct: number
  approvedEvidence: number
  openFindings: number
  evidenceCount: number
  integrityChecked: number
  integrityIntact: number
}

export interface TrendReport {
  generatedAt: string
  current: TrendPoint
  points: TrendPoint[]
  closure: {
    approvals: number
    rejections: number
    resubmissions: number
    avgDaysToApprove?: number | null
    approvalsByWeek: Record<string, number>
  }
  collection: Array<{ at: string; ingested: number; duplicates: number; failed: number }>
}

// ---- Predefined technical query catalogue --------------------------

export interface PredefinedQueryItem {
  controlId: string
  technology: string
  controlName: string
  command: string
  frameworks: string[]
  evidenceType: string
  executableNow: boolean
  runtimeStatus: string
  controlFamily: string
}

export interface PredefinedQueryCatalog {
  total: number
  technologies: string[]
  frameworks: string[]
  controlFamilies: string[]
  items: PredefinedQueryItem[]
}

export interface PredefinedQueryRunResult {
  controlId: string
  technology: string
  applicationSlug: string
  mode: string
  outcome: string
  evidenceId?: string | null
  sha256?: string | null
  error?: string | null
  outputPreview?: string | null
}

export interface PredefinedQueryRunSummary {
  received: number
  ingested: number
  duplicates: number
  failed: number
  applicationSlug: string
  mode: string
  message: string
  results: PredefinedQueryRunResult[]
}

// ---- UC17 regulatory reporting -------------------------------------

export interface ReportInfo {
  name: string
  title: string
  formats: string[]
  params: string[]
}

export interface IntegrityReport {
  evidenceId: string
  version: number
  expectedSha256: string
  actualSha256?: string | null
  intact: boolean
  detail: string
}

// ---- UC17 regulator-ready filing (distinct from the generic reports) -------

export interface FrameworkFiling {
  framework: string
  regulator: string
  expected: number
  compliant: number
  nonCompliant: number
  missingEvidence: number
  compliancePct: number
}

export interface RegulatoryFiling {
  reportId: string
  title: string
  regulator: string
  scope: string
  framework: string
  periodStart: string
  periodEnd: string
  generatedAt: string
  preparedBy: string
  applicationsInScope: number
  controlsExpected: number
  controlsCompliant: number
  compliancePct: number
  evidenceRecords: number
  openGaps: number
  frameworks: FrameworkFiling[]
  attestation: string
}

export interface DeterministicQueryResult {
  name: string
  answerText: string
  generatedAt: string
  counts: Record<string, unknown>
  rows: Array<Record<string, unknown>>
}

// ---- Use Case 4 — consolidated evidence dashboard + hash integrity ------

export interface EvidenceDashboard {
  generatedAt: string
  records: number
  versions: number
  applications: number
  frameworks: number
  sources: number
  staleAfterDays: number
  freshness: { fresh: number; aging: number; stale: number; unknown: number }
  integrity: { checked: number; intact: number; mismatch: number; missingObject: number }
  duplicateHashes: number
  bySource: Array<Record<string, unknown>>
}

export interface Page<T> {
  items: T[]
  page: number
  size: number
  totalItems: number
  totalPages: number
}

export type RunTrigger = 'MANUAL' | 'SCHEDULED' | 'BULK'
export type RunStatus = 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED'

export interface RunRequest {
  applications?: string[]
  frameworks?: string[]
  sources?: string[]
  requestedBy?: string
}

export interface RunView {
  runId: string
  trigger: RunTrigger | string
  status: RunStatus | string
  requestedBy?: string
  applications: string[]
  frameworks: string[]
  sources: string[]
  received: number
  ingested: number
  duplicates: number
  failed: number
  message?: string
  perSource: Record<string, string>
  createdAt: string
  startedAt?: string | null
  finishedAt?: string | null
}

// GET /api/v1/agents (contract AgentDescriptorView) — also the shape of the
// `agent describe` output from agents-go. Served from mock data when offline.
export interface AgentCheckInfo {
  id: string
  controlId: string
  framework: string
  title: string
}
export interface AgentCollectorInfo {
  type: string
  technologies: string[]
  checks: AgentCheckInfo[]
}
export interface AgentDescriptor {
  agentId: string
  agentName: string
  host: string
  environment: string
  simulation: boolean
  generatedAt: string
  collectors: AgentCollectorInfo[]
  totalChecks: number
}

// contracts/openapi/pramaan-backend.yaml — CheckStatus / CheckDefView / CheckResultView.

export type CheckStatus = 'PASS' | 'WARNING' | 'FAIL' | 'NOT_APPLICABLE'

export interface CheckDefView {
  checkId: string
  collector: string
  technology: string
  controlId: string
  framework: string
  title: string
}

export interface CheckResultView {
  id: string
  evidenceId: string
  evidenceVersion: number
  applicationSlug: string
  checkId: string
  controlId: string
  framework: string
  sourceSystem: string
  status: CheckStatus | string
  observed: string
  expected: string
  detail: string
  collectedAt: string
}

export interface EvaluationSummary {
  evaluatedAt: string
  evidenceEvaluated: number
  resultsWritten: number
  byStatus: Record<string, number>
}

export interface CheckResultParams {
  applicationSlug?: string
  framework?: string
  controlId?: string
  sourceSystem?: string
  status?: string
  page?: number
  size?: number
}

export interface EvidenceQueryParams {
  applicationSlug?: string
  framework?: string
  controlId?: string
  sourceSystem?: string
  collectedAfter?: string
  collectedBefore?: string
  tag?: string
  technology?: string
  collectionMethod?: string
  page?: number
  size?: number
}

// ---- Phase 2 (insight) — contracts/openapi/pramaan-backend.yaml #/insight ----

export type Coverage = 'COVERED' | 'STALE' | 'MISSING'

export interface ControlCoverage {
  framework: string
  controlId: string
  title: string
  coverage: Coverage | string
  evidenceId?: string | null
  currentVersion?: number | null
  lastCollectedAt?: string | null
  ageDays?: number | null
}

export interface CompletenessReport {
  applicationSlug: string
  framework?: string | null
  generatedAt: string
  staleAfterDays: number
  expected: number
  covered: number
  stale: number
  missing: number
  completenessPct: number
  controls: ControlCoverage[]
}

// ---- per-evidence-item completeness (audit-readiness at the item level) ----

export interface EvidenceCompletenessFactor {
  factor: string
  status: 'ok' | 'fail' | string
  detail: string
}

export type CompletenessBand = 'COMPLETE' | 'PARTIAL' | 'INCOMPLETE'

export interface EvidenceCompletenessItem {
  evidenceId: string
  applicationSlug: string
  framework: string
  controlId: string
  sourceSystem: string
  collectedBy?: string | null
  technology?: string | null
  currentVersion: number
  lastCollectedAt?: string | null
  ageDays?: number | null
  sha256?: string | null
  integrityStatus?: IntegrityStatus
  completenessPct: number
  band: CompletenessBand | string
  factors: EvidenceCompletenessFactor[]
}

export interface EvidenceCompletenessReport {
  applicationSlug?: string | null
  framework?: string | null
  generatedAt: string
  staleAfterDays: number
  totalItems: number
  avgCompletenessPct: number
  completeCount: number
  partialCount: number
  incompleteCount: number
  items: EvidenceCompletenessItem[]
}

// ---- framework/control rollup (Evidence Completeness page, aggregated view) ----

export interface FrameworkCompletenessRow {
  framework: string
  totalControls: number
  controlsEvaluated: number
  controlsNotEvaluated: number
  /** Null when no control in this framework has any evidence yet. */
  avgCompletenessPct: number | null
}

export interface ControlCompletenessRow {
  controlId: string
  title: string
  evaluated: boolean
  /** Null (not 0%) when the control has no mapped evidence. */
  completenessPct: number | null
  evidenceCount: number
}

export interface SimilarEvidence {
  evidenceId: string
  applicationSlug: string
  framework: string
  controlId: string
  sha256?: string | null
  score: number
  crossApplication: boolean
  sameControl: boolean
  exactDuplicate: boolean
  reuseHint: string
}

export interface ReuseResult {
  queryEvidenceId?: string | null
  queryText?: string | null
  embeddingModel: string
  vectorStore: 'memory' | 'pgvector' | string
  indexed: number
  querySha256?: string | null
  exactDuplicates: SimilarEvidence[]
  matches: SimilarEvidence[]
}

// ---- reuse by control (cross-framework) ---------------------------

export interface ControlFrameworks {
  controlId: string
  frameworks: string[]
}

export interface ControlReuseEvidence {
  evidenceId: string
  applicationSlug: string
  controlId: string
  sourceSystem: string
  collectionMethod?: string | null
  collectedAt?: string | null
  sha256?: string | null
  mappedFrameworks: string[]
}

export interface ControlReuseResult {
  controlId: string
  frameworks: string[]
  evidence: ControlReuseEvidence[]
}

export interface EvidenceSummary {
  evidenceId: string
  applicationSlug: string
  framework: string
  controlId: string
  model: string
  simulated: boolean
  /** False when the text is a deterministic prompt digest rather than model output. */
  modelGenerated: boolean
  summary: string
  groundedOn: string[]
  generatedAt: string
}

export interface NlQueryResult {
  question: string
  interpretedAs: string
  matchedQuery: string
  answer: Record<string, unknown>
  narrative: string
  model: string
  simulated: boolean
  /** False when the narrative is a deterministic prompt digest, not model output. */
  modelGenerated: boolean
  /** False when the question matched no supported intent. */
  supported: boolean
  /** The question types the deterministic router can actually answer. */
  supportedQuestionTypes: string[]
  generatedAt: string
}

export type ControlStatus =
  | 'COMPLIANT'
  | 'PARTIALLY_COMPLIANT'
  | 'NON_COMPLIANT'
  | 'NOT_ASSESSED'
  | 'MISSING_EVIDENCE'

export interface ControlPosture {
  framework: string
  controlId: string
  status: ControlStatus | string
  detail: string
}

export interface FrameworkPosture {
  framework: string
  expected: number
  compliant: number
  partiallyCompliant: number
  nonCompliant: number
  notAssessed: number
  missingEvidence: number
  compliancePct: number
}

export interface ComplianceReport {
  applicationSlug: string
  generatedAt: string
  expected: number
  compliant: number
  compliancePct: number
  byFramework: FrameworkPosture[]
  controls: ControlPosture[]
}

// ---- Use Case 11 — leadership compliance dashboard (portfolio rollup) ----

export interface AppPosture {
  applicationSlug: string
  name: string
  criticality: string
  expected: number
  compliant: number
  compliancePct: number
  covered: number
  missing: number
  completenessPct: number
  nonCompliant: number
  missingEvidence: number
}

export interface LeadershipDashboard {
  generatedAt: string
  applications: number
  expected: number
  compliant: number
  compliancePct: number
  covered: number
  stale: number
  missing: number
  completenessPct: number
  checkVerdicts: Record<string, number>
  byApplication: AppPosture[]
  byFramework: FrameworkPosture[]
}
