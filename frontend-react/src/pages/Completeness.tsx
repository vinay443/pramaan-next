import { Fragment, useMemo, useState } from 'react'
import {
  getEvidenceCompletenessControlEvidence,
  getEvidenceCompletenessControls,
  getEvidenceCompletenessFrameworks,
  listApplications,
} from '../api/endpoints'
import type { ControlCompletenessRow, EvidenceCompletenessItem, FrameworkCompletenessRow } from '../api/types'
import { useAsync } from '../hooks/useAsync'
import { DataTable, Empty, ErrorNote, Loading, Modal, Section, StatCard, StatusPill } from '../components/ui'

const ALL_APPLICATIONS = ''

type FrameworkStatus = 'EVALUATED' | 'PARTIAL' | 'NOT_EVALUATED'

function frameworkStatus(row: FrameworkCompletenessRow): FrameworkStatus {
  if (row.controlsEvaluated === 0) return 'NOT_EVALUATED'
  if (row.controlsEvaluated >= row.totalControls) return 'EVALUATED'
  return 'PARTIAL'
}

export function Completeness() {
  const apps = useAsync(() => listApplications(), [])
  const [slug, setSlug] = useState(ALL_APPLICATIONS)
  const [frameworkFilter, setFrameworkFilter] = useState('')
  const [expanded, setExpanded] = useState<string | null>(null)
  const [selectedControl, setSelectedControl] = useState<{ framework: string; control: ControlCompletenessRow } | null>(
    null,
  )
  const [selectedItem, setSelectedItem] = useState<EvidenceCompletenessItem | null>(null)

  const frameworksReq = useAsync(
    () => getEvidenceCompletenessFrameworks(slug || undefined),
    [slug],
  )

  const controlsReq = useAsync(
    () => (expanded ? getEvidenceCompletenessControls(expanded, slug || undefined) : Promise.resolve([])),
    [expanded, slug],
  )

  const evidenceReq = useAsync(
    () =>
      selectedControl
        ? getEvidenceCompletenessControlEvidence(selectedControl.framework, selectedControl.control.controlId, slug || undefined)
        : Promise.resolve([]),
    [selectedControl, slug],
  )

  const allFrameworks = frameworksReq.data ?? []
  const frameworks = useMemo(
    () =>
      frameworkFilter.trim()
        ? allFrameworks.filter((f) => f.framework.toLowerCase().includes(frameworkFilter.trim().toLowerCase()))
        : allFrameworks,
    [allFrameworks, frameworkFilter],
  )

  const totals = useMemo(() => {
    const totalControls = frameworks.reduce((s, f) => s + f.totalControls, 0)
    const evaluated = frameworks.reduce((s, f) => s + f.controlsEvaluated, 0)
    const notEvaluated = frameworks.reduce((s, f) => s + f.controlsNotEvaluated, 0)
    const weightedSum = frameworks.reduce((s, f) => s + (f.avgCompletenessPct ?? 0) * f.controlsEvaluated, 0)
    const avgCompletenessPct = evaluated === 0 ? null : Math.round((weightedSum / evaluated) * 10) / 10
    const evaluatedFrameworks = frameworks.filter((f) => frameworkStatus(f) === 'EVALUATED').length
    const partialFrameworks = frameworks.filter((f) => frameworkStatus(f) === 'PARTIAL').length
    const notEvaluatedFrameworks = frameworks.filter((f) => frameworkStatus(f) === 'NOT_EVALUATED').length
    return { totalControls, evaluated, notEvaluated, avgCompletenessPct, evaluatedFrameworks, partialFrameworks, notEvaluatedFrameworks }
  }, [frameworks])

  function toggleFramework(framework: string) {
    setExpanded((cur) => (cur === framework ? null : framework))
  }

  function updateSlug(value: string) {
    setSlug(value)
    setExpanded(null)
  }

  return (
    <div className="page">
      <h1>Evidence Completeness</h1>
      <p className="muted">
        Framework -&gt; control rollup of audit-readiness: for every expected control, is there current evidence,
        and how complete is it (required metadata, non-trivial content, hash integrity, freshness and control
        mapping)? Backed by <code>GET /api/v1/insight/evidence-completeness/frameworks</code> (deterministic).
      </p>

      <Section title="Scope">
        <div className="filter-row">
          <label>
            Application
            <select aria-label="Application" value={slug} onChange={(e) => updateSlug(e.target.value)}>
              <option value={ALL_APPLICATIONS}>(all applications)</option>
              {(apps.data ?? []).map((a) => (
                <option key={a.slug} value={a.slug}>
                  {a.name}
                </option>
              ))}
            </select>
          </label>
          <label>
            Framework
            <select
              aria-label="Framework"
              value={frameworkFilter}
              onChange={(e) => setFrameworkFilter(e.target.value)}
            >
              <option value="">(all)</option>
              {allFrameworks.map((f) => (
                <option key={f.framework} value={f.framework}>
                  {f.framework}
                </option>
              ))}
            </select>
          </label>
        </div>
      </Section>

      {frameworksReq.loading ? <Loading what="evidence completeness" /> : null}
      {frameworksReq.error ? <ErrorNote message={frameworksReq.error} /> : null}

      {frameworksReq.data ? (
        <>
          <div className="stat-grid">
            <StatCard label="Controls" value={totals.totalControls} hint="expected controls in scope" />
            <StatCard
              label="Avg completeness"
              value={totals.avgCompletenessPct === null ? '—' : `${totals.avgCompletenessPct}%`}
              hint="mean score across evaluated controls only"
            />
            <StatCard label="Evaluated" value={totals.evaluatedFrameworks} hint="frameworks, all controls evaluated" />
            <StatCard label="Partial" value={totals.partialFrameworks} hint="frameworks, some controls evaluated" />
            <StatCard label="Not evaluated" value={totals.notEvaluatedFrameworks} hint="frameworks, no evidence yet" />
          </div>

          <Section title={`Frameworks — ${frameworks.length}`}>
            {frameworks.length === 0 ? (
              <Empty message="No frameworks match this scope." />
            ) : (
              <div className="table-wrap">
                <table>
                  <thead>
                    <tr>
                      <th>Framework</th>
                      <th>Controls evaluated / total</th>
                      <th className="num">Avg completeness</th>
                      <th>Status</th>
                    </tr>
                  </thead>
                  <tbody>
                    {frameworks.map((f) => (
                      <Fragment key={f.framework}>
                        <tr
                          key={f.framework}
                          className="clickable"
                          onClick={() => toggleFramework(f.framework)}
                          aria-expanded={expanded === f.framework}
                        >
                          <td>{f.framework}</td>
                          <td>
                            {f.controlsEvaluated} / {f.totalControls}
                          </td>
                          <td className="num">{f.avgCompletenessPct === null ? '—' : `${f.avgCompletenessPct}%`}</td>
                          <td>
                            <StatusPill
                              status={
                                frameworkStatus(f) === 'EVALUATED'
                                  ? 'EVALUATED'
                                  : frameworkStatus(f) === 'PARTIAL'
                                    ? 'PARTIAL'
                                    : 'NOT_EVALUATED'
                              }
                            />
                          </td>
                        </tr>
                        {expanded === f.framework ? (
                          <tr key={`${f.framework}-detail`}>
                            <td colSpan={4} className="nested-cell">
                              {controlsReq.loading ? <Loading what="controls" /> : null}
                              {controlsReq.error ? <ErrorNote message={controlsReq.error} /> : null}
                              {controlsReq.data && controlsReq.data.length > 0 ? (
                                <DataTable
                                  rows={controlsReq.data}
                                  rowKey={(c) => c.controlId}
                                  onRowClick={(c) => setSelectedControl({ framework: f.framework, control: c })}
                                  columns={[
                                    { header: 'Control ID', cell: (c) => c.controlId },
                                    { header: 'Control name', cell: (c) => c.title },
                                    {
                                      header: 'Evaluated',
                                      cell: (c) => <StatusPill status={c.evaluated ? 'EVALUATED' : 'NOT_EVALUATED'} />,
                                    },
                                    {
                                      header: 'Completeness',
                                      align: 'right',
                                      cell: (c) => (c.completenessPct === null ? '—' : `${c.completenessPct}%`),
                                    },
                                    { header: 'Evidence count', cell: (c) => c.evidenceCount, align: 'right' },
                                  ]}
                                />
                              ) : null}
                              {controlsReq.data && controlsReq.data.length === 0 ? (
                                <Empty message="No controls in this framework's catalog." />
                              ) : null}
                            </td>
                          </tr>
                        ) : null}
                      </Fragment>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </Section>
        </>
      ) : null}

      {selectedControl ? (
        <Modal
          title={`${selectedControl.control.controlId} — ${selectedControl.control.title}`}
          onClose={() => {
            setSelectedControl(null)
            setSelectedItem(null)
          }}
          size="lg"
        >
          <div className="breakdown-head">
            <h3>
              Completeness —{' '}
              {selectedControl.control.completenessPct === null ? 'not evaluated' : `${selectedControl.control.completenessPct}%`}
            </h3>
            <StatusPill status={selectedControl.control.evaluated ? 'EVALUATED' : 'NOT_EVALUATED'} />
          </div>

          {evidenceReq.loading ? <Loading what="evidence" /> : null}
          {evidenceReq.error ? <ErrorNote message={evidenceReq.error} /> : null}
          {evidenceReq.data && evidenceReq.data.length === 0 ? (
            <Empty message="No evidence is mapped to this control." />
          ) : null}
          {evidenceReq.data && evidenceReq.data.length > 0 ? (
            <DataTable
              rows={evidenceReq.data}
              rowKey={(i) => i.evidenceId}
              onRowClick={setSelectedItem}
              columns={[
                { header: 'Application', cell: (i) => i.applicationSlug },
                { header: 'Source', cell: (i) => i.sourceSystem },
                { header: 'Technology', cell: (i) => i.technology ?? '—' },
                { header: 'Ver', cell: (i) => i.currentVersion, align: 'right' },
                {
                  header: 'Last collected',
                  cell: (i) => (i.lastCollectedAt ? new Date(i.lastCollectedAt).toLocaleDateString() : '—'),
                },
                { header: 'Integrity', cell: (i) => <StatusPill status={i.integrityStatus ?? 'UNKNOWN'} /> },
              ]}
            />
          ) : null}

          {selectedItem ? (
            <>
              <div className="breakdown-head">
                <h3>Evidence breakdown — {selectedItem.completenessPct}%</h3>
                <StatusPill status={selectedItem.band} />
              </div>
              <ul className="factor-list">
                {selectedItem.factors.map((f) => (
                  <li key={f.factor} className={`factor-row factor-${f.status}`}>
                    <span className="factor-icon" aria-hidden="true">
                      {f.status === 'ok' ? '✓' : '✕'}
                    </span>
                    <span className="factor-text">
                      <strong>{f.factor}:</strong> {f.detail}
                    </span>
                  </li>
                ))}
              </ul>
            </>
          ) : null}

          <div className="modal-foot">
            <button
              onClick={() => {
                setSelectedControl(null)
                setSelectedItem(null)
              }}
            >
              Close
            </button>
          </div>
        </Modal>
      ) : null}
    </div>
  )
}
