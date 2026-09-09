package collectors

import "github.com/pramaan/pramaan-next/agents-go/internal/agent"

// All returns one instance of every collector.
//
//	os / database / middleware / tls — Phase 1 technical hardening collectors
//	governance                       — Phase 2 change-management + secure-SDLC collector
func All() []agent.Collector {
	return []agent.Collector{
		NewOSCollector(),
		NewDatabaseCollector(),
		NewMiddlewareCollector(),
		NewTLSCollector(),
		NewGovernanceCollector(),
	}
}

// Register adds every collector to the registry.
func Register(reg *agent.Registry) {
	for _, c := range All() {
		reg.Register(c)
	}
}

// AllChecks returns the full predefined technical-check catalog across collectors.
func AllChecks() []agent.Check {
	var out []agent.Check
	for _, c := range All() {
		out = append(out, c.Checks()...)
	}
	return out
}
