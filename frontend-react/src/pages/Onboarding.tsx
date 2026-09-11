import { Fragment, useEffect, useState } from 'react'
import { getOnboardingPlan, getOnboardingScan, listReuseControls, onboardApplication, startOnboardingScan } from '../api/endpoints'
import type { ControlFrameworks, Criticality, OnboardingPhaseKey, OnboardingScanRequest, OnboardingScanView } from '../api/types'
import { useAsync } from '../hooks/useAsync'
import { DataTable, ErrorNote, Loading, Meter, Section, StatCard, StatusPill } from '../components/ui'

const TERMINAL = new Set(['COMPLETED', 'FAILED'])
const isTerminal = (s: string | undefined) => s !== undefined && TERMINAL.has(s)

const CRITICALITIES: Criticality[] = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL']
const ENVIRONMENTS = ['DEV', 'TEST', 'UAT', 'PROD']

const PHASE_LABELS: Record<OnboardingPhaseKey, string> = {
  REGISTER_APPLICATION: 'Register application',
  RESOLVE_FRAMEWORKS_CONTROLS: 'Resolve frameworks / controls',
  VALIDATE_EVIDENCE_SOURCES: 'Validate evidence sources',
  TRIGGER_BASELINE_COLLECTION: 'Trigger baseline collection',
  COMPUTE_INITIAL_POSTURE: 'Compute initial posture',
}
const PHASE_ORDER: OnboardingPhaseKey[] = [
  'REGISTER_APPLICATION',
  'RESOLVE_FRAMEWORKS_CONTROLS',
  'VALIDATE_EVIDENCE_SOURCES',
  'TRIGGER_BASELINE_COLLECTION',
  'COMPUTE_INITIAL_POSTURE',
]

// Sensible defaults so the only field a user ever has to type is Name (Slug
// auto-derives from it below) — everything else is pre-filled and editable.
const EMPTY_SCAN_FORM: OnboardingScanRequest = {
  slug: '',
  name: '',
  businessUnit: 'Retail Banking',
  criticality: 'MEDIUM',
  owner: 'compliance-team',
  technology: ['Java', 'React'],
  dbTechnology: ['PostgreSQL'],
  middlewareTechnology: ['Spring Boot'],
  osTechnology: ['Linux'],
  frameworks: ['PCI_DSS', 'DPSC', 'ISO27001'],
  sources: ['MOCK_JIRA', 'SHAREPOINT'],
  environment: 'PROD',
  hostingCloud: 'AWS',
  dataClassification: 'INTERNAL',
  authType: 'OAUTH2',
  objectStorageLocation: '',
  cmdbIdentifier: '',
  requestedBy: 'ui',
}

const csv = (v?: string[]) => (v ?? []).join(', ')
const parseCsv = (v: string) => v.split(',').map((s) => s.trim()).filter(Boolean)
const kebabCase = (v: string) =>
  v.trim().toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '')

// ---- 5-phase stepper (same visual language as Scheduler's RunPipeline) ----

type StepState = 'pending' | 'active' | 'done' | 'failed'
const MARKER: Record<StepState, string> = { done: '✓', failed: '✕', active: '', pending: '' }

function OnboardingStepper({ scan }: { scan: OnboardingScanView }) {
  const byPhase = new Map(scan.phases.map((p) => [p.phase, p]))
  return (
    <ol className="pipeline" aria-label="Onboarding scan phases">
      {PHASE_ORDER.map((key) => {
        const phase = byPhase.get(key)
        const state: StepState =
          phase?.status === 'COMPLETED' ? 'done' : phase?.status === 'FAILED' ? 'failed' : phase?.status === 'RUNNING' ? 'active' : 'pending'
        return (
          <li key={key} className={`pipeline-step is-${state}`} aria-label={`${PHASE_LABELS[key]}: ${state}`}>
            <span className="pipeline-marker" aria-hidden="true">
              {state === 'active' ? <span className="state-spinner" /> : MARKER[state]}
            </span>
            <span className="pipeline-label">{PHASE_LABELS[key]}</span>
            {phase?.message ? <span className="pipeline-detail">{phase.message}</span> : null}
          </li>
        )
      })}
    </ol>
  )
}

function elapsedLabel(startedAt: string, endedAt: number): string {
  const secs = Math.max(0, Math.round((endedAt - Date.parse(startedAt)) / 1000))
  const m = Math.floor(secs / 60)
  const s = secs % 60
  return m > 0 ? `${m}m ${s}s` : `${s}s`
}

// ---- staged progress modal -------------------------------------------

/** control -> frameworks catalogue (UC03), filtered down to the frameworks this
 *  scan is resolving. Same reuse-controls endpoint the Evidence Reuse page uses —
 *  the backend's RESOLVE_FRAMEWORKS_CONTROLS phase only reports a count, not the
 *  list, so this reconstructs the same mapping client-side from the catalogue. */
function useControlsByFramework(frameworks: string[]): { loading: boolean; byFramework: Map<string, string[]> } {
  const catalogue = useAsync<ControlFrameworks[]>(() => listReuseControls(), [])
  const wanted = new Set(frameworks.map((f) => f.toUpperCase()))
  const byFramework = new Map<string, string[]>()
  for (const c of catalogue.data ?? []) {
    for (const fw of c.frameworks) {
      if (!wanted.has(fw.toUpperCase())) continue
      const list = byFramework.get(fw) ?? []
      list.push(c.controlId)
      byFramework.set(fw, list)
    }
  }
  return { loading: catalogue.loading, byFramework }
}

function OnboardingScanModal({
  scanId,
  frameworks,
  onClose,
}: {
  scanId: string
  frameworks: string[]
  onClose: () => void
}) {
  const scan = useAsync<OnboardingScanView>(() => getOnboardingScan(scanId), [scanId], {
    pollWhile: (d) => !!d && !isTerminal(d.status),
  })
  const [now, setNow] = useState(() => Date.now())

  useEffect(() => {
    if (isTerminal(scan.data?.status)) return
    const id = setInterval(() => setNow(Date.now()), 1000)
    return () => clearInterval(id)
  }, [scan.data?.status])

  const completedPhases = (scan.data?.phases ?? []).filter((p) => p.status === 'COMPLETED').length
  const failed = scan.data?.status === 'FAILED'
  const done = scan.data?.status === 'COMPLETED'
  const endedAt = scan.data?.finishedAt ? Date.parse(scan.data.finishedAt) : now

  // Resolving frameworks/controls is live from the moment that phase starts —
  // not just once the whole scan completes.
  const resolvePhase = scan.data?.phases.find((p) => p.phase === 'RESOLVE_FRAMEWORKS_CONTROLS')
  const showControls = resolvePhase?.status === 'RUNNING' || resolvePhase?.status === 'COMPLETED'
  const controls = useControlsByFramework(showControls ? frameworks : [])

  return (
    <div className="modal-overlay" role="dialog" aria-modal="true" aria-label="Onboarding scan progress">
      <div className="modal">
        <div className="modal-head">
          <h2>Onboarding {scan.data?.applicationSlug ?? '…'}</h2>
          {scan.data ? <StatusPill status={scan.data.status} /> : null}
        </div>

        {scan.loading && !scan.data ? <Loading what="onboarding scan" /> : null}
        {scan.error ? <ErrorNote message={scan.error} /> : null}

        {scan.data ? (
          <>
            <Meter
              value={completedPhases}
              max={PHASE_ORDER.length}
              tone={failed ? 'bad' : done ? 'ok' : 'accent'}
              label={`${completedPhases} / ${PHASE_ORDER.length} phases complete · elapsed ${elapsedLabel(scan.data.createdAt, endedAt)}`}
            />
            <OnboardingStepper scan={scan.data} />

            {scan.data.message ? <p className="muted small">{scan.data.message}</p> : null}

            {showControls ? (
              <>
                <h3>Frameworks &amp; controls in scope</h3>
                {controls.loading ? <Loading what="control mappings" /> : null}
                {frameworks.length === 0 ? (
                  <p className="muted small">No frameworks were specified for this scan.</p>
                ) : (
                  <dl className="kv">
                    {frameworks.map((fw) => {
                      const ids = controls.byFramework.get(fw.toUpperCase()) ?? []
                      return (
                        <Fragment key={fw}>
                          <dt>{fw}</dt>
                          <dd>
                            {ids.length === 0 ? (
                              <span className="muted small">no mapped controls found</span>
                            ) : (
                              ids.map((id) => (
                                <span key={id} className="tag">
                                  {id}
                                </span>
                              ))
                            )}
                          </dd>
                        </Fragment>
                      )
                    })}
                  </dl>
                )}
              </>
            ) : null}

            {done ? (
              <div className="stat-grid">
                <StatCard label="Completeness" value={scan.data.completenessPct != null ? `${scan.data.completenessPct}%` : '—'} />
                <StatCard label="Compliance" value={scan.data.compliancePct != null ? `${scan.data.compliancePct}%` : '—'} />
                <StatCard label="Collection run" value={scan.data.schedulerRunId ?? '—'} />
              </div>
            ) : null}
          </>
        ) : null}

        <div className="modal-foot">
          {done ? (
            // Placeholder — the onboarding results dashboard isn't built yet.
            <button className="primary" onClick={onClose} title="Results dashboard not built yet">
              View Results
            </button>
          ) : null}
          {/* No dismiss option while the scan is in flight — it always tracks to
              completion in the foreground; only a terminal scan can be closed. */}
          {isTerminal(scan.data?.status) ? <button onClick={onClose}>Close</button> : null}
        </div>
      </div>
    </div>
  )
}

// ---- rich intake form --------------------------------------------------

function OnboardingIntakeForm({
  form,
  setForm,
  error,
  busy,
  onSubmit,
  onCancel,
}: {
  form: OnboardingScanRequest
  setForm: (f: OnboardingScanRequest) => void
  error: string | undefined
  busy: boolean
  onSubmit: () => void
  onCancel: () => void
}) {
  // Slug tracks Name (kebab-cased) until the user edits Slug directly.
  const [slugEdited, setSlugEdited] = useState(false)

  return (
    <>
      <div className="filter-row">
        <label>
          Slug
          <input
            aria-label="Slug"
            value={form.slug}
            placeholder="auto-generated from name"
            onChange={(e) => {
              setSlugEdited(true)
              setForm({ ...form, slug: e.target.value })
            }}
          />
        </label>
        <label>
          Name
          <input
            aria-label="Name"
            value={form.name}
            onChange={(e) => {
              const name = e.target.value
              setForm({ ...form, name, slug: slugEdited ? form.slug : kebabCase(name) })
            }}
          />
        </label>
        <label>
          Business unit
          <input
            aria-label="Business unit"
            value={form.businessUnit ?? ''}
            onChange={(e) => setForm({ ...form, businessUnit: e.target.value })}
          />
        </label>
        <label>
          Owner
          <input aria-label="Owner" value={form.owner ?? ''} onChange={(e) => setForm({ ...form, owner: e.target.value })} />
        </label>
        <label>
          Criticality
          <select
            aria-label="Criticality"
            value={form.criticality ?? 'MEDIUM'}
            onChange={(e) => setForm({ ...form, criticality: e.target.value as Criticality })}
          >
            {CRITICALITIES.map((c) => (
              <option key={c}>{c}</option>
            ))}
          </select>
        </label>
      </div>

      <div className="filter-row">
        <label>
          Technology (comma-separated)
          <input
            aria-label="Technology"
            value={csv(form.technology)}
            onChange={(e) => setForm({ ...form, technology: parseCsv(e.target.value) })}
          />
        </label>
        <label>
          DB technology
          <input
            aria-label="DB technology"
            value={csv(form.dbTechnology)}
            onChange={(e) => setForm({ ...form, dbTechnology: parseCsv(e.target.value) })}
          />
        </label>
        <label>
          Middleware technology
          <input
            aria-label="Middleware technology"
            value={csv(form.middlewareTechnology)}
            onChange={(e) => setForm({ ...form, middlewareTechnology: parseCsv(e.target.value) })}
          />
        </label>
        <label>
          OS technology
          <input
            aria-label="OS technology"
            value={csv(form.osTechnology)}
            onChange={(e) => setForm({ ...form, osTechnology: parseCsv(e.target.value) })}
          />
        </label>
      </div>

      <div className="filter-row">
        <label>
          Frameworks in scope (comma-separated)
          <input
            aria-label="Frameworks in scope"
            value={csv(form.frameworks)}
            placeholder="PCI_DSS, DPSC, …"
            onChange={(e) => setForm({ ...form, frameworks: parseCsv(e.target.value) })}
          />
        </label>
        <label>
          Sources to collect (comma-separated)
          <input
            aria-label="Sources to collect"
            value={csv(form.sources)}
            placeholder="MOCK_JIRA, SHAREPOINT, …"
            onChange={(e) => setForm({ ...form, sources: parseCsv(e.target.value) })}
          />
        </label>
        <label>
          Environment
          <select
            aria-label="Environment"
            value={form.environment ?? ''}
            onChange={(e) => setForm({ ...form, environment: e.target.value })}
          >
            <option value="">—</option>
            {ENVIRONMENTS.map((e) => (
              <option key={e}>{e}</option>
            ))}
          </select>
        </label>
        <label>
          Hosting / cloud
          <input
            aria-label="Hosting / cloud"
            value={form.hostingCloud ?? ''}
            placeholder="AWS, Azure, on-prem, …"
            onChange={(e) => setForm({ ...form, hostingCloud: e.target.value })}
          />
        </label>
      </div>

      <div className="filter-row">
        <label>
          Data classification
          <input
            aria-label="Data classification"
            value={form.dataClassification ?? ''}
            placeholder="PUBLIC, INTERNAL, CONFIDENTIAL, …"
            onChange={(e) => setForm({ ...form, dataClassification: e.target.value })}
          />
        </label>
        <label>
          Auth type
          <input
            aria-label="Auth type"
            value={form.authType ?? ''}
            placeholder="OAUTH2, SAML, LDAP, …"
            onChange={(e) => setForm({ ...form, authType: e.target.value })}
          />
        </label>
        <label>
          Object storage location
          <input
            aria-label="Object storage location"
            value={form.objectStorageLocation ?? ''}
            onChange={(e) => setForm({ ...form, objectStorageLocation: e.target.value })}
          />
        </label>
        <label>
          CMDB identifier
          <input
            aria-label="CMDB identifier"
            value={form.cmdbIdentifier ?? ''}
            onChange={(e) => setForm({ ...form, cmdbIdentifier: e.target.value })}
          />
        </label>
      </div>

      {error ? <ErrorNote message={error} /> : null}
      <div className="row-actions">
        <button className="primary" onClick={onSubmit} disabled={busy}>
          {busy ? 'Starting…' : 'Start onboarding scan'}
        </button>
        <button onClick={onCancel} disabled={busy}>
          Cancel
        </button>
      </div>
    </>
  )
}

export function Onboarding() {
  const plan = useAsync(() => getOnboardingPlan(), [])
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string>()

  // ---- rich intake form + staged scan modal ----
  const [showForm, setShowForm] = useState(false)
  const [form, setForm] = useState<OnboardingScanRequest>(EMPTY_SCAN_FORM)
  const [scanBusy, setScanBusy] = useState(false)
  const [scanError, setScanError] = useState<string>()
  const [activeScanId, setActiveScanId] = useState<string>()

  async function onboardOne(slug: string) {
    setBusy(true)
    setError(undefined)
    try {
      await onboardApplication(slug)
      plan.reload()
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e))
    } finally {
      setBusy(false)
    }
  }

  async function submitScan() {
    if (!form.slug.trim() || !form.name.trim()) {
      setScanError('slug and name are required')
      return
    }
    setScanBusy(true)
    setScanError(undefined)
    try {
      const started = await startOnboardingScan(form)
      setActiveScanId(started.scanId)
      setShowForm(false)
    } catch (e) {
      setScanError(e instanceof Error ? e.message : String(e))
    } finally {
      setScanBusy(false)
    }
  }

  function closeScanModal() {
    setActiveScanId(undefined)
    setForm(EMPTY_SCAN_FORM)
    plan.reload()
  }

  return (
    <div className="page">
      <h1>Multi-application Onboarding</h1>
      <p className="muted">
        The catalogue (identity, in-scope frameworks, evidence sources) comes from{' '}
        <code>phase2/onboarding.json</code>. Pick which applications to onboard — onboarding is a
        selective, idempotent upsert via the application store. Backed by <code>/api/v1/onboarding</code>.
      </p>

      <Section
        title="Onboard a new application"
        actions={
          !showForm ? (
            <button className="primary" onClick={() => setShowForm(true)}>
              + New application
            </button>
          ) : undefined
        }
      >
        {showForm ? (
          <OnboardingIntakeForm
            form={form}
            setForm={setForm}
            error={scanError}
            busy={scanBusy}
            onSubmit={submitScan}
            onCancel={() => {
              setShowForm(false)
              setScanError(undefined)
              setForm(EMPTY_SCAN_FORM)
            }}
          />
        ) : (
          <p className="muted">
            Register a new application with a full onboarding profile and run the 5-phase onboarding
            scan (register, resolve frameworks/controls, validate sources, trigger baseline
            collection, compute initial posture). Backed by <code>POST /api/v1/onboarding/scans</code>.
          </p>
        )}
      </Section>

      {activeScanId ? (
        <OnboardingScanModal scanId={activeScanId} frameworks={form.frameworks ?? []} onClose={closeScanModal} />
      ) : null}

      {plan.loading ? <Loading what="onboarding plan" /> : null}
      {plan.error ? <ErrorNote message={plan.error} /> : null}

      {plan.data ? (
        <>
          <div className="stat-grid">
            <StatCard label="Applications" value={plan.data.total} />
            <StatCard label="Not onboarded" value={plan.data.toCreate} />
            <StatCard label="Onboarded" value={plan.data.existing} />
          </div>

          {error ? <ErrorNote message={error} /> : null}

          <Section title="Catalogue">
            <DataTable
              rows={plan.data.items}
              rowKey={(i) => i.slug}
              columns={[
                { header: 'Application', cell: (i) => i.name },
                { header: 'Criticality', cell: (i) => <StatusPill status={i.criticality} /> },
                { header: 'Frameworks', cell: (i) => i.frameworks.join(', ') || '—' },
                { header: 'Sources', cell: (i) => i.sources.join(', ') || '—' },
                {
                  header: 'Status',
                  cell: (i) => (
                    <>
                      <StatusPill status={i.status === 'EXISTS' ? 'ONBOARDED' : 'NOT ONBOARDED'} />
                      {i.unknownSources.length > 0 ? (
                        <StatusPill status={`unknown: ${i.unknownSources.join(',')}`} />
                      ) : null}
                    </>
                  ),
                },
                {
                  header: '',
                  cell: (i) =>
                    i.status === 'EXISTS' ? null : (
                      <button
                        disabled={busy}
                        onClick={(e) => {
                          e.stopPropagation()
                          onboardOne(i.slug)
                        }}
                      >
                        Onboard
                      </button>
                    ),
                },
              ]}
            />
          </Section>
        </>
      ) : null}
    </div>
  )
}
