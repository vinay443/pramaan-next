import { useState } from 'react'
import { getAuditPrep, listApplications } from '../api/endpoints'
import { useAsync } from '../hooks/useAsync'
import { DataTable, ErrorNote, Loading, Section, StatCard, StatusPill } from '../components/ui'

export function AuditPrep() {
  const apps = useAsync(() => listApplications(), [])
  const [slug, setSlug] = useState('')
  const report = useAsync(() => getAuditPrep(slug || undefined), [slug])
  const d = report.data

  return (
    <div className="page">
      <h1>AI-assisted Audit Preparation</h1>
      <p className="muted">
        Deterministic checklist from completeness, compliance and evidence lifecycle; the model only
        narrates it. Backed by <code>GET /api/v1/insight/audit-prep</code>.
      </p>

      <Section title="Scope">
        <div className="filter-row">
          <label>
            Application
            <select aria-label="Application" value={slug} onChange={(e) => setSlug(e.target.value)}>
              <option value="">(whole portfolio)</option>
              {(apps.data ?? []).map((a) => (
                <option key={a.slug} value={a.slug}>
                  {a.name}
                </option>
              ))}
            </select>
          </label>
        </div>
      </Section>

      {report.loading ? <Loading what="audit-prep checklist" /> : null}
      {report.error ? <ErrorNote message={report.error} /> : null}

      {d ? (
        <>
          <div className="stat-grid">
            <StatCard label="Readiness score" value={`${d.readinessScore}%`} />
            <StatCard label="Findings" value={d.totalFindings} />
            <StatCard label="High" value={d.bySeverity.HIGH ?? 0} />
            <StatCard label="Medium" value={d.bySeverity.MEDIUM ?? 0} />
          </div>

          <Section title="Narrative">
            <p className="answer">{d.narrative}</p>
            <p className="muted small">
              model {d.model} {d.simulated ? <StatusPill status="simulated" /> : null}
            </p>
          </Section>

          <Section title={`Checklist — ${d.findings.length}`}>
            <DataTable
              rows={d.findings}
              rowKey={(f) => `${f.applicationSlug}-${f.controlId}-${f.category}`}
              columns={[
                { header: 'Severity', cell: (f) => <StatusPill status={f.severity} /> },
                { header: 'Category', cell: (f) => f.category },
                { header: 'Application', cell: (f) => f.applicationSlug },
                { header: 'Control', cell: (f) => `${f.framework}/${f.controlId}` },
                { header: 'Detail', cell: (f) => f.detail },
                { header: 'Action', cell: (f) => f.action },
              ]}
            />
          </Section>
        </>
      ) : null}
    </div>
  )
}
