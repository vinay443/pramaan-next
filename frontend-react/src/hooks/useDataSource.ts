import { useSyncExternalStore } from 'react'
import {
  getDataSource,
  isDataSourceBannerSuppressed,
  subscribeBannerSuppression,
  subscribeDataSource,
  type DataSource,
} from '../api/dataSource'

/** Reactive view of whether the UI is showing live backend data or mock fallback. */
export function useDataSource(): DataSource {
  return useSyncExternalStore(subscribeDataSource, getDataSource, getDataSource)
}

/** True while a screen that is intentionally always-mock asks the shell to hide the mock-data banner. */
export function useDataSourceBannerSuppressed(): boolean {
  return useSyncExternalStore(subscribeBannerSuppression, isDataSourceBannerSuppressed, isDataSourceBannerSuppressed)
}
