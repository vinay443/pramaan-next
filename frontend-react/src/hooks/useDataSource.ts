import { useSyncExternalStore } from 'react'
import { getDataSource, subscribeDataSource, type DataSource } from '../api/dataSource'

/** Reactive view of whether the UI is showing live backend data or mock fallback. */
export function useDataSource(): DataSource {
  return useSyncExternalStore(subscribeDataSource, getDataSource, getDataSource)
}
