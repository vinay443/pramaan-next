import { afterEach, describe, expect, it, vi } from 'vitest'
import { apiFetch } from '../api/client'
import { PERSONA_STORAGE_KEY, clearPersona, currentPersona, setPersonaCode } from './PersonaLogin'

/** A stand-in for one browser tab's sessionStorage. */
function tabStorage(): Storage {
  const m = new Map<string, string>()
  return {
    getItem: (k: string) => m.get(k) ?? null,
    setItem: (k: string, v: string) => void m.set(k, v),
    removeItem: (k: string) => void m.delete(k),
    clear: () => m.clear(),
    key: (i: number) => [...m.keys()][i] ?? null,
    get length() {
      return m.size
    },
  } as Storage
}

afterEach(() => {
  vi.unstubAllGlobals()
  localStorage.removeItem(PERSONA_STORAGE_KEY)
})

describe('persona per tab', () => {
  it('keeps each tab its own role and request headers, and logout only clears the current tab', async () => {
    const tab1 = tabStorage()
    const tab2 = tabStorage()
    const sent: Array<Record<string, string>> = []
    vi.stubGlobal(
      'fetch',
      vi.fn(async (_url: string, init: { headers: Record<string, string> }) => {
        sent.push(init.headers)
        return { ok: true, status: 200, statusText: 'OK', text: async () => '[]' }
      }),
    )

    vi.stubGlobal('sessionStorage', tab1)
    setPersonaCode('APP')
    vi.stubGlobal('sessionStorage', tab2)
    setPersonaCode('AUDITOR') // signing in as someone else "in the other tab"

    vi.stubGlobal('sessionStorage', tab1)
    expect(currentPersona()?.name).toBe('App Owner')
    await apiFetch('/api/v1/schedules')
    vi.stubGlobal('sessionStorage', tab2)
    expect(currentPersona()?.name).toBe('Auditor')
    await apiFetch('/api/v1/schedules')
    expect(sent[0]).toMatchObject({ 'X-Pramaan-Role': 'APP', 'X-Pramaan-User': 'App Owner' })
    expect(sent[1]).toMatchObject({ 'X-Pramaan-Role': 'AUDITOR', 'X-Pramaan-User': 'Auditor' })

    clearPersona() // logout in tab 2
    expect(currentPersona()).toBeUndefined()
    vi.stubGlobal('sessionStorage', tab1)
    expect(currentPersona()?.name).toBe('App Owner') // tab 1 untouched
  })

  it('adopts a persona left in localStorage by an older version once, without leaking it to other tabs', () => {
    const tab1 = tabStorage()
    const tab2 = tabStorage()
    localStorage.setItem(PERSONA_STORAGE_KEY, 'CIO')

    vi.stubGlobal('sessionStorage', tab1)
    expect(currentPersona()?.name).toBe('CIO') // first load keeps working
    expect(localStorage.getItem(PERSONA_STORAGE_KEY)).toBeNull()

    vi.stubGlobal('sessionStorage', tab2)
    expect(currentPersona()).toBeUndefined() // a new tab is not silently signed in as someone else
    vi.stubGlobal('sessionStorage', tab1)
    expect(currentPersona()?.name).toBe('CIO')
  })
})
