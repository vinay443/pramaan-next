package agent

import (
	"fmt"
	"os"
	"sort"
	"time"
)

// Registry is the in-process agent registration: it holds the identity of this
// agent and the set of collectors it advertises.
type Registry struct {
	ID          string
	Name        string
	Environment string
	Simulation  bool

	byType map[string]Collector
}

// NewRegistry creates an empty registry for an agent instance.
func NewRegistry(id, name, environment string, simulation bool) *Registry {
	return &Registry{
		ID:          id,
		Name:        name,
		Environment: environment,
		Simulation:  simulation,
		byType:      map[string]Collector{},
	}
}

// Register adds a collector. It panics on a duplicate type — registration is
// wiring done once at startup.
func (r *Registry) Register(c Collector) {
	if _, dup := r.byType[c.Type()]; dup {
		panic(fmt.Sprintf("collector type already registered: %s", c.Type()))
	}
	r.byType[c.Type()] = c
}

// Collectors returns all registered collectors, sorted by type.
func (r *Registry) Collectors() []Collector {
	out := make([]Collector, 0, len(r.byType))
	for _, c := range r.byType {
		out = append(out, c)
	}
	sort.Slice(out, func(i, j int) bool { return out[i].Type() < out[j].Type() })
	return out
}

// Get returns a collector by type.
func (r *Registry) Get(collectorType string) (Collector, bool) {
	c, ok := r.byType[collectorType]
	return c, ok
}

// Match returns the first registered collector that Supports the target.
func (r *Registry) Match(t Target) (Collector, bool) {
	for _, c := range r.Collectors() {
		if c.Supports(t) {
			return c, true
		}
	}
	return nil, false
}

// --- registration document -------------------------------------------------

// CheckInfo is a check advertised in the registration document. Field names and
// contents mirror the contract CheckDef (contracts/proto/agent.proto) /
// CheckDefView (contracts/openapi/pramaan-backend.yaml).
type CheckInfo struct {
	ID         string `json:"id"`
	Collector  string `json:"collector"`
	Technology string `json:"technology"`
	ControlID  string `json:"controlId"`
	Framework  string `json:"framework"`
	Title      string `json:"title"`
}

// CollectorInfo describes one collector in the registration document.
type CollectorInfo struct {
	Type         string      `json:"type"`
	Technologies []string    `json:"technologies"`
	Checks       []CheckInfo `json:"checks"`
}

// Descriptor is the self-describing agent registration document. Phase 1 has no
// backend registration endpoint in the contract, so this is produced locally
// (printed by `agent describe`) and is ready to POST once a contract exists.
type Descriptor struct {
	AgentID     string          `json:"agentId"`
	AgentName   string          `json:"agentName"`
	Host        string          `json:"host"`
	Environment string          `json:"environment"`
	Simulation  bool            `json:"simulation"`
	GeneratedAt string          `json:"generatedAt"`
	Collectors  []CollectorInfo `json:"collectors"`
	TotalChecks int             `json:"totalChecks"`
}

// AllChecks flattens every collector's checks into one list (contract CheckDefView order).
func (d Descriptor) AllChecks() []CheckInfo {
	var out []CheckInfo
	for _, c := range d.Collectors {
		out = append(out, c.Checks...)
	}
	return out
}

// Describe builds the registration document for this agent.
func (r *Registry) Describe(now time.Time) Descriptor {
	host, _ := os.Hostname()
	d := Descriptor{
		AgentID:     r.ID,
		AgentName:   r.Name,
		Host:        host,
		Environment: r.Environment,
		Simulation:  r.Simulation,
		GeneratedAt: now.UTC().Format(time.RFC3339),
	}
	for _, c := range r.Collectors() {
		ci := CollectorInfo{Type: c.Type(), Technologies: c.Technologies()}
		tech := ""
		if ts := c.Technologies(); len(ts) == 1 {
			tech = ts[0]
		}
		for _, ck := range c.Checks() {
			ci.Checks = append(ci.Checks, CheckInfo{
				ID: ck.ID, Collector: c.Type(), Technology: tech,
				ControlID: ck.ControlID, Framework: ck.Framework, Title: ck.Title,
			})
			d.TotalChecks++
		}
		d.Collectors = append(d.Collectors, ci)
	}
	return d
}
