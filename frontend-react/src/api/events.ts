// Tiny in-process pub/sub so one page can tell another that server-side data it
// renders has changed (this app has no global query cache). Currently used by the
// Scheduler page to nudge the Evidence Repository list after a collection run
// finishes pulling new evidence.

type DataEvent = 'evidence-changed'

const listeners: Record<DataEvent, Set<() => void>> = {
  'evidence-changed': new Set(),
}

export function onDataEvent(event: DataEvent, fn: () => void): () => void {
  listeners[event].add(fn)
  return () => listeners[event].delete(fn)
}

export function emitDataEvent(event: DataEvent): void {
  for (const fn of [...listeners[event]]) fn()
}
