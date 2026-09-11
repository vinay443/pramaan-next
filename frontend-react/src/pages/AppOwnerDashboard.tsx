import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { getAppOwnerDashboard } from '../api/appOwnerEndpoints'
import { ApiError } from '../api/client'
import type { ActionRequiredItem, AppOwnerDashboard as Dashboard } from '../api/appOwnerTypes'

const ACTION_LABEL: Record<ActionRequiredItem['action'], string> = {
  UPLOAD_EVIDENCE: 'Upload Evidence',
  REVIEW_AND_RESUBMIT: 'Review & Resubmit',
  SHARE_UPDATE_TD: 'Share/Update TD',
  OPEN_ITEM: 'Open Item',
}

export function AppOwnerDashboard() {
  const [data, setData] = useState<Dashboard | null>(null)
  const [error, setError] = useState<string | null>(null)
  const navigate = useNavigate()

  useEffect(() => {
    getAppOwnerDashboard()
      .then(setData)
      .catch((e) => setError(e instanceof ApiError ? e.message : 'Failed to load dashboard'))
  }, [])

  function drillDown(filter: Record<string, string>) {
    const qs = new URLSearchParams(filter).toString()
    navigate(`/app-owner/compliance-pending${qs ? `?${qs}` : ''}`)
  }

  function openAction(item: ActionRequiredItem) {
    if (item.action === 'UPLOAD_EVIDENCE') {
      navigate(`/app-owner/compliance-pending?upload=1&applicationSlug=${item.applicationSlug}&controlId=${item.controlId}&framework=${item.framework}`)
      return
    }
    if (item.evidenceId) {
      navigate(`/app-owner/compliance-pending?focus=${item.evidenceId}&action=${item.action}`)
    }
  }

  if (error) {
    return (
      <div className="page">
        <h1>App Owner Dashboard</h1>
        <div className="state state-error">{error}</div>
      </div>
    )
  }
  if (!data) {
    return (
      <div className="page">
        <h1>App Owner Dashboard</h1>
        <div className="state"><span className="state-spinner" />Loading…</div>
      </div>
    )
  }

  return (
    <div className="page">
      <h1>App Owner Dashboard</h1>
      <p className="muted">
        Owned applications: {data.ownedApplications.length ? data.ownedApplications.join(', ') : 'none'}
      </p>

      <div className="stat-grid">
        <button type="button" className="card stat" onClick={() => drillDown({})}>
          <span className="stat-value">{data.compliancePct}%</span>
          <span className="stat-label">Compliance Status</span>
        </button>
        <button type="button" className="card stat" onClick={() => drillDown({})}>
          <span className="stat-value">{data.compliancePendingCount}</span>
          <span className="stat-label">Compliance Pending</span>
        </button>
        <button type="button" className="card stat" onClick={() => drillDown({ evidenceStatus: 'SUBMITTED' })}>
          <span className="stat-value">{data.evidencePending}</span>
          <span className="stat-label">Evidence Pending</span>
        </button>
        <button type="button" className="card stat" onClick={() => drillDown({ evidenceStatus: 'REJECTED' })}>
          <span className="stat-value">{data.evidenceRejected}</span>
          <span className="stat-label">Evidence Rejected</span>
        </button>
        <button type="button" className="card stat" onClick={() => drillDown({ slaStatus: 'NONE' })}>
          <span className="stat-value">{data.tdPending}</span>
          <span className="stat-label">TD Pending</span>
        </button>
        <button type="button" className="card stat" onClick={() => drillDown({ slaStatus: 'DUE_SOON' })}>
          <span className="stat-value">{data.dueSoon}</span>
          <span className="stat-label">Due Soon</span>
        </button>
        <button type="button" className="card stat" onClick={() => drillDown({ slaStatus: 'OVERDUE' })}>
          <span className="stat-value">{data.overdue}</span>
          <span className="stat-label">Overdue</span>
        </button>
      </div>

      <div className="card">
        <div className="section-head">
          <h2>Framework KPI</h2>
        </div>
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Framework</th><th className="num">Applicable</th><th className="num">Compliant</th>
                <th className="num">Pending</th><th className="num">Rejected</th><th className="num">%</th>
              </tr>
            </thead>
            <tbody>
              {data.frameworkKpis.map((k) => (
                <tr key={k.framework} className="clickable" onClick={() => drillDown({ framework: k.framework })}>
                  <td>{k.framework}</td>
                  <td className="num">{k.totalApplicable}</td>
                  <td className="num">{k.compliant}</td>
                  <td className="num">{k.pending}</td>
                  <td className="num">{k.rejected}</td>
                  <td className="num">{k.compliancePct}%</td>
                </tr>
              ))}
              {data.frameworkKpis.length === 0 ? (
                <tr><td colSpan={6} className="muted">No applicable frameworks.</td></tr>
              ) : null}
            </tbody>
          </table>
        </div>
      </div>

      <div className="card">
        <div className="section-head">
          <h2>Action Required</h2>
        </div>
        <div className="table-wrap">
          <table>
            <thead>
              <tr><th>Application</th><th>Framework / Control</th><th>Reason</th><th>Target Date</th><th>Action</th></tr>
            </thead>
            <tbody>
              {data.actionRequired.map((item, i) => (
                <tr key={`${item.evidenceId ?? 'none'}-${i}`}>
                  <td>{item.applicationSlug}</td>
                  <td>{item.framework} / {item.controlId}</td>
                  <td>{item.reason}</td>
                  <td>{item.targetDate ?? '—'}</td>
                  <td><button type="button" className="ghost" onClick={() => openAction(item)}>{ACTION_LABEL[item.action]}</button></td>
                </tr>
              ))}
              {data.actionRequired.length === 0 ? (
                <tr><td colSpan={5} className="muted">Nothing needs action right now.</td></tr>
              ) : null}
            </tbody>
          </table>
        </div>
      </div>

      <div className="card">
        <div className="section-head">
          <h2>Recent Activity</h2>
        </div>
        <div className="table-wrap">
          <table>
            <thead><tr><th>Application</th><th>Event</th><th>When</th></tr></thead>
            <tbody>
              {data.recentActivity.map((a, i) => (
                <tr key={`${a.evidenceId}-${i}`} className="clickable" onClick={() => navigate(`/app-owner/compliance-pending?focus=${a.evidenceId}`)}>
                  <td>{a.applicationSlug}</td>
                  <td>{a.message}</td>
                  <td>{new Date(a.occurredAt).toLocaleString()}</td>
                </tr>
              ))}
              {data.recentActivity.length === 0 ? (
                <tr><td colSpan={3} className="muted">No recent activity.</td></tr>
              ) : null}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  )
}
