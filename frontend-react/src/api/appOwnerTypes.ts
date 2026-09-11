// Wire types for the App-Owner-scoped endpoints (/api/v1/app-owner/*).
// Mirrors backend com.pramaan.backend.appowner.AppOwnerDtos.

export type ActionType = 'UPLOAD_EVIDENCE' | 'REVIEW_AND_RESUBMIT' | 'SHARE_UPDATE_TD' | 'OPEN_ITEM'
export type SlaStatus = 'NONE' | 'ON_TRACK' | 'DUE_SOON' | 'OVERDUE'

export interface FrameworkKpi {
  framework: string
  totalApplicable: number
  compliant: number
  pending: number
  rejected: number
  compliancePct: number
}

export interface ActivityItem {
  type: string
  message: string
  evidenceId: string
  applicationSlug: string
  occurredAt: string
}

export interface ActionRequiredItem {
  evidenceId: string | null
  applicationSlug: string
  controlId: string
  framework: string
  reason: string
  action: ActionType
  targetDate: string | null
}

export interface AppOwnerDashboard {
  generatedAt: string
  ownedApplications: string[]
  compliancePendingCount: number
  compliancePct: number
  frameworkKpis: FrameworkKpi[]
  evidencePending: number
  evidenceRejected: number
  tdPending: number
  dueSoon: number
  overdue: number
  recentActivity: ActivityItem[]
  actionRequired: ActionRequiredItem[]
}

export interface CompliancePendingRow {
  evidenceId: string | null
  framework: string
  controlId: string
  applicationSlug: string
  complianceStatus: string
  evidenceStatus: string | null
  targetDate: string | null
  slaStatus: SlaStatus
  auditorComment: string | null
  lastUpdated: string | null
  allowedActions: ActionType[]
}

export interface TargetDateHistoryView {
  oldTargetDate: string | null
  newTargetDate: string
  reason: string | null
  actorUsername: string
  createdAt: string
}

export interface NotificationView {
  id: string | null
  type: string
  message: string
  evidenceId: string | null
  applicationSlug: string | null
  createdAt: string
  read: boolean
}

export interface UploadEvidenceRequest {
  applicationSlug: string
  controlId: string
  framework: string
  sourceSystem?: string
  title?: string
  contentType?: string
  contentBase64?: string
  contentText?: string
  description?: string
}
