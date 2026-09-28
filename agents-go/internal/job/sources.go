package job

import (
	"encoding/json"
	"errors"
	"fmt"
	"os"
)

// Source is one approved evidence-collection target, keyed by SourceID. A
// JobRequest never carries connection details itself — it references a
// SourceID that must already be present in the approved source registry.
type Source struct {
	SourceID    string `json:"sourceId"`
	Environment string `json:"environment"`
	// Technology, if the referenced collector has a fixed Technology, must
	// match it; for multi-technology collectors (database, middleware) it
	// must match the job's "technology" parameter.
	Technology string `json:"technology"`
	Address    string `json:"address"`
	// Available lets the fixture registry simulate a source that is known
	// but currently unreachable, distinct from a source that doesn't exist.
	Available bool `json:"available"`
}

// ErrSourceNotFound means the SourceID is not in the approved registry at
// all — this is a caller error (job.ErrInvalidSource), not an infrastructure
// problem.
var ErrSourceNotFound = errors.New("source not found in approved registry")

// SourceStore resolves an approved SourceID to its connection record.
type SourceStore interface {
	Lookup(sourceID string) (Source, error)
}

// StaticSourceStore is a fixed, in-memory approved-source registry, loaded
// once at startup from a file or from built-in simulation fixtures. It never
// accepts a source it wasn't seeded with — there is no way for a job request
// to register or invent a source at runtime.
type StaticSourceStore struct {
	byID map[string]Source
}

// NewStaticSourceStore builds a store from a fixed list of sources.
func NewStaticSourceStore(sources []Source) *StaticSourceStore {
	s := &StaticSourceStore{byID: map[string]Source{}}
	for _, src := range sources {
		s.byID[src.SourceID] = src
	}
	return s
}

// Lookup implements SourceStore.
func (s *StaticSourceStore) Lookup(sourceID string) (Source, error) {
	src, ok := s.byID[sourceID]
	if !ok {
		return Source{}, ErrSourceNotFound
	}
	return src, nil
}

// LoadSourceStoreFromFile reads a JSON array of Source records. A read or
// parse failure is returned as-is (the executor maps it to
// SOURCE_UNAVAILABLE — the registry itself is the thing that's broken, not
// any specific source reference).
func LoadSourceStoreFromFile(path string) (*StaticSourceStore, error) {
	data, err := os.ReadFile(path)
	if err != nil {
		return nil, fmt.Errorf("read source registry %s: %w", path, err)
	}
	var sources []Source
	if err := json.Unmarshal(data, &sources); err != nil {
		return nil, fmt.Errorf("parse source registry %s: %w", path, err)
	}
	return NewStaticSourceStore(sources), nil
}

// SimulationSources is a deterministic, offline fixture registry used when
// SIMULATION_MODE is on and no real source registry file is configured. It
// intentionally includes one source marked unavailable, so the
// source-unavailable path can be exercised without any real infrastructure.
func SimulationSources() []Source {
	return []Source{
		{SourceID: "sim-os-host-1", Environment: "dev", Technology: "linux", Address: "sim-os-host-1", Available: true},
		{SourceID: "sim-db-pg-1", Environment: "dev", Technology: "postgresql", Address: "sim-db-pg-1:5432", Available: true},
		{SourceID: "sim-db-mysql-1", Environment: "dev", Technology: "mysql", Address: "sim-db-mysql-1:3306", Available: true},
		{SourceID: "sim-mw-nginx-1", Environment: "dev", Technology: "nginx", Address: "sim-mw-nginx-1", Available: true},
		{SourceID: "sim-mw-tomcat-1", Environment: "dev", Technology: "tomcat", Address: "sim-mw-tomcat-1", Available: true},
		{SourceID: "sim-tls-endpoint-1", Environment: "dev", Technology: "tls", Address: "sim-tls-endpoint-1:443", Available: true},
		{SourceID: "sim-governance-1", Environment: "dev", Technology: "governance", Address: "sim-governance-1", Available: true},
		{SourceID: "sim-db-pg-down", Environment: "dev", Technology: "postgresql", Address: "sim-db-pg-down:5432", Available: false},
	}
}
