import { useEffect, useState } from 'react'
import { appOwnerHeaders } from '../api/appOwnerSession'
import { apiBaseUrl, ApiError } from '../api/client'
import { getAppOwnerDashboard, listAppOwnerReports } from '../api/appOwnerEndpoints'
import type { ReportInfo } from '../api/types'

/** Read-only, scoped to the App Owner's authorized applications — same restricted
 *  export formats (JSON / CSV) as the shared ReportController, via the App-Owner
 *  wrapper endpoints that enforce ownership server-side. */
export function AppOwnerReports() {
  const [reports, setReports] = useState<ReportInfo[] | null>(null)
  const [applications, setApplications] = useState<string[]>([])
  const [applicationSlug, setApplicationSlug] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [downloading, setDownloading] = useState<string | null>(null)

  useEffect(() => {
    listAppOwnerReports().then(setReports).catch((e) => setError(e instanceof ApiError ? e.message : 'Failed to load reports'))
    getAppOwnerDashboard().then((d) => {
      setApplications(d.ownedApplications)
      setApplicationSlug(d.ownedApplications[0] ?? '')
    }).catch(() => {})
  }, [])

  async function download(name: string, format: 'json' | 'csv') {
    setDownloading(`${name}-${format}`)
    setError(null)
    try {
      const qs = new URLSearchParams({ format, ...(applicationSlug ? { applicationSlug } : {}) })
      const res = await fetch(`${apiBaseUrl()}/api/v1/app-owner/reports/${encodeURIComponent(name)}?${qs}`, {
        headers: appOwnerHeaders(),
      })
      if (!res.ok) {
        const body = await res.text()
        throw new ApiError(`${res.status} ${res.statusText}: ${body}`, { status: res.status })
      }
      const blob = await res.blob()
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `${name}.${format}`
      a.click()
      URL.revokeObjectURL(url)
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Download failed')
    } finally {
      setDownloading(null)
    }
  }

  return (
    <div className="page">
      <h1>Reports</h1>
      <p className="muted">Read-only, scoped to your authorized applications.</p>

      <div className="card">
        <label>
          Application
          <select value={applicationSlug} onChange={(e) => setApplicationSlug(e.target.value)}>
            {applications.map((a) => <option key={a} value={a}>{a}</option>)}
          </select>
        </label>
      </div>

      {error ? <div className="state state-error">{error}</div> : null}
      {!error && !reports ? <div className="state"><span className="state-spinner" />Loading…</div> : null}

      {reports ? (
        <div className="table-wrap">
          <table>
            <thead><tr><th>Report</th><th>Formats</th><th>Download</th></tr></thead>
            <tbody>
              {reports.map((r) => (
                <tr key={r.name}>
                  <td>{r.title}</td>
                  <td className="small">{r.formats.join(', ')}</td>
                  <td>
                    <div className="row-actions" style={{ marginTop: 0 }}>
                      {r.formats.includes('json') ? (
                        <button type="button" className="ghost" disabled={downloading === `${r.name}-json`}
                          onClick={() => download(r.name, 'json')}>JSON</button>
                      ) : null}
                      {r.formats.includes('csv') ? (
                        <button type="button" className="ghost" disabled={downloading === `${r.name}-csv`}
                          onClick={() => download(r.name, 'csv')}>CSV</button>
                      ) : null}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : null}
    </div>
  )
}
