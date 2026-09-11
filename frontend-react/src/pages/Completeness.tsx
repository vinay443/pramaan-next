import { useState } from 'react'
import { Link } from 'react-router-dom'
import { getCompleteness, listApplications } from '../api/endpoints'
import { useAsync } from '../hooks/useAsync'
import { DataTable, Empty, ErrorNote, Loading, Section, StatCard, StatusPill } from '../components/ui'

export function Completeness() {
  const apps = useAsync(() => listApplications(), [])
  const [slug, setSlug] = useState('')
  const [framework, setFramework] = useState('')

  const effectiveSlug = slug || apps.data?.[0]?.slug || ''
  const report = useAsync(
    () => (effectiveSlug ? getCompleteness(effectiveSlug, framework || undefined) : Promise.resolve(undefined)),
    [effectiveSlug, framework],
  )

  return (
    <div className="page">
      <h1>Evidence Completeness</h1>
      <p className="muted">
        Expected controls with current evidence, per application. Backed by{' '}
        <code>GET /api/v1/insight/completeness</code> (deterministic).
      </p>

      <Section title="Scope">
        <div className="filter-row">
          <label>
            Application
            <select aria-label="Application" value={effectiveSlug} onChange={(e) => setSlug(e.target.value)}>
              {(apps.data ?? []).map((a) => (
                <option key={a.slug} value={a.slug}>
                  {a.name}
                </option>
              ))}
            </select>
          </label>
          <label>
            Framework
            <input
              aria-label="Framework"
              value={framework}
              onChange={(e) => setFramework(e.target.value)}
              placeholder="(all)"
            />
          </label>
        </div>
      </Section>

      {report.loading ? <Loading what="completeness" /> : null}
      {report.error ? <ErrorNote message={report.error} /> : null}

      {report.data ? (
        <>
          <div className="stat-grid">
            <StatCard label="Expected" value={report.data.expected} />
            <StatCard label="Covered" value={report.data.covered} />
            <StatCard label="Stale" value={report.data.stale} hint={`> ${report.data.staleAfterDays}d`} />
            <StatCard label="Missing" value={report.data.missing} />
            <StatCard
              label="Completeness"
              value={`${report.data.completenessPct}%`}
              hint={`${report.data.covered} covered / ${report.data.expected} expected`}
            />
          </div>

          <Section title={`Controls — ${report.data.controls.length}`}>
            {report.data.controls.length === 0 ? (
              <Empty message="No expected controls for this scope." />
            ) : (
              <DataTable
                rows={report.data.controls}
                rowKey={(c) => `${c.framework}|${c.controlId}`}
                columns={[
                  { header: 'Coverage', cell: (c) => <StatusPill status={c.coverage} /> },
                  { header: 'Framework', cell: (c) => c.framework },
                  { header: 'Control', cell: (c) => c.controlId },
                  { header: 'Title', cell: (c) => c.title },
                  {
                    // The evidence record this row's COVERED/STALE verdict was computed from.
                    // MISSING rows genuinely have none, so they show an em dash.
                    header: 'Evidence',
                    cell: (c) =>
                      c.evidenceId ? (
                        <Link to={`/evidence/${c.evidenceId}`}>{c.evidenceId.slice(0, 8)}</Link>
                      ) : (
                        '—'
                      ),
                  },
                  { header: 'Ver', cell: (c) => c.currentVersion ?? '—', align: 'right' },
                  { header: 'Age (d)', cell: (c) => c.ageDays ?? '—', align: 'right' },
                  {
                    header: 'Last collected',
                    cell: (c) => (c.lastCollectedAt ? new Date(c.lastCollectedAt).toLocaleDateString() : '—'),
                  },
                ]}
              />
            )}
          </Section>
        </>
      ) : null}
    </div>
  )
}
