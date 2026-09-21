import { useEffect } from 'react'
import type { ReactNode } from 'react'

export function StatCard({
  label,
  value,
  hint,
  icon,
  size = 'md',
}: {
  label: string
  value: ReactNode
  hint?: string
  icon?: ReactNode
  /** 'lg' for a hero-row headline tile (bigger value type) — same card, larger scale. */
  size?: 'md' | 'lg'
}) {
  return (
    <div className={size === 'lg' ? 'card stat stat-lg' : 'card stat'}>
      {icon ? <div className="stat-icon">{icon}</div> : null}
      <div className="stat-value">{value}</div>
      <div className="stat-label">{label}</div>
      {hint ? <div className="stat-hint">{hint}</div> : null}
    </div>
  )
}

export function Section({ title, actions, children }: { title: string; actions?: ReactNode; children: ReactNode }) {
  return (
    <section className="card">
      <div className="section-head">
        <h2>{title}</h2>
        {actions}
      </div>
      {children}
    </section>
  )
}

const PILL_OK = new Set([
  'COMPLETED', 'PASS', 'CREATED', 'COVERED', 'COMPLIANT', 'ONBOARDED', 'APPROVED',
  'GREEN', 'ACTIVE', 'READY', 'INTACT', 'SUCCESS', 'DONE', 'VERIFIED', 'COMPLETE', 'OK',
  'EVALUATED',
])
const PILL_BAD = new Set([
  'FAILED', 'FAIL', 'ERROR', 'MISSING', 'MISSING_EVIDENCE', 'NON_COMPLIANT',
  'REJECTED', 'RED', 'EXPIRED', 'CRITICAL', 'MISMATCH', 'TAMPERED', 'INCOMPLETE',
])
const PILL_MUTED = new Set([
  'DUPLICATE', 'NOT_APPLICABLE', 'NOT_ASSESSED', 'NOT ONBOARDED', 'NOT_ONBOARDED',
  'DRAFT', 'SUPERSEDED', 'SKIPPED', 'N/A', 'MEDIUM', 'LOW', 'UNKNOWN', 'NEW',
  'NOT_EVALUATED',
])

export function StatusPill({ status, tone: forced }: { status: string; tone?: 'ok' | 'bad' | 'muted' | 'warn' }) {
  const s = status.toUpperCase()
  const tone = forced ?? (PILL_OK.has(s) ? 'ok' : PILL_BAD.has(s) ? 'bad' : PILL_MUTED.has(s) ? 'muted' : 'warn')
  return <span className={`pill pill-${tone}`}>{status}</span>
}

/**
 * Centered, dismissible modal overlay. Dismisses on Escape or a click on the
 * backdrop (outside the panel); the caller supplies content and a close button
 * (or other actions) via {@code children} — same `.modal` / `.modal-head` /
 * `.modal-foot` classes as the Onboarding scan-progress modal, for a consistent look.
 */
export function Modal({
  title,
  onClose,
  children,
  size = 'md',
}: {
  title?: ReactNode
  onClose: () => void
  children: ReactNode
  /** 'lg' for content that needs more horizontal room (wide grids, multi-column lists). */
  size?: 'md' | 'lg'
}) {
  useEffect(() => {
    function onKeyDown(e: KeyboardEvent) {
      if (e.key === 'Escape') onClose()
    }
    document.addEventListener('keydown', onKeyDown)
    return () => document.removeEventListener('keydown', onKeyDown)
  }, [onClose])

  return (
    <div
      className="modal-overlay"
      role="dialog"
      aria-modal="true"
      aria-label={typeof title === 'string' ? title : undefined}
      onClick={(e) => {
        if (e.target === e.currentTarget) onClose()
      }}
    >
      <div className={size === 'lg' ? 'modal modal-lg' : 'modal'}>
        {title ? (
          <div className="modal-head">
            <h2>{title}</h2>
            <button type="button" className="ghost modal-close" aria-label="Close dialog" onClick={onClose}>
              ✕
            </button>
          </div>
        ) : null}
        {children}
      </div>
    </div>
  )
}

export function Loading({ what = 'data' }: { what?: string }) {
  return (
    <div className="state" role="status">
      <span className="state-spinner" aria-hidden="true" />
      <span>Loading {what}…</span>
    </div>
  )
}

export function ErrorNote({ message }: { message: string }) {
  return (
    <div className="state state-error" role="alert">
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" aria-hidden="true">
        <circle cx="12" cy="12" r="9" />
        <path d="M12 7v6M12 16.5h.01" />
      </svg>
      <span>{message}</span>
    </div>
  )
}

export function Empty({ message }: { message: string }) {
  return (
    <div className="state">
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
        <path d="M4 7h16v13H4zM4 7l2-3h12l2 3M9 12h6" />
      </svg>
      <span>{message}</span>
    </div>
  )
}

export function DataTable<T>({
  rows,
  columns,
  rowKey,
  onRowClick,
  className,
}: {
  rows: T[]
  columns: Array<{ header: string; cell: (row: T) => ReactNode; align?: 'right'; className?: string }>
  rowKey: (row: T) => string
  onRowClick?: (row: T) => void
  className?: string
}) {
  return (
    <div className={className ? `table-wrap ${className}` : 'table-wrap'}>
      <table>
        <thead>
          <tr>
            {columns.map((c) => (
              <th
                key={c.header}
                className={[c.align === 'right' ? 'num' : '', c.className ?? ''].filter(Boolean).join(' ') || undefined}
              >
                {c.header}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr
              key={rowKey(row)}
              onClick={onRowClick ? () => onRowClick(row) : undefined}
              className={onRowClick ? 'clickable' : undefined}
            >
              {columns.map((c) => (
                <td
                  key={c.header}
                  className={[c.align === 'right' ? 'num' : '', c.className ?? ''].filter(Boolean).join(' ') || undefined}
                >
                  {c.cell(row)}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

/** Horizontal progress bar (0-100%). Same visual language as the pipeline stepper's
 *  markers/fills — used for overall progress where a stepper is too granular. */
export function Meter({
  value,
  max = 100,
  label,
  tone = 'accent',
  indeterminate = false,
}: {
  value: number
  max?: number
  label?: string
  tone?: 'accent' | 'ok' | 'bad'
  /** True while progress can't be quantified yet (e.g. a bulk run in flight with no
   *  per-item completion signal from the backend) — renders a sweeping bar instead
   *  of a fabricated percentage. */
  indeterminate?: boolean
}) {
  const pct = max <= 0 ? 0 : Math.max(0, Math.min(100, (value / max) * 100))
  return (
    <div className="meter">
      <div
        className={indeterminate ? 'meter-track meter-indeterminate' : 'meter-track'}
        role="progressbar"
        aria-valuenow={indeterminate ? undefined : Math.round(pct)}
        aria-valuemin={0}
        aria-valuemax={100}
        aria-label={label}
      >
        <div className={`meter-fill meter-${tone}`} style={indeterminate ? undefined : { width: `${pct}%` }} />
      </div>
      {label ? <div className="meter-label">{label}</div> : null}
    </div>
  )
}

export function JsonBlock({ value }: { value: unknown }) {
  return <pre className="json">{JSON.stringify(value, null, 2)}</pre>
}

/**
 * Minimal single-series line chart (SVG). Same hand-rolled approach as the Trend
 * page sparkline, with axis labels + last-value marker. Theme-aware via currentColor.
 */
export function LineChart({
  points,
  height = 140,
  width = 560,
  yMax,
  yMin = 0,
  valueSuffix = '',
  ariaLabel,
}: {
  points: Array<{ label: string; value: number }>
  height?: number
  width?: number
  yMax?: number
  yMin?: number
  valueSuffix?: string
  ariaLabel: string
}) {
  if (points.length === 0) return <Empty message="No data yet." />
  const padL = 44
  const padB = 20
  const padT = 8
  const padR = 8
  const innerW = width - padL - padR
  const innerH = height - padT - padB
  const hi = yMax ?? Math.max(...points.map((p) => p.value), 1)
  const lo = Math.min(yMin, ...points.map((p) => p.value))
  const span = hi - lo || 1
  const x = (i: number) => padL + (points.length === 1 ? innerW / 2 : (i / (points.length - 1)) * innerW)
  const y = (v: number) => padT + innerH - ((v - lo) / span) * innerH
  const path = points.map((p, i) => `${i === 0 ? 'M' : 'L'}${x(i).toFixed(1)},${y(p.value).toFixed(1)}`).join(' ')
  const last = points[points.length - 1]
  const ticks = [lo, lo + span / 2, hi]

  return (
    <svg className="linechart" viewBox={`0 0 ${width} ${height}`} role="img" aria-label={ariaLabel}>
      {ticks.map((t, i) => (
        <g key={i}>
          <line x1={padL} x2={width - padR} y1={y(t)} y2={y(t)} className="linechart-grid" />
          <text x={padL - 6} y={y(t) + 3} textAnchor="end" className="linechart-axis">
            {Math.round(t * 10) / 10}
            {valueSuffix}
          </text>
        </g>
      ))}
      <path d={path} fill="none" stroke="currentColor" strokeWidth="2" />
      <circle cx={x(points.length - 1)} cy={y(last.value)} r="3" fill="currentColor" />
      <text x={padL} y={height - 6} textAnchor="start" className="linechart-axis">
        {points[0].label}
      </text>
      <text x={width - padR} y={height - 6} textAnchor="end" className="linechart-axis">
        {last.label}
      </text>
    </svg>
  )
}
