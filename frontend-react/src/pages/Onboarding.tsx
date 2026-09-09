import { useState } from 'react'
import { applyOnboarding, deboardApplication, getOnboardingPlan, onboardApplication } from '../api/endpoints'
import type { OnboardingResult } from '../api/types'
import { useAsync } from '../hooks/useAsync'
import { DataTable, ErrorNote, Loading, Section, StatCard, StatusPill } from '../components/ui'

export function Onboarding() {
  const plan = useAsync(() => getOnboardingPlan(), [])
  const [selected, setSelected] = useState<string[]>([])
  const [collect, setCollect] = useState(false)
  const [result, setResult] = useState<OnboardingResult>()
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string>()

  const notOnboarded = (plan.data?.items ?? []).filter((i) => i.status !== 'EXISTS')

  function toggle(slug: string) {
    setSelected((cur) => (cur.includes(slug) ? cur.filter((s) => s !== slug) : [...cur, slug]))
  }

  async function onboard() {
    if (selected.length === 0) {
      setError('Select at least one application to onboard.')
      return
    }
    setBusy(true)
    setError(undefined)
    try {
      setResult(await applyOnboarding(selected, collect))
      setSelected([])
      plan.reload()
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e))
    } finally {
      setBusy(false)
    }
  }

  async function toggleBoarded(slug: string, onboarded: boolean) {
    setBusy(true)
    setError(undefined)
    try {
      await (onboarded ? deboardApplication(slug) : onboardApplication(slug))
      plan.reload()
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="page">
      <h1>Multi-application Onboarding</h1>
      <p className="muted">
        The catalogue (identity, in-scope frameworks, evidence sources) comes from{' '}
        <code>phase2/onboarding.json</code>. Pick which applications to onboard — onboarding is a
        selective, idempotent upsert via the application store. Backed by <code>/api/v1/onboarding</code>.
      </p>

      {plan.loading ? <Loading what="onboarding plan" /> : null}
      {plan.error ? <ErrorNote message={plan.error} /> : null}

      {plan.data ? (
        <>
          <div className="stat-grid">
            <StatCard label="Applications" value={plan.data.total} />
            <StatCard label="Not onboarded" value={plan.data.toCreate} />
            <StatCard label="Onboarded" value={plan.data.existing} />
          </div>

          <Section
            title="Onboard applications"
            actions={
              <span className="row-actions">
                <label>
                  <input type="checkbox" checked={collect} onChange={(e) => setCollect(e.target.checked)} />{' '}
                  also collect evidence
                </label>
                <button className="primary" onClick={onboard} disabled={busy || notOnboarded.length === 0}>
                  {busy ? 'Working…' : `Onboard${selected.length ? ` (${selected.length})` : ''}`}
                </button>
              </span>
            }
          >
            {error ? <ErrorNote message={error} /> : null}
            {result ? (
              <p className="answer">
                Onboarded {result.applied} — {result.created} created, {result.updated} updated.
                {result.collectionRunId ? ` Collection run ${result.collectionRunId} started.` : ''}
              </p>
            ) : null}
            {notOnboarded.length === 0 ? (
              <p className="muted">Every catalogued application is onboarded.</p>
            ) : (
              <fieldset className="sources">
                <legend>Not yet onboarded</legend>
                {notOnboarded.map((i) => (
                  <label key={i.slug} className="checkbox">
                    <input type="checkbox" checked={selected.includes(i.slug)} onChange={() => toggle(i.slug)} />{' '}
                    {i.name} <span className="muted small">({i.slug})</span>
                  </label>
                ))}
              </fieldset>
            )}
          </Section>

          <Section title="Catalogue">
            <DataTable
              rows={plan.data.items}
              rowKey={(i) => i.slug}
              columns={[
                { header: 'Application', cell: (i) => i.name },
                { header: 'Criticality', cell: (i) => <StatusPill status={i.criticality} /> },
                { header: 'Frameworks', cell: (i) => i.frameworks.join(', ') || '—' },
                { header: 'Sources', cell: (i) => i.sources.join(', ') || '—' },
                {
                  header: 'Status',
                  cell: (i) => (
                    <>
                      <StatusPill status={i.status === 'EXISTS' ? 'ONBOARDED' : 'NOT ONBOARDED'} />
                      {i.unknownSources.length > 0 ? (
                        <StatusPill status={`unknown: ${i.unknownSources.join(',')}`} />
                      ) : null}
                    </>
                  ),
                },
                {
                  header: '',
                  cell: (i) => (
                    <button
                      disabled={busy}
                      onClick={(e) => {
                        e.stopPropagation()
                        toggleBoarded(i.slug, i.status === 'EXISTS')
                      }}
                    >
                      {i.status === 'EXISTS' ? 'Deboard' : 'Onboard'}
                    </button>
                  ),
                },
              ]}
            />
          </Section>
        </>
      ) : null}
    </div>
  )
}
