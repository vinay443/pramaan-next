import { useState } from 'react'
import { useNavigate } from 'react-router-dom'

export const PERSONA_STORAGE_KEY = 'pramaan_selected_persona'

export type PersonaCode = 'APP' | 'AUDITOR' | 'FH' | 'VH' | 'CIO'

interface Persona {
  code: PersonaCode
  name: string
}

export const PERSONAS: Persona[] = [
  { code: 'APP', name: 'App Owner' },
  { code: 'AUDITOR', name: 'Auditor' },
  { code: 'FH', name: 'Functional Head' },
  { code: 'VH', name: 'Vertical Head' },
  { code: 'CIO', name: 'CIO' },
]

export function PersonaLogin() {
  const [selected, setSelected] = useState<PersonaCode | ''>('')
  const navigate = useNavigate()

  function handleContinue() {
    if (!selected) return
    localStorage.setItem(PERSONA_STORAGE_KEY, selected)
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

        <label htmlFor="persona-select">
          Persona
          <select
            id="persona-select"
            value={selected}
            onChange={(e) => setSelected(e.target.value as PersonaCode | '')}
          >
            <option value="" disabled>
              Select Persona
            </option>
            {PERSONAS.map((p) => (
              <option key={p.code} value={p.code}>
                {p.name} ({p.code})
              </option>
            ))}
          </select>
        </label>

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
