import { useState } from 'react'
import { useNavigate } from 'react-router-dom'

export const PERSONA_STORAGE_KEY = 'pramaan_selected_persona'

export type PersonaCode = 'APP' | 'AUDITOR' | 'FH' | 'VH' | 'CIO' | 'ADMIN'

export interface Persona {
  code: PersonaCode
  name: string
}

export const PERSONAS: Persona[] = [
  { code: 'APP', name: 'App Owner' },
  { code: 'AUDITOR', name: 'Auditor' },
  { code: 'FH', name: 'Functional Head' },
  { code: 'VH', name: 'Vertical Head' },
  { code: 'CIO', name: 'CIO' },
  { code: 'ADMIN', name: 'Admin' },
]

/** The persona picked at login, if any. */
export function currentPersona(): Persona | undefined {
  const code = getPersonaCode()
  return PERSONAS.find((p) => p.code === code)
}

/**
 * The persona is kept per browser tab (sessionStorage), so signing in as someone else in a second tab - or
 * logging out there - cannot change this tab's identity or role headers. A persona left in localStorage by an
 * older version is adopted once by the first tab that loads, then removed so it cannot leak into other tabs.
 */
export function getPersonaCode(): string | null {
  try {
    const own = sessionStorage.getItem(PERSONA_STORAGE_KEY)
    if (own) return own
    const legacy = localStorage.getItem(PERSONA_STORAGE_KEY)
    if (legacy) {
      sessionStorage.setItem(PERSONA_STORAGE_KEY, legacy)
      localStorage.removeItem(PERSONA_STORAGE_KEY)
      return legacy
    }
  } catch {
    /* storage unavailable */
  }
  return null
}

export function setPersonaCode(code: string): void {
  sessionStorage.setItem(PERSONA_STORAGE_KEY, code)
}

/** Logout: affects this tab only. */
export function clearPersona(): void {
  try {
    sessionStorage.removeItem(PERSONA_STORAGE_KEY)
    localStorage.removeItem(PERSONA_STORAGE_KEY)
  } catch {
    /* storage unavailable */
  }
}

/** Mirrors the backend SchedulerAccessPolicy: only these personas may change schedules, runs and source connections. */
export const SCHEDULER_MANAGE_ROLES: PersonaCode[] = ['APP', 'ADMIN']

/** Mirrors the backend: only these personas may raise or close audit observations. */
export const OBSERVATION_WRITE_ROLES: PersonaCode[] = ['AUDITOR', 'ADMIN']

export function PersonaLogin() {
  const [selected, setSelected] = useState<PersonaCode | ''>('')
  const navigate = useNavigate()

  function handleContinue() {
    if (!selected) return
    setPersonaCode(selected)
    navigate('/dashboard')
  }

  return (
    <div className="persona-login">
      <div className="persona-login-panel">
        <div className="persona-login-brand">
          <span className="brand-mark">P</span>
          <div>
            <div className="persona-login-title">Pramaan</div>
            <div className="persona-login-subtitle">Evidence &amp; Compliance Platform</div>
          </div>
        </div>

        <h1 className="persona-login-heading">Welcome to Pramaan</h1>
        <p className="muted persona-login-lead">Select your persona to continue</p>

        <div className="persona-login-field">
          <label className="persona-login-label" htmlFor="persona-select">
            Login
          </label>
          <select
            id="persona-select"
            aria-label="Login"
            value={selected}
            onChange={(e) => setSelected(e.target.value as PersonaCode | '')}
          >
            <option value="" disabled>
              Select Login
            </option>
            {PERSONAS.map((p) => (
              <option key={p.code} value={p.code}>
                {p.name}
              </option>
            ))}
          </select>
        </div>

        <div className="persona-login-actions">
          <button type="button" className="primary" disabled={!selected} onClick={handleContinue}>
            Continue to Pramaan
          </button>
        </div>

        <div className="persona-login-footer muted small">Pramaan | Evidence &amp; Compliance Platform</div>
      </div>
    </div>
  )
}
