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
}

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

export interface EvidenceSummary {
  evidenceId: string
  applicationSlug: string
  framework: string
  controlId: string
  model: string
  simulated: boolean
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
