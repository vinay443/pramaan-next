package agent

import (
	"context"
	"encoding/base64"
	"testing"
	"time"

	"github.com/pramaan/pramaan-next/agents-go/internal/evidence"
)

type captureSubmitter struct {
	got []evidence.IngestRequest
}

func (c *captureSubmitter) Submit(_ context.Context, reqs []evidence.IngestRequest) (evidence.BulkIngestResponse, error) {
	c.got = append(c.got, reqs...)
	return evidence.BulkIngestResponse{Received: len(reqs), Created: len(reqs)}, nil
}

type stubCollector struct{}

func (stubCollector) Type() string           { return "stub" }
func (stubCollector) Technologies() []string { return []string{"stub"} }
func (stubCollector) Checks() []Check        { return nil }
func (stubCollector) Supports(t Target) bool { return t.Kind == "stub" }
func (stubCollector) Collect(cc CollectionContext, t Target) Result {
	now := cc.Now()
	return Result{
		Target:   t,
		Findings: []Finding{{CheckID: "S-1", Status: StatusPass}, {CheckID: "S-2", Status: StatusFail}},
		Evidence: []Evidence{{
			Application:  t.Application,
			ControlID:    "C-1",
			Framework:    "ITPP",
			SourceSystem: "AGENT_STUB",
			Content:      []byte(`{"ok":true}`),
			ContentType:  "application/json",
			CollectedAt:  now,
		}},
	}
}

func TestRunnerMapsEvidenceToContractAndSubmits(t *testing.T) {
	reg := NewRegistry("agent-x", "host", "dev", true)
	reg.Register(stubCollector{})
	sub := &captureSubmitter{}
	fixed := time.Date(2026, 9, 1, 12, 0, 0, 0, time.UTC)

	r := &Runner{Registry: reg, Submitter: sub, CollectedBy: "pramaan-agent",
		Now: func() time.Time { return fixed }}

	rep, err := r.Run(context.Background(), []Target{
		{Kind: "stub", Name: "t1", Application: "payments"},
		{Kind: "unknown", Name: "t2"},
	})
	if err != nil {
		t.Fatal(err)
	}
	if rep.Submitted != 1 || len(sub.got) != 1 {
		t.Fatalf("want 1 submitted, got %d / %d", rep.Submitted, len(sub.got))
	}
	if len(rep.Unmatched) != 1 || rep.Unmatched[0] != "t2" {
		t.Fatalf("unmatched not recorded: %+v", rep.Unmatched)
	}
	if rep.FindingStats[StatusPass] != 1 || rep.FindingStats[StatusFail] != 1 {
		t.Fatalf("finding stats wrong: %+v", rep.FindingStats)
	}

	req := sub.got[0]
	if req.ApplicationSlug != "payments" || req.ControlID != "C-1" || req.Framework != "ITPP" {
		t.Fatalf("contract fields wrong: %+v", req)
	}
	if req.CollectedAt != "2026-09-01T12:00:00Z" {
		t.Fatalf("collectedAt not RFC3339 UTC: %q", req.CollectedAt)
	}
	raw, decErr := base64.StdEncoding.DecodeString(req.ContentBase64)
	if decErr != nil || string(raw) != `{"ok":true}` {
		t.Fatalf("contentBase64 round-trip failed: %v %q", decErr, raw)
	}
	if rep.Backend == nil || rep.Backend.Received != 1 {
		t.Fatalf("backend response not captured: %+v", rep.Backend)
	}
}
