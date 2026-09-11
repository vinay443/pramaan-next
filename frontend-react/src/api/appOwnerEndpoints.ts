// Typed calls for the App-Owner-scoped backend surface (/api/v1/app-owner/*).
// New, additive endpoints — reuses apiFetch/buildQuery from ./client, the same
// wire types as the rest of the app, but talks straight to the live backend (no
// mock fallback here; the pages call these while the App Owner UI is new).

import { apiFetch, buildQuery } from './client'
import { appOwnerHeaders } from './appOwnerSession'
import type { EvidenceLifecycleView, EvidenceView, ReportInfo } from './types'
import type {
  AppOwnerDashboard,
  CompliancePendingRow,
  NotificationView,
  TargetDateHistoryView,
  UploadEvidenceRequest,
} from './appOwnerTypes'

function get<T>(path: string): Promise<T> {
  return apiFetch<T>(path, { headers: appOwnerHeaders() })
}

function post<T>(path: string, body?: unknown): Promise<T> {
  return apiFetch<T>(path, { method: 'POST', body: body ?? {}, headers: appOwnerHeaders() })
}

export function getAppOwnerDashboard(): Promise<AppOwnerDashboard> {
  return get('/api/v1/app-owner/dashboard')
}

export interface CompliancePendingFilters {
  complianceStatus?: string
  evidenceStatus?: string
  slaStatus?: string
  framework?: string
  sortBy?: string
}

export function listCompliancePending(filters: CompliancePendingFilters = {}): Promise<CompliancePendingRow[]> {
  return get(`/api/v1/app-owner/compliance-pending${buildQuery(filters as Record<string, unknown>)}`)
}

export function getAppOwnerEvidence(id: string): Promise<EvidenceView> {
  return get(`/api/v1/app-owner/evidence/${encodeURIComponent(id)}`)
}

export function uploadAppOwnerEvidence(req: UploadEvidenceRequest): Promise<EvidenceView> {
  return post('/api/v1/app-owner/evidence/upload', req)
}

export function resubmitAppOwnerEvidence(id: string, comment?: string): Promise<EvidenceLifecycleView> {
  return post(`/api/v1/app-owner/evidence/${encodeURIComponent(id)}/resubmit`, { comment })
}

export function shareAppOwnerTargetDate(id: string, targetDate: string, comment?: string): Promise<EvidenceView> {
  return post(`/api/v1/app-owner/evidence/${encodeURIComponent(id)}/target-date`, { targetDate, comment })
}

export function getAppOwnerTargetDateHistory(id: string): Promise<TargetDateHistoryView[]> {
  return get(`/api/v1/app-owner/evidence/${encodeURIComponent(id)}/target-date/history`)
}

export function listAppOwnerNotifications(): Promise<NotificationView[]> {
  return get('/api/v1/app-owner/notifications')
}

export function listAppOwnerReports(): Promise<ReportInfo[]> {
  return get('/api/v1/app-owner/reports')
}

export function appOwnerReportUrl(name: string, format: 'json' | 'csv', applicationSlug?: string, framework?: string): string {
  // Not directly linkable (needs identity headers) — pages fetch + download via blob instead.
  return `/api/v1/app-owner/reports/${encodeURIComponent(name)}${buildQuery({ format, applicationSlug, framework })}`
}

export function getAppOwnerReportJson(name: string, applicationSlug?: string, framework?: string): Promise<unknown> {
  return get(`/api/v1/app-owner/reports/${encodeURIComponent(name)}${buildQuery({ format: 'json', applicationSlug, framework })}`)
}
