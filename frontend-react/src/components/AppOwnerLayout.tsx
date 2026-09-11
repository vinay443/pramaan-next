import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { PERSONA_STORAGE_KEY } from '../pages/PersonaLogin'

/**
 * A separate, dedicated shell for the App Owner persona — does not import or
 * reuse the shared Layout/sidebar, so the existing app shell used by every other
 * persona is completely untouched by this page.
 */
export function AppOwnerLayout() {
  const navigate = useNavigate()

  function handleLogout() {
    localStorage.removeItem(PERSONA_STORAGE_KEY)
    navigate('/login')
  }

  return (
    <div className="app">
      <aside className="sidebar">
        <div className="brand">
          <span className="brand-mark">P</span>
          Pramaan <span className="brand-dim">Next</span>
        </div>
        <div className="persona-bar">
          <span className="pill pill-muted">App Owner (APP)</span>
          <button type="button" className="ghost persona-logout" onClick={handleLogout}>
            Logout
          </button>
        </div>
        <nav aria-label="Primary">
          <div className="nav-group">
            <div className="nav-heading">App Owner</div>
            <NavLink to="/app-owner" end className={({ isActive }) => (isActive ? 'nav-link active' : 'nav-link')}>
              Dashboard
            </NavLink>
            <NavLink to="/app-owner/compliance-pending" className={({ isActive }) => (isActive ? 'nav-link active' : 'nav-link')}>
              Compliance Pending
            </NavLink>
            <NavLink to="/app-owner/notifications" className={({ isActive }) => (isActive ? 'nav-link active' : 'nav-link')}>
              Notifications
            </NavLink>
            <NavLink to="/app-owner/reports" className={({ isActive }) => (isActive ? 'nav-link active' : 'nav-link')}>
              Reports
            </NavLink>
          </div>
        </nav>
      </aside>
      <main className="content">
        <Outlet />
      </main>
    </div>
  )
}
