import type { ReactNode } from 'react'
import { NavLink, Outlet } from 'react-router-dom'
import { useDataSource } from '../hooks/useDataSource'

interface NavItem {
  to: string
  label: string
  end?: boolean
  icon: ReactNode
}

interface NavGroup {
  heading: string
  items: NavItem[]
}

// Simple 20×20 line icons (stroke = currentColor) so the sidebar reads as
// grouped navigation rather than a wall of text.
const I = {
  gauge: (
    <path d="M12 13a4 4 0 0 1 4-4M4 13a8 8 0 0 1 15.5-2.7M4.5 16h15" />
  ),
  archive: <path d="M4 7h16M6 7v11a1 1 0 0 0 1 1h10a1 1 0 0 0 1-1V7M9 11h6" />,
  search: <path d="M11 18a7 7 0 1 0 0-14 7 7 0 0 0 0 14ZM20 20l-4-4" />,
  list: <path d="M8 6h12M8 12h12M8 18h12M4 6h.01M4 12h.01M4 18h.01" />,
  upload: <path d="M12 15V4M8 8l4-4 4 4M5 15v4a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1v-4" />,
  clock: <path d="M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18ZM12 7v5l3 2" />,
  check: <path d="M4 12a8 8 0 1 0 16 0 8 8 0 0 0-16 0ZM9 12l2 2 4-4" />,
  layers: <path d="M12 3 3 8l9 5 9-5-9-5ZM3 13l9 5 9-5M3 17l9 5 9-5" />,
  shield: <path d="M12 3 5 6v5c0 4.5 3 8 7 10 4-2 7-5.5 7-10V6l-7-3Z" />,
  crown: <path d="M4 8l3.5 3L12 5l4.5 6L20 8l-1.5 10h-13L4 8Z" />,
  columns: <path d="M4 5h16v14H4zM12 5v14M4 12h16" />,
  building: <path d="M5 21V5a2 2 0 0 1 2-2h6a2 2 0 0 1 2 2v16M15 21V9h3a2 2 0 0 1 2 2v10M9 7h2M9 11h2M9 15h2" />,
  clipboard: <path d="M9 4h6v3H9zM7 5H5v16h14V5h-2M9 12h6M9 16h4" />,
  trend: <path d="M4 18 10 12l4 4 6-8M15 6h5v5" />,
  file: <path d="M7 3h7l5 5v13H7zM14 3v5h5M10 13h6M10 17h6" />,
  recycle: <path d="M7 7l2-3h6l2 3M17 7l2 4-3 2M7 7 4 11l3 2M9 20h6l-2-3M13 15l-2 5" />,
  chat: <path d="M4 5h16v10H9l-4 4V5Z" />,
  grid: <path d="M4 4h7v7H4zM13 4h7v7h-7zM4 13h7v7H4zM13 13h7v7h-7z" />,
  power: <path d="M12 4v8M7.5 7a7 7 0 1 0 9 0" />,
  users: <path d="M9 11a3.5 3.5 0 1 0 0-7 3.5 3.5 0 0 0 0 7ZM3 20c0-3 2.7-5 6-5s6 2 6 5M16 4.5a3.5 3.5 0 0 1 0 7M21 20c0-2.4-1.6-4.2-4-4.8" />,
  cpu: <path d="M7 7h10v10H7zM4 10h3M4 14h3M17 10h3M17 14h3M10 4v3M14 4v3M10 17v3M14 17v3" />,
}

function Icon({ d }: { d: ReactNode }) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      {d}
    </svg>
  )
}

const NAV: NavGroup[] = [
  {
    heading: 'Overview',
    items: [{ to: '/', label: 'Dashboard', end: true, icon: <Icon d={I.gauge} /> }],
  },
  {
    heading: 'Evidence',
    items: [
      { to: '/evidence', label: 'Repository', icon: <Icon d={I.archive} /> },
      { to: '/evidence/query', label: 'Evidence Query', icon: <Icon d={I.search} /> },
      { to: '/predefined-queries', label: 'Predefined Queries', icon: <Icon d={I.list} /> },
      { to: '/bulk-upload', label: 'Bulk Upload', icon: <Icon d={I.upload} /> },
      { to: '/scheduler', label: 'Scheduler', icon: <Icon d={I.clock} /> },
      { to: '/reuse', label: 'Evidence Reuse', icon: <Icon d={I.recycle} /> },
    ],
  },
  {
    heading: 'Compliance',
    items: [
      { to: '/control-results', label: 'Control Results', icon: <Icon d={I.check} /> },
      { to: '/completeness', label: 'Completeness', icon: <Icon d={I.layers} /> },
      { to: '/compliance', label: 'Compliance', icon: <Icon d={I.shield} /> },
      { to: '/audit-prep', label: 'Audit Prep', icon: <Icon d={I.clipboard} /> },
      { to: '/reports', label: 'Reports', icon: <Icon d={I.file} /> },
    ],
  },
  {
    heading: 'Insights',
    items: [
      { to: '/leadership', label: 'Leadership', icon: <Icon d={I.crown} /> },
      { to: '/comparison', label: 'Comparison', icon: <Icon d={I.columns} /> },
      { to: '/enterprise', label: 'Enterprise', icon: <Icon d={I.building} /> },
      { to: '/trend', label: 'Trend', icon: <Icon d={I.trend} /> },
      { to: '/nl-query', label: 'NL Queries', icon: <Icon d={I.chat} /> },
    ],
  },
  {
    heading: 'Admin',
    items: [
      { to: '/applications', label: 'Applications', icon: <Icon d={I.grid} /> },
      { to: '/onboarding', label: 'Onboarding', icon: <Icon d={I.power} /> },
      { to: '/admin', label: 'Users & Roles', icon: <Icon d={I.users} /> },
      { to: '/agents', label: 'Agents', icon: <Icon d={I.cpu} /> },
    ],
  },
]

export function Layout() {
  const source = useDataSource()
  return (
    <div className="app">
      <aside className="sidebar">
        <div className="brand">
          <span className="brand-mark">P</span>
          Pramaan <span className="brand-dim">Next</span>
        </div>
        <nav aria-label="Primary">
          {NAV.map((group) => (
            <div className="nav-group" key={group.heading}>
              <div className="nav-heading">{group.heading}</div>
              {group.items.map((n) => (
                <NavLink
                  key={n.to}
                  to={n.to}
                  end={n.end}
                  className={({ isActive }) => (isActive ? 'nav-link active' : 'nav-link')}
                >
                  <span className="nav-icon">{n.icon}</span>
                  {n.label}
                </NavLink>
              ))}
            </div>
          ))}
        </nav>
        <div className="sidebar-foot">
          <span className={`conn-dot conn-${source}`} aria-hidden="true" />
          {source === 'live'
            ? 'Live backend'
            : source === 'mock'
              ? 'Mock data'
              : 'Connecting…'}
        </div>
      </aside>
      <main className="content">
        {source === 'mock' ? (
          <div className="banner banner-warn" role="status">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" aria-hidden="true">
              <path d="M12 8v5M12 16h.01M10.3 3.9 2.5 18a2 2 0 0 0 1.7 3h15.6a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0Z" />
            </svg>
            <span>
              Backend unavailable — showing mock data. Start the backend on <code>localhost:8080</code> for live data.
            </span>
          </div>
        ) : null}
        <Outlet />
      </main>
    </div>
  )
}
