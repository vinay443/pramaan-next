package job

import "fmt"

// ParamRule is one entry in an approved collector's parameter contract.
type ParamRule struct {
	// Name is the parameter key as it appears in JobRequest.Parameters.
	Name string
	// Required means the job request must supply a non-empty value.
	Required bool
	// AllowedValues, if non-empty, restricts the value to this enum.
	AllowedValues []string
}

func (p ParamRule) allowed(v string) bool {
	if len(p.AllowedValues) == 0 {
		return true
	}
	for _, a := range p.AllowedValues {
		if a == v {
			return true
		}
	}
	return false
}

// ApprovedCollector is one versioned, pre-approved collector/query operation
// that a JobRequest may reference. It maps directly onto an existing
// agent.Collector (Type + Technologies) already wired into the shared
// registry in internal/collectors — this package never introduces new
// collection logic, only the validation/execution envelope around it.
type ApprovedCollector struct {
	// ID is the approved collector/query identifier (e.g. "database-hardening").
	ID string
	// Version is the approved version of that collector/query (e.g. "v1").
	// A job requesting a different version for a known ID is rejected, not
	// silently run against the latest version.
	Version string
	// AgentType must match an agent.Collector.Type() already registered.
	AgentType string
	// Technology, if non-empty, is fixed for this catalog entry (e.g. "tls").
	// If empty, the job must supply an allowed "technology" parameter.
	Technology string
	// Params is the whitelist+validation contract for JobRequest.Parameters.
	// Any parameter key not listed here is rejected.
	Params []ParamRule
	// MaxOutputBytes bounds total evidence content for this collector. Zero
	// means: use the executor's global default.
	MaxOutputBytes int
}

func (c ApprovedCollector) key() string { return c.ID + "@" + c.Version }

// Catalog is the fixed, versioned set of collector/query operations a job may
// invoke. It is built once at startup (see DefaultCatalog) and never mutated
// at request time.
type Catalog struct {
	byID map[string][]ApprovedCollector // ID -> all known versions
}

// LookupStatus reports why a catalog lookup did or didn't succeed.
type LookupStatus int

const (
	LookupFound LookupStatus = iota
	LookupUnknownID
	LookupUnsupportedVersion
)

// NewCatalog builds a Catalog from a fixed list of approved entries. It
// panics on a duplicate (ID, Version) pair — like agent.Registry.Register,
// this is wiring done once at startup, not request-time data.
func NewCatalog(entries []ApprovedCollector) *Catalog {
	c := &Catalog{byID: map[string][]ApprovedCollector{}}
	seen := map[string]bool{}
	for _, e := range entries {
		if seen[e.key()] {
			panic(fmt.Sprintf("job: duplicate approved collector %s", e.key()))
		}
		seen[e.key()] = true
		c.byID[e.ID] = append(c.byID[e.ID], e)
	}
	return c
}

// Lookup resolves an (ID, Version) pair against the approved catalog.
func (c *Catalog) Lookup(id, version string) (ApprovedCollector, LookupStatus) {
	versions, ok := c.byID[id]
	if !ok {
		return ApprovedCollector{}, LookupUnknownID
	}
	for _, v := range versions {
		if v.Version == version {
			return v, LookupFound
		}
	}
	return ApprovedCollector{}, LookupUnsupportedVersion
}

// commonOptionalParams are accepted by every catalog entry: "name" steers the
// agent.Target's Name field. "address" is added per-entry below (required for
// the TLS collector, optional elsewhere) so it never appears twice in one
// entry's Params list.
var commonOptionalParams = []ParamRule{
	{Name: "name"},
}

// DefaultCatalog is the Phase 1 approved catalog. Every entry maps onto a
// collector already registered by collectors.Register (see
// agents-go/internal/collectors/catalog.go) — nothing here is a new
// collection capability, only an approved, versioned way to invoke one.
func DefaultCatalog() []ApprovedCollector {
	return []ApprovedCollector{
		{
			ID:         "os-baseline",
			Version:    "v1",
			AgentType:  "os",
			Technology: "linux",
			Params: append(append([]ParamRule{}, commonOptionalParams...),
				ParamRule{Name: "address"},
				ParamRule{Name: "osReleasePath"},
				ParamRule{Name: "sshdConfigPath"},
				ParamRule{Name: "auditStatusPath"},
			),
		},
		{
			ID:        "database-hardening",
			Version:   "v1",
			AgentType: "database",
			Params: append(append([]ParamRule{}, commonOptionalParams...),
				ParamRule{Name: "address"},
				ParamRule{Name: "technology", Required: true, AllowedValues: []string{"postgresql", "mysql"}},
				ParamRule{Name: "snapshotPath", Required: true},
			),
		},
		{
			ID:        "middleware-hardening",
			Version:   "v1",
			AgentType: "middleware",
			Params: append(append([]ParamRule{}, commonOptionalParams...),
				ParamRule{Name: "address"},
				ParamRule{Name: "technology", Required: true, AllowedValues: []string{"nginx", "tomcat"}},
				ParamRule{Name: "configPath", Required: true},
			),
		},
		{
			ID:         "tls-posture",
			Version:    "v1",
			AgentType:  "tls",
			Technology: "tls",
			Params: append(append([]ParamRule{}, commonOptionalParams...),
				ParamRule{Name: "address", Required: true},
			),
		},
		{
			ID:         "governance-sdlc",
			Version:    "v1",
			AgentType:  "governance",
			Technology: "governance",
			Params: append(append([]ParamRule{}, commonOptionalParams...),
				ParamRule{Name: "address"},
				ParamRule{Name: "governancePath", Required: true},
			),
		},
	}
}
