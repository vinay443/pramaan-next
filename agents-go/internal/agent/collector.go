// Package agent defines the collector contract, the in-process agent registry,
// the predefined technical-check framework, and the run/submit pipeline.
//
// It contains NO GRC business rules, NO scheduler business logic, and NO API
// server. Collectors gather technical signals; checks turn those signals into
// deterministic PASS/FAIL findings; the runner packages findings as evidence and
// hands them to the submission client.
package agent

import (
	"context"
	"time"
)

// Status values for a check finding.
const (
	StatusPass          = "PASS"
	StatusFail          = "FAIL"
	StatusNotApplicable = "NOT_APPLICABLE"
	StatusError         = "ERROR"
)

// Target describes something to collect from.
type Target struct {
	Kind        string            `json:"kind"`        // os | database | middleware | tls
	Name        string            `json:"name"`        // logical target name
	Application string            `json:"application"` // application slug (backend contract)
	Technology  string            `json:"technology"`  // linux | postgresql | nginx | tls ...
	Address     string            `json:"address"`     // host or host:port (tls/db)
	Params      map[string]string `json:"params,omitempty"`
}

// Param returns a target parameter or "".
func (t Target) Param(key string) string {
	if t.Params == nil {
		return ""
	}
	return t.Params[key]
}

// Facts is the normalized set of technical signals a collector extracts.
type Facts map[string]string

// Get returns a fact value or "".
func (f Facts) Get(key string) string { return f[key] }

// Finding is the outcome of one predefined technical check.
type Finding struct {
	CheckID   string `json:"checkId"`
	ControlID string `json:"controlId"`
	Framework string `json:"framework"`
	Title     string `json:"title"`
	Status    string `json:"status"`
	Observed  string `json:"observed,omitempty"`
	Expected  string `json:"expected,omitempty"`
	Detail    string `json:"detail,omitempty"`
}

// Evidence is one submittable artifact produced by a collector.
type Evidence struct {
	Application    string
	ControlID      string
	Framework      string
	SourceSystem   string
	SourceObjectID string
	Title          string
	ContentType    string
	Content        []byte
	CollectedAt    time.Time
	Metadata       map[string]string
	Tags           map[string]string
}

// Result is what a collector returns for one target.
type Result struct {
	Target    Target     `json:"target"`
	Simulated bool       `json:"simulated"`
	Facts     Facts      `json:"facts"`
	Findings  []Finding  `json:"findings"`
	Evidence  []Evidence `json:"-"`
	StartedAt time.Time  `json:"startedAt"`
	EndedAt   time.Time  `json:"endedAt"`
	Err       string     `json:"error,omitempty"`
}

// CollectionContext is passed to every Collect call.
type CollectionContext struct {
	Ctx        context.Context
	Simulation bool
	Now        func() time.Time
}

// Time returns the run timestamp (injected clock, or time.Now().UTC()).
func (cc CollectionContext) Time() time.Time {
	if cc.Now != nil {
		return cc.Now()
	}
	return time.Now().UTC()
}

// Collector gathers signals from one class of technology.
type Collector interface {
	// Type is the collector kind, e.g. "os".
	Type() string
	// Technologies lists the technology keys this collector handles.
	Technologies() []string
	// Checks returns the predefined technical checks this collector runs.
	Checks() []Check
	// Supports reports whether this collector can handle the target.
	Supports(t Target) bool
	// Collect gathers facts, runs Checks, and builds Evidence.
	Collect(cc CollectionContext, t Target) Result
}
