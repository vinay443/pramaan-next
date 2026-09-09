package collectors

import (
	"encoding/json"

	"github.com/pramaan/pramaan-next/agents-go/internal/agent"
)

// SimulationTargets is a deterministic target set used when SIMULATION_MODE is on
// and the operator passes no target file. Application slugs match the backend
// demo applications.
func SimulationTargets() []agent.Target {
	return []agent.Target{
		{Kind: "os", Name: "netbanking-app-01", Application: "net-banking", Technology: "linux"},
		{Kind: "database", Name: "netbanking-pg-01", Application: "net-banking", Technology: "postgresql"},
		{Kind: "middleware", Name: "netbanking-nginx-01", Application: "net-banking", Technology: "nginx"},
		{Kind: "tls", Name: "netbanking-edge", Application: "net-banking", Technology: "tls", Address: "netbanking.internal:443"},
		{Kind: "governance", Name: "netbanking-governance", Application: "net-banking", Technology: "governance"},
		{Kind: "os", Name: "payments-app-01", Application: "payments", Technology: "linux"},
		{Kind: "database", Name: "payments-pg-01", Application: "payments", Technology: "postgresql"},
		{Kind: "database", Name: "payments-mysql-01", Application: "payments", Technology: "mysql"},
		{Kind: "middleware", Name: "payments-tomcat-01", Application: "payments", Technology: "tomcat"},
		{Kind: "tls", Name: "payments-edge", Application: "payments", Technology: "tls", Address: "payments.internal:443"},
		{Kind: "governance", Name: "payments-governance", Application: "payments", Technology: "governance"},
	}
}

// ParseTargets reads a JSON array of agent.Target.
func ParseTargets(data []byte) ([]agent.Target, error) {
	var ts []agent.Target
	if err := json.Unmarshal(data, &ts); err != nil {
		return nil, err
	}
	return ts, nil
}
