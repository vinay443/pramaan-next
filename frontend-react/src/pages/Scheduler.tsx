import { useEffect, useRef, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import {
  actingUser,
  createSchedule,
  getNotificationSettings,
  getRun,
  listSecurityEvents,
  getSystemStatus,
  listApplications,
  listRuns,
  listScheduleOptions,
  listSourceConnections,
  listSchedules,
  retryRun,
  saveNotificationSettings,
  runScheduleNow,
  setScheduleActive,
  setSourceConnection,
  startRun,
} from '../api/endpoints'
import { ApiError } from '../api/client'
import { emitDataEvent } from '../api/events'
import { SCHEDULER_MANAGE_ROLES, currentPersona, type PersonaCode } from './PersonaLogin'
import type { NotificationSettings, RunView, ScheduleView } from '../api/types'
import { useAsync } from '../hooks/useAsync'
import { DataTable, Empty, ErrorNote, Loading, Section, StatCard, StatusPill } from '../components/ui'

const RUNS_PAGE_SIZE = 25
const TERMINAL = new Set(['COMPLETED', 'PARTIAL', 'FAILED'])
const isTerminal = (s: string | undefined) => s !== undefined && TERMINAL.has(s)
const isActive = (s: string) => s === 'RUNNING' || s === 'PENDING'

// ---- Collection pipeline -------------------------------------------------
// The stages a scheduler run actually goes through in this codebase, in order.
// Traced from SchedulerRunExecutor.execute() + EvidenceIngestionService.ingest():
//   markRunning -> resolve active applications -> for each source: resolve the
//   integration + integration.collect() -> per collected item: ingest (SHA-256
//   hash, resolve control->framework mapping, dedup vs latest version, write to
//   object store + new version + tags + rule re-evaluation) -> run.complete().
// The backend only persists PENDING/RUNNING/COMPLETED/FAILED plus the final
// tallies (received/ingested/duplicates/failed) — there is no per-stage state —
// so stage status here is derived from the run's status + those counts.

type StageState = 'pending' | 'active' | 'done' | 'failed'

interface Stage {
  key: string
  label: string
  /** Count/hint pulled from an existing run field, or null when the backend tracks none. */
  detail?: (r: RunView) => string | null
}

const PIPELINE: Stage[] = [
  { key: 'queued', label: 'Queued' },
  {
    key: 'apps',
    label: 'Applications resolved',
    detail: (r) => (r.applications.length ? `${r.applications.length} named` : 'all onboarded'),
  },
  {
    key: 'sources',
    label: 'Sources polled',
    detail: (r) => `${r.sources.length} source${r.sources.length === 1 ? '' : 's'}`,
  },
  { key: 'received', label: 'Evidence received', detail: (r) => `${r.received} recv` },
  // No backing count — the control->framework mapping is resolved per item inside
  // ingest() and never tallied on the run.
  { key: 'mapping', label: 'Control mappings resolved' },
  { key: 'dedup', label: 'Dedup / reuse check', detail: (r) => `${r.duplicates} dup` },
  { key: 'stored', label: 'Evidence stored', detail: (r) => `${r.ingested} ingested` },
  { key: 'complete', label: 'Complete', detail: (r) => (r.failed ? `${r.failed} failed` : null) },
]

function stageStates(r: RunView): Record<string, StageState> {
  const fill = (v: StageState) =>
    Object.fromEntries(PIPELINE.map((s) => [s.key, v])) as Record<string, StageState>

  if (r.status === 'PENDING') return { ...fill('pending'), queued: 'active' }
  if (r.status === 'RUNNING') return { ...fill('active'), queued: 'done', complete: 'pending' }
  if (r.status === 'COMPLETED' || r.status === 'PARTIAL') return fill('done')

  // FAILED — mark the stage the failure most likely landed on. received===0 means
  // no source produced anything (integration resolution / collect failed);
  // otherwise ingestion/storage failed.
  const failedAt = r.received === 0 ? 'sources' : 'stored'
  const out: Record<string, StageState> = {}
  let done = true
  for (const s of PIPELINE) {
    if (s.key === 'complete') {
      out[s.key] = 'failed'
    } else if (!done) {
      out[s.key] = 'pending'
    } else if (s.key === failedAt) {
      out[s.key] = 'failed'
      done = false
    } else {
      out[s.key] = 'done'
    }
  }
  return out
}

const MARKER: Record<StageState, string> = { done: '✓', failed: '✕', active: '', pending: '' }

export function RunPipeline({ run }: { run: RunView }) {
  const states = stageStates(run)
  return (
    <ol className="pipeline" aria-label="Collection pipeline">
      {PIPELINE.map((stage) => {
        const state = states[stage.key]
        const detail = stage.detail?.(run) ?? null
        return (
          <li
            key={stage.key}
            className={`pipeline-step is-${state}`}
            aria-label={`${stage.label}: ${state}`}
          >
            <span className="pipeline-marker" aria-hidden="true">
              {state === 'active' ? <span className="state-spinner" /> : MARKER[state]}
            </span>
            <span className="pipeline-label">{stage.label}</span>
            {detail ? <span className="pipeline-detail">{detail}</span> : null}
          </li>
        )
      })}
    </ol>
  )
}

const fmtTime = (iso?: string | null) => (iso ? new Date(iso).toLocaleString() : '—')

const fmtDuration = (ms?: number | null) =>
  ms === undefined || ms === null ? '—' : ms < 1000 ? `${ms} ms` : `${(ms / 1000).toFixed(1)} s`

const scheduleLabel = (r: RunView) =>
  r.scheduleName ? `${r.scheduleName}${r.scheduleFrequency ? ` (${r.scheduleFrequency})` : ''}` : '—'

/** Short failure summary for FAILED / PARTIAL rows: first failed source + its error code. */
const errorSummary = (r: RunView) => {
  const first = Object.entries(r.sourceErrors ?? {})[0]
  if (!first) return r.status === 'FAILED' || r.status === 'PARTIAL' ? (r.message ?? '—') : '—'
  const more = Object.keys(r.sourceErrors ?? {}).length - 1
  return `${first[0]}: ${first[1].errorCode}${more > 0 ? ` (+${more} more)` : ''}`
}

/** Green "Within SLA <n>s" / red "SLA breached" once a run has finished. */
function SlaBadge({ run }: { run: RunView }) {
  if (run.slaStatus === 'WITHIN_SLA') return <StatusPill status={`Within SLA ${run.slaSeconds}s`} tone="ok" />
  if (run.slaStatus === 'BREACHED') return <StatusPill status="SLA breached" tone="bad" />
  return <span className="muted">{isActive(run.status) ? 'In progress' : '—'}</span>
}

/** Roll-up of the audit trails on this page: total, how many met the SLA, slowest and average duration. */
function RunSummary({ runs, page, size, totalItems }: { runs: RunView[]; page: number; size: number; totalItems: number }) {
  const finished = runs.filter((r) => r.slaStatus === 'WITHIN_SLA' || r.slaStatus === 'BREACHED')
  const within = finished.filter((r) => r.slaStatus === 'WITHIN_SLA').length
  const durations = runs.map((r) => r.durationMs).filter((d): d is number => typeof d === 'number')
  const slowest = durations.length ? Math.max(...durations) : null
  const average = durations.length ? Math.round(durations.reduce((a, b) => a + b, 0) / durations.length) : null
  return (
    <p className="muted" aria-label="Audit trail summary">
      Showing {page * size + 1}–{page * size + runs.length} of {totalItems} runs · on this page: {within} of{' '}
      {finished.length} finished within SLA · slowest {fmtDuration(slowest)} · average {fmtDuration(average)}
    </p>
  )
}

/** Audit block for one run: who, what schedule, when, outcome. */
function RunAudit({ run }: { run: RunView }) {
  const rows: Array<[string, React.ReactNode]> = [
    ['Run id', run.runId],
    ['Triggered by', run.triggeredBy ?? '—'],
    ['Trigger', run.trigger],
    ['Schedule', run.scheduleName ?? '—'],
    ['Frequency', run.scheduleFrequency ?? '—'],
    ['Schedule created by', run.scheduleCreatedBy ?? '—'],
    ['Started', fmtTime(run.startedAt)],
    ['Finished', fmtTime(run.finishedAt)],
    ['Duration', fmtDuration(run.durationMs)],
    ['Queue wait', fmtDuration(run.queuedMs)],
    ['SLA', <SlaBadge key="sla" run={run} />],
    ['Status', <StatusPill key="s" status={run.status} />],
    ['Retry of', run.retryOfRunId ?? '—'],
  ]
  return (
    <dl className="audit-grid" aria-label="Run audit">
      {rows.map(([k, v]) => (
        <div key={k}>
          <dt>{k}</dt>
          <dd>{v}</dd>
        </div>
      ))}
    </dl>
  )
}

const OPTION_LABELS: Record<string, string> = { MINUTELY: 'Every minute (testing)' }

const EMPTY_FORM = { name: '', sourceSystem: '', application: '', evidenceType: '', frequency: '' }
type ScheduleForm = typeof EMPTY_FORM

const FIELD_LABELS: Record<keyof ScheduleForm, string> = {
  name: 'Name',
  sourceSystem: 'Source',
  application: 'Application',
  evidenceType: 'Evidence type',
  frequency: 'Frequency',
}

/** Collection schedules: create form + table. Runs go through the existing run API (trigger=SCHEDULED). */
function Schedules({
  onRunStarted,
  onOpenRun,
  canManage,
}: {
  onRunStarted: (run: RunView) => void
  onOpenRun: (runId: string) => void
  canManage: boolean
}) {
  const [inProgress, setInProgress] = useState<{ schedule: string; runId: string }>()
  const options = useAsync(() => listScheduleOptions(), [])
  const schedules = useAsync(() => listSchedules(), [], { pollWhile: () => true, pollMs: 5000 })
  // Always fetched from the backend (no client cache) so a schedule lost in a restart is visible.
  const status = useAsync(() => getSystemStatus(), [schedules.data])
  const [form, setForm] = useState<ScheduleForm>(EMPTY_FORM)
  const [errors, setErrors] = useState<Partial<Record<keyof ScheduleForm, string>>>({})
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string>()
  const [saved, setSaved] = useState<string>()

  const set = (k: keyof ScheduleForm) => (v: string) => setForm((f) => ({ ...f, [k]: v }))

  async function save(e: React.FormEvent) {
    e.preventDefault()
    setError(undefined)
    setSaved(undefined)
    const found: Partial<Record<keyof ScheduleForm, string>> = {}
    for (const k of Object.keys(FIELD_LABELS) as Array<keyof ScheduleForm>) {
      if (!form[k].trim()) found[k] = `${FIELD_LABELS[k]} is required`
    }
    setErrors(found)
    if (Object.keys(found).length) return
    setSaving(true)
    try {
      const job = await createSchedule({ ...form, name: form.name.trim(), createdBy: actingUser() ?? 'ui' })
      setForm(EMPTY_FORM)
      setSaved(`Schedule "${job.name}" created — next run ${fmtTime(job.nextRunAt)}`)
      schedules.reload()
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err))
    } finally {
      setSaving(false)
    }
  }

  async function runNow(job: ScheduleView) {
    setError(undefined)
    setInProgress(undefined)
    try {
      const run = await runScheduleNow(job.id)
      schedules.reload()
      onRunStarted(run)
    } catch (err) {
      const body = err instanceof ApiError ? (err.body as { error?: string; runId?: string } | undefined) : undefined
      if (err instanceof ApiError && err.status === 409 && body?.error === 'RUN_IN_PROGRESS' && body.runId) {
        // someone (maybe another user) already started it: point at that run instead of failing
        setInProgress({ schedule: job.name, runId: body.runId })
        onOpenRun(body.runId)
      } else {
        setError(err instanceof Error ? err.message : String(err))
      }
    }
  }

  async function toggle(job: ScheduleView) {
    setError(undefined)
    try {
      await setScheduleActive(job.id, !job.active, job.version ?? undefined)
      schedules.reload()
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err))
      if (err instanceof ApiError && err.status === 409) schedules.reload() // changed by someone else: show their version
    }
  }

  const choices = (k: keyof ScheduleForm, label: string, values: string[]) => (
    <label>
      {label}
      <select value={form[k]} onChange={(e) => set(k)(e.target.value)} aria-invalid={!!errors[k]}>
        <option value="">Select…</option>
        {values.map((v) => (
          <option key={v} value={v}>{OPTION_LABELS[v] ?? v}</option>
        ))}
      </select>
      {errors[k] ? <span className="field-error" role="alert">{errors[k]}</span> : null}
    </label>
  )

  return (
    <>
      {status.data ? (
        <p className="muted" role="status">
          Backend started {fmtTime(status.data.serverStartedAt)} · {status.data.activeSchedules} active schedule
          {status.data.activeSchedules === 1 ? '' : 's'}
          {status.data.slaSeconds !== undefined ? ` · SLA ${status.data.slaSeconds}s per run` : ''}
        </p>
      ) : null}
      <Section title="Create schedule">
        {canManage ? (
        <form onSubmit={save} noValidate>
          <div className="filter-row">
            <label>
              Name
              <input value={form.name} onChange={(e) => set('name')(e.target.value)} aria-invalid={!!errors.name} />
              {errors.name ? <span className="field-error" role="alert">{errors.name}</span> : null}
            </label>
            {choices('sourceSystem', 'Source', options.data?.sources ?? [])}
            {choices('application', 'Application', options.data?.applications ?? [])}
            {choices('evidenceType', 'Evidence type', options.data?.evidenceTypes ?? [])}
            {choices('frequency', 'Frequency', options.data?.frequencies ?? [])}
          </div>
          <p>
            <button className="primary" type="submit" disabled={saving}>
              {saving ? 'Saving…' : 'Create schedule'}
            </button>
          </p>
        </form>
        ) : null}
        {saved ? <p className="muted" role="status">{saved}</p> : null}
        {error ? <ErrorNote message={error} /> : null}
        {inProgress ? (
          <p role="alert">
            <strong>Already running</strong> — {inProgress.schedule} has a run in progress.{" "}
            <button type="button" onClick={() => onOpenRun(inProgress.runId)}>
              Open the active run
            </button>
          </p>
        ) : null}
      </Section>

      <Section title="Schedules">
        {schedules.loading ? <Loading what="schedules" /> : null}
        {schedules.error ? <ErrorNote message={schedules.error} /> : null}
        {schedules.data && schedules.data.length === 0 ? <Empty message="No schedules yet." /> : null}
        {schedules.data && schedules.data.length > 0 ? (
          <DataTable
            rows={schedules.data}
            rowKey={(s) => s.id}
            columns={[
              { header: 'Name', cell: (s) => s.name },
              { header: 'Source', cell: (s) => s.sourceSystem },
              { header: 'Application', cell: (s) => s.application },
              { header: 'Frequency', cell: (s) => OPTION_LABELS[s.frequency] ?? s.frequency },
              { header: 'Created by', cell: (s) => s.createdBy ?? '—' },
              { header: 'Status', cell: (s) => <StatusPill status={s.active ? 'Active' : 'Inactive'} tone={s.active ? 'ok' : 'muted'} /> },
              { header: 'Next run', cell: (s) => fmtTime(s.nextRunAt) },
              { header: 'Last run', cell: (s) => fmtTime(s.lastRunAt) },
              {
                header: '',
                cell: (s) => (
                  <>
                    <button onClick={() => runNow(s)} disabled={!canManage}>Run now</button>{' '}
                  <button onClick={() => toggle(s)} disabled={!canManage}>{s.active ? 'Deactivate' : 'Activate'}</button>
                  </>
                ),
              },
            ]}
          />
        ) : null}
      </Section>
    </>
  )
}

const countOf = (text: string, key: string) => text.match(new RegExp(String.raw`${key}=(\d+)`))?.[1] ?? '—'

/** Per-source connectivity (CONNECTED/FAILED) beside the counts the executor reports. */
function SourceTable({ run }: { run: RunView }) {
  const rows = Object.entries(run.perSource)
  if (rows.length === 0) return <p className="muted">No per-source results yet.</p>
  return (
    <DataTable
      rows={rows}
      rowKey={([source]) => source}
      columns={[
        { header: 'Source', cell: ([source]) => source },
        {
          header: 'Connectivity',
          cell: ([source, text]) => {
            const c = run.sourceStatus?.[source] ?? (/connectivity=CONNECTED/.test(text) ? 'CONNECTED' : text.startsWith('error') ? 'FAILED' : '—')
            return c === '—' ? c : <StatusPill status={c} tone={c === 'CONNECTED' ? 'ok' : 'bad'} />
          },
        },
        { header: 'Received', cell: ([, t]) => countOf(t, 'received') },
        { header: 'Evidence', cell: ([, t]) => countOf(t, 'ingested') },
        { header: 'Duplicates', cell: ([, t]) => countOf(t, 'duplicates') },
        { header: 'Failed', cell: ([, t]) => countOf(t, 'failed') },
        {
          header: 'Error',
          cell: ([source]) => {
            const e = run.sourceErrors?.[source]
            return e ? (
              <>
                <strong>{e.errorCode}</strong>
                {e.errorMessage ? ` — ${e.errorMessage}` : ''}
              </>
            ) : (
              ''
            )
          },
        },
      ]}
    />
  )
}

/** Simulated credential state per source: Invalid / Disabled sources are not polled and their runs fail. */
function SourceConnections({ canManage }: { canManage: boolean }) {
  const conns = useAsync(() => listSourceConnections(), [], { pollWhile: () => true, pollMs: 5000 })
  const [error, setError] = useState<string>()

  async function change(source: string, status: string) {
    setError(undefined)
    try {
      await setSourceConnection(source, status)
      conns.reload()
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err))
    }
  }

  return (
    <Section title="Source connections">
      <p className="muted">
        Simulate source credential state. Invalid fails the next run once, then recovers automatically (status returns to Valid). Disabled keeps failing until it is re-enabled.
      </p>
      {conns.loading ? <Loading what="connections" /> : null}
      {conns.error ? <ErrorNote message={conns.error} /> : null}
      {error ? <ErrorNote message={error} /> : null}
      {conns.data ? (
        <DataTable
          rows={conns.data}
          rowKey={(c) => c.sourceSystem}
          columns={[
            { header: 'Source', cell: (c) => c.sourceSystem },
            { header: 'Status', cell: (c) => <StatusPill status={c.status} tone={c.status === 'VALID' ? 'ok' : 'bad'} /> },
            {
              header: 'Simulate source credential state',
              cell: (c) => (
                <select
                  aria-label={`Credential state for ${c.sourceSystem}`}
                  value={c.status}
                  disabled={!canManage}
                  onChange={(e) => change(c.sourceSystem, e.target.value)}
                >
                  <option value="VALID">Valid</option>
                  <option value="INVALID">Invalid</option>
                  <option value="DISABLED">Disabled</option>
                </select>
              ),
            },
          ]}
        />
      ) : null}
    </Section>
  )
}

const ALL_ROLES: Array<[string, string]> = [
  ['APP', 'App Owner'],
  ['AUDITOR', 'Auditor'],
  ['FH', 'Functional Head'],
  ['VH', 'Vertical Head'],
  ['CIO', 'CIO'],
  ['ADMIN', 'Admin'],
]

/** Who is alerted when a run finishes. Email delivery is simulated (a delivery record only, nothing is sent). */
function NotificationSettingsPanel({ canManage }: { canManage: boolean }) {
  const loaded = useAsync(() => getNotificationSettings(), [])
  const [draft, setDraft] = useState<NotificationSettings>()
  const [msg, setMsg] = useState<string>()
  const [error, setError] = useState<string>()
  const s = draft ?? loaded.data

  const edit = (patch: Partial<NotificationSettings>) => s && setDraft({ ...s, ...patch })

  async function save() {
    if (!s) return
    setError(undefined)
    setMsg(undefined)
    try {
      setDraft(await saveNotificationSettings(s))
      setMsg('Notification settings saved.')
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e))
      if (e instanceof ApiError && e.status === 409) {
        setDraft(undefined) // someone else saved first: drop our stale copy and show theirs
        loaded.reload()
      }
    }
  }

  const toggle = (label: string, key: 'enabled' | 'notifyOnSuccess' | 'notifyOnFailure' | 'emailSimulated') => (
    <label className="checkbox">
      <input
        type="checkbox"
        checked={s ? s[key] : false}
        disabled={!canManage || !s}
        onChange={(e) => edit({ [key]: e.target.checked })}
      />{' '}
      {label}
    </label>
  )

  return (
    <Section title="Notification settings">
      <p className="muted">Email delivery is simulated: a delivery record is stored, no email is actually sent.</p>
      {loaded.error ? <ErrorNote message={loaded.error} /> : null}
      {s ? (
        <>
          <div className="filter-row">
            {toggle('Notifications enabled', 'enabled')}
            {toggle('Notify on success', 'notifyOnSuccess')}
            {toggle('Notify on failure / partial', 'notifyOnFailure')}
            {toggle('Email (simulated)', 'emailSimulated')}
          </div>
          <fieldset disabled={!canManage}>
            <legend>Recipient roles</legend>
            {ALL_ROLES.map(([code, name]) => (
              <label key={code} className="checkbox">
                <input
                  type="checkbox"
                  checked={s.recipientRoles.includes(code)}
                  onChange={(e) =>
                    edit({
                      recipientRoles: e.target.checked
                        ? [...s.recipientRoles, code]
                        : s.recipientRoles.filter((r) => r !== code),
                    })
                  }
                />{' '}
                {name}{' '}
              </label>
            ))}
          </fieldset>
          {canManage ? (
            <p>
              <button className="primary" type="button" onClick={save}>
                Save notification settings
              </button>
            </p>
          ) : null}
        </>
      ) : null}
      {msg ? <p className="muted" role="status">{msg}</p> : null}
      {error ? <ErrorNote message={error} /> : null}
    </Section>
  )
}

const SECURITY_VIEW_ROLES: PersonaCode[] = ['ADMIN', 'AUDITOR']
const EVENT_TYPES = ['ACCESS_DENIED', 'UNKNOWN_ROLE', 'INPUT_REJECTED', 'INVALID_IDENTIFIER', 'UPLOAD_REJECTED']

/** Blocked-request audit trail (Admin / Auditor). All fields render as plain text. */
function SecurityEventsPanel() {
  const [type, setType] = useState('')
  const events = useAsync(() => listSecurityEvents(type || undefined), [type], { pollWhile: () => true, pollMs: 15_000 })

  return (
    <Section
      title="Security events"
      actions={
        <button type="button" onClick={() => events.reload()}>
          Refresh
        </button>
      }
    >
      <div className="filter-row">
        <label>
          Type
          <select value={type} onChange={(e) => setType(e.target.value)} aria-label="Event type">
            <option value="">All</option>
            {EVENT_TYPES.map((t) => (
              <option key={t} value={t}>
                {t}
              </option>
            ))}
          </select>
        </label>
      </div>
      {events.error ? <ErrorNote message={events.error} /> : null}
      {events.data && events.data.length === 0 ? <Empty message="No blocked requests recorded." /> : null}
      {events.data && events.data.length > 0 ? (
        <DataTable
          rows={events.data}
          rowKey={(e) => e.id}
          columns={[
            { header: 'Time', cell: (e) => fmtTime(e.occurredAt) },
            { header: 'Type', cell: (e) => e.eventType },
            { header: 'Role', cell: (e) => e.role ?? '—' },
            { header: 'User', cell: (e) => e.actor ?? '—' },
            { header: 'Method', cell: (e) => e.method ?? '—' },
            { header: 'Path', cell: (e) => e.path ?? '—' },
            { header: 'Detail', cell: (e) => e.detail ?? '' },
            { header: 'Outcome', cell: (e) => <StatusPill status={e.outcome} tone="bad" /> },
          ]}
        />
      ) : null}
    </Section>
  )
}

export function Scheduler() {
  const persona = currentPersona()
  const canManage = !!persona && SCHEDULER_MANAGE_ROLES.includes(persona.code)
  const [params, setParams] = useSearchParams()
  const selectedRunId = params.get('run') ?? undefined

  const applications = useAsync(() => listApplications(), [])
  const onboardedApps = (applications.data ?? []).filter((a) => a.active)
  const [page, setPage] = useState(0)
  const runs = useAsync(() => listRuns(page, RUNS_PAGE_SIZE), [page], { pollWhile: () => true, pollMs: 5000 })
  // Out-of-range page (e.g. stale page after runs were removed): fall back to the last real page.
  const lastPage = runs.data ? Math.max(0, runs.data.totalPages - 1) : 0
  useEffect(() => {
    if (runs.data && page > lastPage) setPage(lastPage)
  }, [runs.data, page, lastPage])
  const selected = useAsync<RunView | undefined>(
    () => (selectedRunId ? getRun(selectedRunId) : Promise.resolve(undefined)),
    [selectedRunId, runs.data?.items.length],
    { pollWhile: (d) => !!d && !isTerminal(d.status) },
  )

  // When the tracked run reaches a terminal state, refresh the runs table and —
  // if it completed — tell the Evidence Repository its list is now stale so
  // newly pulled evidence shows up without navigating there and back.
  const runsReload = runs.reload
  const lastStatus = useRef<string>()
  const selectedStatus = selected.data?.status
  useEffect(() => {
    const prev = lastStatus.current
    lastStatus.current = selectedStatus
    if (!selectedStatus || prev === selectedStatus) return
    if (isTerminal(selectedStatus)) {
      runsReload()
      if (selectedStatus === 'COMPLETED') emitDataEvent('evidence-changed')
    }
  }, [selectedStatus, runsReload])

  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string>()

  const noneOnboarded = !applications.loading && onboardedApps.length === 0

  async function start() {
    setBusy(true)
    setError(undefined)
    try {
      // No per-app / per-source selection — run collection across every onboarded
      // application and every configured source. Omitting applications/sources
      // makes the backend default to all active apps + its default source set.
      const run = await startRun({ requestedBy: 'ui' })
      setPage(0)
      runs.reload()
      setParams({ run: run.runId })
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e))
    } finally {
      setBusy(false)
    }
  }

  async function retry(runId: string) {
    setBusy(true)
    setError(undefined)
    try {
      const run = await retryRun(runId)
      setPage(0)
      runs.reload()
      setParams({ run: run.runId })
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="page">
      <h1>Scheduler</h1>

      {!canManage ? (
        <p className="banner banner-warn" role="note">
          Your role ({persona?.name ?? 'none'}) has view-only access to the Scheduler.
        </p>
      ) : null}

      <Section
        title="Start a collection run"
        actions={
          <button className="primary" onClick={start} disabled={busy || noneOnboarded || !canManage}>
            {busy ? 'Working…' : 'Run Collection'}
          </button>
        }
      >
        {noneOnboarded ? (
          <ErrorNote message="No applications onboarded — onboard at least one application before running the scheduler." />
        ) : (
          <p className="muted">
            Runs collection across all {onboardedApps.length || ''} onboarded application
            {onboardedApps.length === 1 ? '' : 's'} and every configured source.
          </p>
        )}
        {error ? <ErrorNote message={error} /> : null}
      </Section>

      <Schedules
        canManage={canManage}
        onOpenRun={(runId) => setParams({ run: runId })}
        onRunStarted={(run) => {
          setPage(0)
          runs.reload()
          setParams({ run: run.runId })
        }}
      />

      <SourceConnections canManage={canManage} />

      <NotificationSettingsPanel canManage={canManage} />

      {persona && SECURITY_VIEW_ROLES.includes(persona.code) ? <SecurityEventsPanel /> : null}

      <Section
        title="Audit trails"
        actions={
          runs.data && runs.data.totalPages > 1 ? (
            <div className="pager">
              <button disabled={page === 0} onClick={() => setPage((p) => p - 1)}>
                Prev
              </button>
              <span>
                Page {page + 1} / {runs.data.totalPages}
              </span>
              <button disabled={page + 1 >= runs.data.totalPages} onClick={() => setPage((p) => p + 1)}>
                Next
              </button>
            </div>
          ) : undefined
        }
      >
        {runs.data && runs.data.items.length > 0 ? (
          <RunSummary runs={runs.data.items} page={runs.data.page} size={runs.data.size} totalItems={runs.data.totalItems} />
        ) : null}
        {runs.loading ? <Loading what="audit trails" /> : null}
        {runs.error ? <ErrorNote message={runs.error} /> : null}
        {runs.data ? (
          <DataTable
            rows={runs.data.items}
            rowKey={(r) => r.runId}
            onRowClick={(r) => setParams({ run: r.runId })}
            columns={[
              { header: 'Run', cell: (r) => r.runId },
              { header: 'Started', cell: (r) => fmtTime(r.startedAt ?? r.createdAt) },
              { header: 'Triggered by', cell: (r) => r.triggeredBy ?? '—' },
              { header: 'Trigger', cell: (r) => r.trigger },
              { header: 'Schedule', cell: (r) => scheduleLabel(r) },
              { header: 'Retry of', cell: (r) => r.retryOfRunId ?? '—' },
              { header: 'Status', cell: (r) => <StatusPill status={r.status} /> },
              { header: 'Evidence', cell: (r) => r.ingested },
              { header: 'Duration', cell: (r) => fmtDuration(r.durationMs) },
              { header: 'Queue wait', cell: (r) => fmtDuration(r.queuedMs) },
              { header: 'SLA', cell: (r) => <SlaBadge run={r} /> },
              {
                header: 'Error',
                cell: (r) => (
                  <span className="cell-ellipsis" title={errorSummary(r)}>
                    {errorSummary(r)}
                  </span>
                ),
              },
              {
                header: '',
                cell: (r) => (
                  <button
                    disabled={!canManage || busy || r.status === 'RUNNING' || r.status === 'PENDING' || r.trigger === 'BULK'}
                    title={r.trigger === 'BULK' ? 'Bulk-upload batches are retried from Bulk Upload (re-submit the failed files)' : undefined}
                    onClick={(e) => {
                      e.stopPropagation()
                      retry(r.runId)
                    }}
                  >
                    Retry
                  </button>
                ),
              },
            ]}
          />
        ) : null}
      </Section>

      {selectedRunId ? (
        <Section title={`Run ${selectedRunId}`}>
          {selected.loading ? <Loading what="run" /> : null}
          {selected.error ? <ErrorNote message={`Run not found / invalid run id (${selected.error})`} /> : null}
          {selected.data && !selected.error ? (
            <>
              <RunPipeline run={selected.data} />
              <div className="stat-grid">
                <StatCard label="Status" value={<StatusPill status={selected.data.status} />} />
                <StatCard label="Received" value={selected.data.received} />
                <StatCard label="Ingested" value={selected.data.ingested} />
                <StatCard label="Duplicates" value={selected.data.duplicates} />
                <StatCard label="Failed" value={selected.data.failed} />
              </div>
              <p className="muted">{selected.data.message}</p>
              <h3>Audit</h3>
              <RunAudit run={selected.data} />
              <h3>Per source</h3>
              <SourceTable run={selected.data} />
            </>
          ) : null}
        </Section>
      ) : null}
    </div>
  )
}
