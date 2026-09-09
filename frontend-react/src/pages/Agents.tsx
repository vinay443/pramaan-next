import { listAgents } from '../api/endpoints'
import { useAsync } from '../hooks/useAsync'
import { DataTable, ErrorNote, Loading, Section, StatCard } from '../components/ui'

export function Agents() {
  const agents = useAsync(() => listAgents(), [])

  return (
    <div className="page">
      <h1>Agents</h1>
      <p className="muted">
        Technical collectors (Go). The backend has no agent-registration endpoint in the contract yet, so this view is
        populated from each agent's <code>describe</code> output.
      </p>

      {agents.loading ? <Loading what="agents" /> : null}
      {agents.error ? <ErrorNote message={agents.error} /> : null}

      {(agents.data ?? []).map((a) => (
        <Section key={a.agentId} title={`${a.agentName} (${a.agentId})`}>
          <div className="stat-grid">
            <StatCard label="Host" value={a.host} />
            <StatCard label="Environment" value={a.environment} />
            <StatCard label="Simulation" value={a.simulation ? 'on' : 'off'} />
            <StatCard label="Collectors" value={a.collectors.length} />
            <StatCard label="Checks" value={a.totalChecks} />
          </div>

          {a.collectors.map((c) => (
            <div key={c.type} className="collector">
              <h3>
                {c.type} <span className="muted">— {c.technologies.join(', ')}</span>
              </h3>
              <DataTable
                rows={c.checks}
                rowKey={(ch) => ch.id}
                columns={[
                  { header: 'Check', cell: (ch) => ch.id },
                  { header: 'Control', cell: (ch) => ch.controlId },
                  { header: 'Framework', cell: (ch) => ch.framework },
                  { header: 'Title', cell: (ch) => ch.title },
                ]}
              />
            </div>
          ))}
        </Section>
      ))}
    </div>
  )
}
