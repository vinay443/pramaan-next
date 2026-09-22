import { useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { getCompliance, listApplications } from '../api/endpoints'
import type { ControlPosture } from '../api/types'
import { useAsync } from '../hooks/useAsync'
import { DataTable, Empty, ErrorNote, Loading, Section, StatCard, StatusPill } from '../components/ui'

export function Compliance() {
  const apps = useAsync(() => listApplications(), [])
  // ?applicationSlug= seeds the picker so the Leadership drill-down lands on the
  // application that was clicked, not on whichever app happens to sort first.
  const [searchParams, setSearchParams] = useSearchParams()
  const fromUrl = searchParams.get('applicationSlug') ?? ''
  const [slug, setSlug] = useState('')
  const effectiveSlug = slug || fromUrl || apps.data?.[0]?.slug || ''

  function pick(next: string) {
    setSlug(next)
    // Keep the URL in step so the view stays shareable / reloadable.
    setSearchParams(next ? { applicationSlug: next } : {}, { replace: true })
  }

  return (
    <div className="page">
      <h1>Compliance Dashboard</h1>
      <p className="muted">
        Deterministic check verdicts rolled up against the expected-control catalog. Backed by{' '}
        <code>GET /api/v1/insight/compliance</code>.
      </p>

      <Section title="Application">
        <div className="filter-row">
          <label>
            Application
            <select aria-label="Application" value={effectiveSlug} onChange={(e) => pick(e.target.value)}>
              {(apps.data ?? []).map((a) => (
                <option key={a.slug} value={a.slug}>
                  {a.name}
                </option>
              ))}
            </select>
          </label>
        </div>
      </Section>

      <ComplianceView slug={effectiveSlug} />
    </div>
  )
}

/** Where a control row should send the user to fix it, or null when nothing is actionable. */
function controlAction(slug: string, c: ControlPosture): { to: string; label: string } | null {
  const qs = (extra: Record<string, string> = {}) =>
    new URLSearchParams({ applicationSlug: slug, framework: c.framework, ...extra }).toString()
  if (c.status === 'MISSING_EVIDENCE') {
    return { to: `/bulk-upload?${qs({ controlId: c.controlId })}`, label: 'Upload evidence' }
  }
  if (c.status === 'NON_COMPLIANT' || c.status === 'PARTIALLY_COMPLIANT') {
    return { to: `/evidence/query?${qs()}`, label: 'Review evidence' }
  }
  return null
}

/** Compliance report for one application. Shared by the /compliance page (picker-driven)
 *  and the App Owner dashboard tab (pre-scoped, `actionable` adds fix links per control). */
export function ComplianceView({ slug, actionable = false }: { slug: string; actionable?: boolean }) {
  const report = useAsync(
    () => (slug ? getCompliance(slug) : Promise.resolve(undefined)),
    [slug],
  )

  return (
    <>
      {report.loading ? <Loading what="compliance" /> : null}
      {report.error ? <ErrorNote message={report.error} /> : null}

      {report.data ? (
        <>
          <div className="stat-grid">
            <StatCard label="Expected controls" value={report.data.expected} />
            <StatCard label="Compliant" value={report.data.compliant} />
            <StatCard
              label="Compliance"
              value={`${report.data.compliancePct}%`}
              hint={`${report.data.compliant} compliant / ${report.data.expected} expected`}
            />
          </div>

          <Section title="By framework">
            {report.data.byFramework.length === 0 ? (
              <Empty message="No frameworks in scope." />
            ) : (
              <DataTable
                rows={report.data.byFramework}
                rowKey={(f) => f.framework}
                columns={[
                  { header: 'Framework', cell: (f) => f.framework },
                  { header: 'Expected', cell: (f) => f.expected },
                  { header: 'Compliant', cell: (f) => f.compliant },
                  { header: 'Partial', cell: (f) => f.partiallyCompliant },
                  { header: 'Non-compliant', cell: (f) => f.nonCompliant },
                  { header: 'Not assessed', cell: (f) => f.notAssessed },
                  { header: 'Missing evidence', cell: (f) => f.missingEvidence },
                  { header: '%', cell: (f) => `${f.compliancePct}%` },
                ]}
              />
            )}
          </Section>

          <Section title={`Controls — ${report.data.controls.length}`}>
            <DataTable
              rows={report.data.controls}
              rowKey={(c) => `${c.framework}|${c.controlId}`}
              columns={[
                { header: 'Status', cell: (c) => <StatusPill status={c.status} /> },
                { header: 'Framework', cell: (c) => c.framework },
                { header: 'Control', cell: (c) => c.controlId },
                { header: 'Detail', cell: (c) => c.detail },
                ...(actionable
                  ? [
                      {
                        header: 'Action',
                        cell: (c: ControlPosture) => {
                          const a = controlAction(slug, c)
                          return a ? <Link to={a.to}>{a.label} →</Link> : '—'
                        },
                      },
                    ]
                  : []),
              ]}
            />
          </Section>
        </>
      ) : null}
    </>
  )
}
