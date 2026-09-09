import '@testing-library/jest-dom/vitest'
import { afterEach } from 'vitest'
import { cleanup } from '@testing-library/react'
import { setDataSource } from '../api/dataSource'

afterEach(() => {
  cleanup()
  setDataSource('unknown')
})
