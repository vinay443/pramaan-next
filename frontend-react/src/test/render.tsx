import type { ReactElement } from 'react'
import { render } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { vi } from 'vitest'

/** Renders inside a router, with fetch stubbed to "offline" so endpoints use mock data. */
export function renderOffline(ui: ReactElement, initialPath = '/') {
  vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('offline in test')))
  return render(<MemoryRouter initialEntries={[initialPath]}>{ui}</MemoryRouter>)
}
