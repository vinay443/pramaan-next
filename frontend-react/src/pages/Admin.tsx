import { useState } from 'react'
import {
  deleteAdminUser,
  listAdminRoles,
  listAdminUsers,
  listApplications,
  setAdminUserActive,
  upsertAdminUser,
} from '../api/endpoints'
import type { AdminUserView } from '../api/types'
import { useAsync } from '../hooks/useAsync'
import { DataTable, ErrorNote, Loading, Section, StatusPill } from '../components/ui'

type Draft = { username: string; displayName: string; email: string; roles: string[]; active: boolean }

const EMPTY: Draft = { username: '', displayName: '', email: '', roles: [], active: true }

export function Admin() {
  const users = useAsync(() => listAdminUsers(), [])
  const roles = useAsync(() => listAdminRoles(), [])
  const apps = useAsync(() => listApplications(), [])
  const [editing, setEditing] = useState<Draft | null>(null)
  const [isNew, setIsNew] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string>()

  function edit(u: AdminUserView) {
    setIsNew(false)
    setEditing({
      username: u.username,
      displayName: u.displayName,
      email: u.email ?? '',
      roles: [...u.roles],
      active: u.active,
    })
  }

  function startNew() {
    setIsNew(true)
    setError(undefined)
    setEditing({ ...EMPTY })
  }

  function toggleRole(id: string) {
    if (!editing) return
    setEditing({
      ...editing,
      roles: editing.roles.includes(id)
        ? editing.roles.filter((r) => r !== id)
        : [...editing.roles, id],
    })
  }

  async function save() {
    if (!editing) return
    if (!editing.username || !editing.displayName || editing.roles.length === 0) {
      setError('username, display name and at least one role are required')
      return
    }
    setBusy(true)
    setError(undefined)
    try {
      await upsertAdminUser(
        {
          username: editing.username,
          displayName: editing.displayName,
          email: editing.email || undefined,
          roles: editing.roles,
          active: editing.active,
        },
        !isNew,
      )
      setEditing(null)
      users.reload()
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e))
    } finally {
      setBusy(false)
    }
  }

  async function toggleActive(u: AdminUserView) {
    await setAdminUserActive(u.username, !u.active)
    users.reload()
  }

  async function remove(u: AdminUserView) {
    await deleteAdminUser(u.username)
    if (editing?.username === u.username) setEditing(null)
    users.reload()
  }

  return (
    <div className="page">
      <h1>Admin — Users, Roles &amp; Applications</h1>

      <Section
        title="Personas"
        actions={<button onClick={startNew}>+ New user</button>}
      >
        {users.loading ? <Loading what="users" /> : null}
        {users.error ? <ErrorNote message={users.error} /> : null}
        {users.data ? (
          <DataTable
            rows={users.data}
            rowKey={(u) => u.username}
            onRowClick={edit}
            columns={[
              { header: 'Username', cell: (u) => u.username },
              { header: 'Display name', cell: (u) => u.displayName },
              { header: 'Email', cell: (u) => u.email ?? '—' },
              { header: 'Roles', cell: (u) => u.roles.join(', ') },
              { header: 'Status', cell: (u) => <StatusPill status={u.active ? 'ACTIVE' : 'DISABLED'} /> },
              {
                header: 'Actions',
                cell: (u) => (
                  <span className="row-actions" onClick={(e) => e.stopPropagation()}>
                    <button onClick={() => toggleActive(u)}>{u.active ? 'Disable' : 'Enable'}</button>
                    <button onClick={() => remove(u)}>Delete</button>
                  </span>
                ),
              },
            ]}
          />
        ) : null}
      </Section>

      {editing ? (
        <Section title={isNew ? 'New user' : `Edit ${editing.username}`}>
          <div className="filter-row">
            <label>
              Username
              <input
                aria-label="Username"
                value={editing.username}
                disabled={!isNew}
                onChange={(e) => setEditing({ ...editing, username: e.target.value })}
              />
            </label>
            <label>
              Display name
              <input
                aria-label="Display name"
                value={editing.displayName}
                onChange={(e) => setEditing({ ...editing, displayName: e.target.value })}
              />
            </label>
            <label>
              Email
              <input
                aria-label="Email"
                value={editing.email}
                onChange={(e) => setEditing({ ...editing, email: e.target.value })}
              />
            </label>
            <label>
              Active
              <input
                type="checkbox"
                aria-label="Active"
                checked={editing.active}
                onChange={(e) => setEditing({ ...editing, active: e.target.checked })}
              />
            </label>
          </div>
          <fieldset>
            <legend>Roles</legend>
            {(roles.data ?? []).map((r) => (
              <label key={r.id} title={r.description} style={{ display: 'block' }}>
                <input
                  type="checkbox"
                  aria-label={r.id}
                  checked={editing.roles.includes(r.id)}
                  onChange={() => toggleRole(r.id)}
                />
                {' '}
                {r.id} — <span className="muted">{r.description}</span>
              </label>
            ))}
          </fieldset>
          {error ? <ErrorNote message={error} /> : null}
          <div className="row-actions">
            <button className="primary" onClick={save} disabled={busy}>
              {busy ? 'Saving…' : 'Save'}
            </button>
            <button onClick={() => setEditing(null)}>Cancel</button>
          </div>
        </Section>
      ) : null}

      <Section title="Role catalogue (read-only)">
        {roles.loading ? <Loading what="roles" /> : null}
        {roles.data ? (
          <DataTable
            rows={roles.data}
            rowKey={(r) => r.id}
            columns={[
              { header: 'Role', cell: (r) => r.id },
              { header: 'Description', cell: (r) => r.description },
            ]}
          />
        ) : null}
      </Section>

      <Section title="Onboarded applications">
        {apps.loading ? <Loading what="applications" /> : null}
        {apps.data ? (
          <DataTable
            rows={apps.data}
            rowKey={(a) => a.slug}
            columns={[
              { header: 'Slug', cell: (a) => a.slug },
              { header: 'Name', cell: (a) => a.name },
              { header: 'Business unit', cell: (a) => a.businessUnit ?? '—' },
              { header: 'Criticality', cell: (a) => <StatusPill status={a.criticality ?? 'MEDIUM'} /> },
              { header: 'Owner', cell: (a) => a.owner ?? '—' },
            ]}
          />
        ) : null}
        <p className="muted">Manage applications on the Applications screen.</p>
      </Section>
    </div>
  )
}
