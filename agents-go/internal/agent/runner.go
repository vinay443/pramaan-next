package agent

import (
	"context"
	"encoding/base64"
	"encoding/json"
	"fmt"
	"time"

	"github.com/pramaan/pramaan-next/agents-go/internal/evidence"
)

// Submitter sends evidence to the backend. *evidence.Client satisfies it.
type Submitter interface {
	Submit(ctx context.Context, reqs []evidence.IngestRequest) (evidence.BulkIngestResponse, error)
}

// Runner executes matched collectors against targets and submits their evidence.
type Runner struct {
	Registry    *Registry
	Submitter   Submitter
	CollectedBy string
	Now         func() time.Time
}

// RunReport summarizes one run.
type RunReport struct {
	AgentID      string                       `json:"agentId"`
	Simulation   bool                         `json:"simulation"`
	StartedAt    string                       `json:"startedAt"`
	EndedAt      string                       `json:"endedAt"`
	Targets      int                          `json:"targets"`
	Unmatched    []string                     `json:"unmatched,omitempty"`
	Results      []Result                     `json:"results"`
	FindingStats map[string]int               `json:"findingStats"`
	Submitted    int                          `json:"submitted"`
	Backend      *evidence.BulkIngestResponse `json:"backend,omitempty"`
}

func (r *Runner) now() time.Time {
	if r.Now != nil {
		return r.Now()
	}
	return time.Now().UTC()
}

// Run collects from every target and submits the resulting evidence in one bulk call.
func (r *Runner) Run(ctx context.Context, targets []Target) (RunReport, error) {
	start := r.now()
	rep := RunReport{
		AgentID:      r.Registry.ID,
		Simulation:   r.Registry.Simulation,
		StartedAt:    start.Format(time.RFC3339),
		Targets:      len(targets),
		FindingStats: map[string]int{StatusPass: 0, StatusFail: 0, StatusNotApplicable: 0, StatusError: 0},
	}

	cc := CollectionContext{Ctx: ctx, Simulation: r.Registry.Simulation, Now: r.now}
	var toSubmit []evidence.IngestRequest

	for _, t := range targets {
		c, ok := r.Registry.Match(t)
		if !ok {
			rep.Unmatched = append(rep.Unmatched, t.Name)
			continue
		}
		res := c.Collect(cc, t)
		for k, v := range Summarize(res.Findings) {
			rep.FindingStats[k] += v
		}
		for _, ev := range res.Evidence {
			toSubmit = append(toSubmit, r.toIngestRequest(ev))
		}
		res.Evidence = nil // keep the report lean; content already mapped
		rep.Results = append(rep.Results, res)
	}

	rep.Submitted = len(toSubmit)
	if len(toSubmit) > 0 && r.Submitter != nil {
		resp, err := r.Submitter.Submit(ctx, toSubmit)
		if err != nil {
			rep.EndedAt = r.now().Format(time.RFC3339)
			return rep, fmt.Errorf("submit evidence: %w", err)
		}
		rep.Backend = &resp
	}

	rep.EndedAt = r.now().Format(time.RFC3339)
	return rep, nil
}

// toIngestRequest maps collector Evidence onto the backend contract shape.
func (r *Runner) toIngestRequest(ev Evidence) evidence.IngestRequest {
	req := evidence.IngestRequest{
		ApplicationSlug: ev.Application,
		ControlID:       ev.ControlID,
		Framework:       ev.Framework,
		SourceSystem:    ev.SourceSystem,
		SourceObjectID:  ev.SourceObjectID,
		Title:           ev.Title,
		ContentType:     ev.ContentType,
		ContentBase64:   base64.StdEncoding.EncodeToString(ev.Content),
		CollectedBy:     r.CollectedBy,
		Metadata:        ev.Metadata,
		Tags:            ev.Tags,
	}
	if !ev.CollectedAt.IsZero() {
		req.CollectedAt = ev.CollectedAt.UTC().Format(time.RFC3339)
	}
	return req
}

// EvidencePayload is the canonical JSON body a collector stores as evidence content.
type EvidencePayload struct {
	Collector   string    `json:"collector"`
	Technology  string    `json:"technology"`
	Target      string    `json:"target"`
	Simulated   bool      `json:"simulated"`
	CollectedAt string    `json:"collectedAt"`
	Facts       Facts     `json:"facts"`
	Findings    []Finding `json:"findings"`
}

// MarshalPayload renders an EvidencePayload as indented JSON.
func MarshalPayload(p EvidencePayload) []byte {
	b, _ := json.MarshalIndent(p, "", "  ")
	return b
}
