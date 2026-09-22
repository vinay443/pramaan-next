// Temporary demo data for the App Owner dashboard (Overview/Controls/Evidence/Findings/
// Remediation tabs) — visual parity with the ECS POC screenshots pending a richer backend
// demo dataset; flip USE_MOCK_APP_OWNER_DATA to false to revert to the live API hooks.

import type { LifecycleStateCounts, RejectionAuditRow } from '../api/types'

/** Single switch controlling every mocked section below — set to false to go back to
 *  useAppOwnerData()'s live results everywhere it's read in Dashboard.tsx. */
export const USE_MOCK_APP_OWNER_DATA = true

// ---- Overview tab ---------------------------------------------------------

export const MOCK_APP_OWNER_COUNTS: LifecycleStateCounts = {
  draft: 78,
  submitted: 78,
  rejected: 0,
  approved: 83,
  expired: 0,
  superseded: 0,
}

export const MOCK_APP_OWNER_OVERVIEW = {
  closureRatePct: 51.6,
  avgReviewTimeDays: 3.2,
  rejectionTrendPct: -12,
  pendingAging: { count: 78, avgDaysInQueue: 8 },
  auditorSla: { pct: 94.5, targetDays: 5 },
  highlights: { pendingActions: 80, rejectedEvidence: 12, expiringStale: 36 },
}

// ---- Controls tab — "Pending Actions Work Queue" --------------------------

export interface MockWorkQueueRow {
  id: string
  framework: string
  application: string
  controlCode: string
  evidenceFile: string
  action: string
  priority: 'Critical' | 'High' | 'Medium' | 'Low'
  aging: string
  status: string
  due: string
  comments: string
  submitted: string
  expiry: string
}

export const MOCK_WORK_QUEUE_OPEN_COUNT = 80

const WORK_QUEUE_COMMENT = 'Supporting evidence — cross-check with SOC monitoring.'

export const MOCK_WORK_QUEUE: MockWorkQueueRow[] = [
  { id: 'dps-c11', framework: 'DPSC', application: 'Payments', controlCode: 'DPS-C11', evidenceFile: 'DPSC_MANUAL_OVERRIDE_AP_Q1.xlsx', action: 'Expiring evidence', priority: 'Critical', aging: '12d', status: 'Draft', due: '2026-11-30', comments: WORK_QUEUE_COMMENT, submitted: '—', expiry: '2026-11-30' },
  { id: 'osb-c11', framework: 'OS Baselining', application: 'Net Banking', controlCode: 'OSB-C11', evidenceFile: 'OSB_TERMINATED_USER_CL_Q1.xlsx', action: 'Expiring evidence', priority: 'Critical', aging: '12d', status: 'Draft', due: '2026-11-30', comments: WORK_QUEUE_COMMENT, submitted: '—', expiry: '2026-11-30' },
  { id: 'dbb-c11', framework: 'DB Baselining', application: 'Net Banking', controlCode: 'DBB-C11', evidenceFile: 'DBB_LEGAL_HOLD_PROC_Q1.xlsx', action: 'Expiring evidence', priority: 'Critical', aging: '12d', status: 'Draft', due: '2026-11-30', comments: WORK_QUEUE_COMMENT, submitted: '—', expiry: '2026-11-30' },
  { id: 'ngx-c11', framework: 'Nginx Baselining', application: 'UPI', controlCode: 'NGX-C11', evidenceFile: 'NGX_THIRD_PARTY_SCRIPT_Q1.xlsx', action: 'Expiring evidence', priority: 'Critical', aging: '12d', status: 'Draft', due: '2026-11-30', comments: WORK_QUEUE_COMMENT, submitted: '—', expiry: '2026-11-30' },
  { id: 'aps-c11', framework: 'AppSec', application: 'Loan System', controlCode: 'APS-C11', evidenceFile: 'APS_DEPRECATED_LIB_RM_Q1.xlsx', action: 'Expiring evidence', priority: 'Critical', aging: '12d', status: 'Draft', due: '2026-11-30', comments: WORK_QUEUE_COMMENT, submitted: '—', expiry: '2026-11-30' },
  { id: 'vap-c11', framework: 'VAPT', application: 'Mobile Banking', controlCode: 'VAP-C11', evidenceFile: 'VAP_DB_PATCH_APPLI_Q1.xlsx', action: 'Expiring evidence', priority: 'Critical', aging: '12d', status: 'Draft', due: '2026-11-30', comments: WORK_QUEUE_COMMENT, submitted: '—', expiry: '2026-11-30' },
  { id: 'csi-c11', framework: 'CSITE', application: 'Mobile Banking', controlCode: 'CSI-C11', evidenceFile: 'CSI_EXCEPTION_RISK_ACC_Q1.xlsx', action: 'Expiring evidence', priority: 'Critical', aging: '12d', status: 'Draft', due: '2026-11-30', comments: WORK_QUEUE_COMMENT, submitted: '—', expiry: '2026-11-30' },
  { id: 'itp-c11', framework: 'ITPP', application: 'Treasury', controlCode: 'ITP-C11', evidenceFile: 'ITP_POST_IMPL_REVIEW_Q1.xlsx', action: 'Expiring evidence', priority: 'Critical', aging: '12d', status: 'Draft', due: '2026-11-30', comments: WORK_QUEUE_COMMENT, submitted: '—', expiry: '2026-11-30' },
]

// ---- Evidence tab — "Evidence Rejections" ----------------------------------

const REJECTION_REASON =
  'Evidence package incomplete: reviewer requires updated production artefact and signed attestation.'
const REJECTED_BY = 'S. Nair (Auditor)'
const REJECTED_AT = '2026-05-20T14:00:00Z'
const WORKFLOW_STATE = 'Owner Review'

export const MOCK_REJECTIONS: RejectionAuditRow[] = [
  { evidenceId: 'mock-rej-01', applicationSlug: 'net-banking', framework: 'DPSC', controlId: 'Biometric Data Minimization', reason: REJECTION_REASON, rejectedBy: REJECTED_BY, rejectedAt: REJECTED_AT, workflowState: WORKFLOW_STATE },
  { evidenceId: 'mock-rej-02', applicationSlug: 'net-banking', framework: 'DB Baselining', controlId: 'MongoDB NoSQL Audit Trail', reason: REJECTION_REASON, rejectedBy: REJECTED_BY, rejectedAt: REJECTED_AT, workflowState: WORKFLOW_STATE },
  { evidenceId: 'mock-rej-03', applicationSlug: 'net-banking', framework: 'Nginx Baselining', controlId: 'HTTP/2 & HTTP/3 Security Review', reason: REJECTION_REASON, rejectedBy: REJECTED_BY, rejectedAt: REJECTED_AT, workflowState: WORKFLOW_STATE },
  { evidenceId: 'mock-rej-04', applicationSlug: 'payments', framework: 'AppSec', controlId: 'API OAuth Scope Validation', reason: REJECTION_REASON, rejectedBy: REJECTED_BY, rejectedAt: REJECTED_AT, workflowState: WORKFLOW_STATE },
  { evidenceId: 'mock-rej-05', applicationSlug: 'mobile-banking', framework: 'VAPT', controlId: 'Container Escape Testing', reason: REJECTION_REASON, rejectedBy: REJECTED_BY, rejectedAt: REJECTED_AT, workflowState: WORKFLOW_STATE },
  { evidenceId: 'mock-rej-06', applicationSlug: 'mobile-banking', framework: 'CSITE', controlId: 'Container Registry Access Review', reason: REJECTION_REASON, rejectedBy: REJECTED_BY, rejectedAt: REJECTED_AT, workflowState: WORKFLOW_STATE },
  { evidenceId: 'mock-rej-07', applicationSlug: 'net-banking', framework: 'ITPP', controlId: 'Post-Implementation Signoff', reason: REJECTION_REASON, rejectedBy: REJECTED_BY, rejectedAt: REJECTED_AT, workflowState: WORKFLOW_STATE },
  { evidenceId: 'mock-rej-08', applicationSlug: 'net-banking', framework: 'OS Baselining', controlId: 'Sudoers File Review', reason: REJECTION_REASON, rejectedBy: REJECTED_BY, rejectedAt: REJECTED_AT, workflowState: WORKFLOW_STATE },
  { evidenceId: 'mock-rej-09', applicationSlug: 'net-banking', framework: 'DPSC', controlId: 'Data Retention Schedule Proof', reason: REJECTION_REASON, rejectedBy: REJECTED_BY, rejectedAt: REJECTED_AT, workflowState: WORKFLOW_STATE },
  { evidenceId: 'mock-rej-10', applicationSlug: 'net-banking', framework: 'DB Baselining', controlId: 'Encryption at Rest Verification', reason: REJECTION_REASON, rejectedBy: REJECTED_BY, rejectedAt: REJECTED_AT, workflowState: WORKFLOW_STATE },
  { evidenceId: 'mock-rej-11', applicationSlug: 'net-banking', framework: 'Nginx Baselining', controlId: 'Rate Limiting Configuration', reason: REJECTION_REASON, rejectedBy: REJECTED_BY, rejectedAt: REJECTED_AT, workflowState: WORKFLOW_STATE },
  { evidenceId: 'mock-rej-12', applicationSlug: 'payments', framework: 'AppSec', controlId: 'Dependency Vulnerability Scan', reason: REJECTION_REASON, rejectedBy: REJECTED_BY, rejectedAt: REJECTED_AT, workflowState: WORKFLOW_STATE },
]

// ---- Findings tab — "Prioritized Actions" ----------------------------------

export interface MockFindingCard {
  id: string
  framework: string
  controlCode: string
  application: string
  description: string
  priority: 'Critical'
}

export const MOCK_FINDINGS: MockFindingCard[] = [
  { id: 'f-dps-c11', framework: 'DPSC', controlCode: 'DPS-C11', application: 'Payments', description: 'Manual override approval log', priority: 'Critical' },
  { id: 'f-osb-c11', framework: 'OS Baselining', controlCode: 'OSB-C11', application: 'Net Banking', description: 'Terminated user cleanup proof', priority: 'Critical' },
  { id: 'f-dbb-c11', framework: 'DB Baselining', controlCode: 'DBB-C11', application: 'Net Banking', description: 'Legal hold procedure attestation', priority: 'Critical' },
  { id: 'f-ngx-c11', framework: 'Nginx Baselining', controlCode: 'NGX-C11', application: 'UPI', description: 'Third-party script inventory', priority: 'Critical' },
  { id: 'f-aps-c11', framework: 'AppSec', controlCode: 'APS-C11', application: 'Loan System', description: 'Deprecated library removal proof', priority: 'Critical' },
  { id: 'f-vap-c11', framework: 'VAPT', controlCode: 'VAP-C11', application: 'Mobile Banking', description: 'DB patch application proof', priority: 'Critical' },
  { id: 'f-csi-c11', framework: 'CSITE', controlCode: 'CSI-C11', application: 'Mobile Banking', description: 'Exception risk acceptance form', priority: 'Critical' },
  { id: 'f-itp-c11', framework: 'ITPP', controlCode: 'ITP-C11', application: 'Treasury', description: 'Post-implementation review samples', priority: 'Critical' },
]

// ---- Remediation tab --------------------------------------------------------

export interface MockRemediationCard {
  id: string
  framework: string
  controlCode: string
  application: string
  /** Slug sent to Bulk Upload's ?applicationSlug= prefill — a free-text field there, so this
   *  doesn't need to resolve to a real application record for the deep link to work. */
  applicationSlugForDeepLink: string
}

export const MOCK_REMEDIATION: MockRemediationCard[] = [
  { id: 'r-dps-c19', framework: 'DPSC', controlCode: 'DPS-C19', application: 'UPI', applicationSlugForDeepLink: 'upi' },
  { id: 'r-dbb-c19', framework: 'DB Baselining', controlCode: 'DBB-C19', application: 'Mobile Banking', applicationSlugForDeepLink: 'mobile-banking' },
  { id: 'r-ngx-c19', framework: 'Nginx Baselining', controlCode: 'NGX-C19', application: 'Mobile Banking', applicationSlugForDeepLink: 'mobile-banking' },
  { id: 'r-aps-c19', framework: 'AppSec', controlCode: 'APS-C19', application: 'Payments', applicationSlugForDeepLink: 'payments' },
  { id: 'r-vap-c19', framework: 'VAPT', controlCode: 'VAP-C19', application: 'Mobile Banking', applicationSlugForDeepLink: 'mobile-banking' },
]
