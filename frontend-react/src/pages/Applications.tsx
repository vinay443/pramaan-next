import { useState } from 'react'
import { deboardApplication, listApplications, onboardApplication, upsertApplication } from '../api/endpoints'
import type { ApplicationView, Criticality } from '../api/types'
import { useAsync } from '../hooks/useAsync'
import { DataTable, ErrorNote, Loading, Section, StatusPill } from '../components/ui'

const CRITICALITIES: Criticality[] = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL']

export function Applications() {
  const apps = useAsync(() => listApplications(), [])
  const [editing, setEditing] = useState<Partial<ApplicationView> | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string>()

  function startNew() {
    setEditing({ slug: '', name: '', criticality: 'MEDIUM', technology: [] })
  }

  async function toggleBoarded(slug: string, active: boolean) {
    setBusy(true)
    setError(undefined)
    try {
      await (active ? deboardApplication(slug) : onboardApplication(slug))
      apps.reload()
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e))
    } finally {
      setBusy(false)
    }
  }

  async function save() {
    if (!editing?.slug || !editing?.name) {
      setError('slug and name are required')
      return
    }
    setBusy(true)
    setError(undefined)
    try {
      const existing = apps.data?.some((a) => a.slug === editing.slug) ?? false
      await upsertApplication(
        {
          slug: editing.slug,
          name: editing.name,
          businessUnit: editing.businessUnit,
          criticality: editing.criticality,
          owner: editing.owner,
          technology: editing.technology,
        },
        existing,
      )
      setEditing(null)
      apps.reload()
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="page">
      <h1>Applications</h1>

      <Section title="Registered applications" actions={<button onClick={startNew}>+ New application</button>}>
        {apps.loading ? <Loading what="applications" /> : null}
        {apps.error ? <ErrorNote message={apps.error} /> : null}
        {apps.data ? (
          <DataTable
            rows={apps.data}
            rowKey={(a) => a.slug}
            onRowClick={(a) => setEditing(a)}
            columns={[
              { header: 'Slug', cell: (a) => a.slug },
              { header: 'Name', cell: (a) => a.name },
              { header: 'Business unit', cell: (a) => a.businessUnit ?? '—' },
              { header: 'Criticality', cell: (a) => <StatusPill status={a.criticality ?? 'MEDIUM'} /> },
              { header: 'Owner', cell: (a) => a.owner ?? '—' },
              { header: 'Technology', cell: (a) => (a.technology ?? []).join(', ') || '—' },
              { header: 'Auto', cell: (a) => (a.autoCreated ? 'yes' : '') },
              {
                header: 'Onboarding',
                cell: (a) => <StatusPill status={a.active ? 'ONBOARDED' : 'DEBOARDED'} />,
              },
              {
                header: '',
                cell: (a) => (
                  <button
                    disabled={busy}
                    onClick={(e) => {
                      e.stopPropagation()
                      toggleBoarded(a.slug, a.active)
                    }}
                  >
                    {a.active ? 'Deboard' : 'Onboard'}
                  </button>
                ),
              },
            ]}
          />
        ) : null}
      </Section>

      {editing ? (
        <Section title={editing.createdAt ? `Edit ${editing.slug}` : 'New application'}>
          <div className="filter-row">
            <label>
              Slug
              <input
                aria-label="Slug"
                value={editing.slug ?? ''}
                disabled={Boolean(editing.createdAt)}
                onChange={(e) => setEditing({ ...editing, slug: e.target.value })}
              />
            </label>
            <label>
              Name
              <input aria-label="Name" value={editing.name ?? ''} onChange={(e) => setEditing({ ...editing, name: e.target.value })} />
            </label>
            <label>
              Business unit
              <input
                aria-label="Business unit"
                value={editing.businessUnit ?? ''}
                onChange={(e) => setEditing({ ...editing, businessUnit: e.target.value })}
              />
            </label>
            <label>
              Criticality
              <select
                aria-label="Criticality"
                value={editing.criticality ?? 'MEDIUM'}
                onChange={(e) => setEditing({ ...editing, criticality: e.target.value as Criticality })}
              >
                {CRITICALITIES.map((c) => (
                  <option key={c}>{c}</option>
                ))}
              </select>
            </label>
            <label>
              Owner
              <input aria-label="Owner" value={editing.owner ?? ''} onChange={(e) => setEditing({ ...editing, owner: e.target.value })} />
            </label>
            <label>
              Technology (comma-separated)
              <input
                aria-label="Technology"
                value={(editing.technology ?? []).join(', ')}
                onChange={(e) =>
                  setEditing({ ...editing, technology: e.target.value.split(',').map((t) => t.trim()).filter(Boolean) })
                }
              />
            </label>
          </div>
          {error ? <ErrorNote message={error} /> : null}
          <div className="row-actions">
            <button className="primary" onClick={save} disabled={busy}>
              {busy ? 'Saving…' : 'Save'}
            </button>
            <button onClick={() => setEditing(null)}>Cancel</button>
          </div>
        </Section>
      ) : null}
    </div>
  )
}
