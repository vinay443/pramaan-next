import { useState } from 'react'
import { getComparison } from '../api/endpoints'
import { useAsync } from '../hooks/useAsync'
import { DataTable, ErrorNote, Loading, Section, StatusPill } from '../components/ui'

export function Comparison() {
  const [framework, setFramework] = useState('')
  const report = useAsync(() => getComparison(undefined, framework || undefined), [framework])
  const d = report.data
  const apps = d?.applications ?? []

  return (
    <div className="page">
      <h1>Cross-application Compliance Comparison</h1>
      <p className="muted">
        Side-by-side compliance posture and control-gap list across every application. Re-aggregates the
        per-application results. Backed by <code>GET /api/v1/insight/comparison</code>.
      </p>

      <Section title="Scope">
        <div className="filter-row">
          <label>
            Framework
            <input aria-label="Framework" value={framework} placeholder="(all)" onChange={(e) => setFramework(e.target.value)} />
          </label>
        </div>
      </Section>

      {report.loading ? <Loading what="comparison" /> : null}
      {report.error ? <ErrorNote message={report.error} /> : null}

      {d ? (
        <>
          <Section title={`Compliance % by framework (${apps.join(' vs ')})`}>
            <DataTable
              rows={d.frameworks}
              rowKey={(f) => f.framework}
              columns={[
                { header: 'Framework', cell: (f) => f.framework },
                ...apps.map((a) => ({ header: a, cell: (f: (typeof d.frameworks)[number]) => `${f.compliancePctByApp[a] ?? 0}%` })),
                { header: 'Spread', cell: (f) => `${f.spreadPct}%` },
              ]}
            />
          </Section>

          <Section title={`Control status matrix — ${d.controls.length}`}>
            <DataTable
              rows={d.controls}
              rowKey={(c) => `${c.framework}|${c.controlId}`}
              columns={[
                { header: 'Framework', cell: (c) => c.framework },
                { header: 'Control', cell: (c) => c.controlId },
                ...apps.map((a) => ({
                  header: a,
                  cell: (c: (typeof d.controls)[number]) => <StatusPill status={c.statusByApp[a] ?? '—'} />,
                })),
                { header: 'Consistent', cell: (c) => (c.consistent ? 'yes' : 'no') },
              ]}
            />
          </Section>

          <Section title={`Gaps — ${d.gaps.length}`}>
            <DataTable
              rows={d.gaps}
              rowKey={(g) => `${g.applicationSlug}-${g.framework}-${g.controlId}`}
              columns={[
                { header: 'Application', cell: (g) => g.applicationSlug },
                { header: 'Framework', cell: (g) => g.framework },
                { header: 'Control', cell: (g) => g.controlId },
                { header: 'Status', cell: (g) => <StatusPill status={g.status} /> },
              ]}
            />
          </Section>
        </>
      ) : null}
    </div>
  )
}
