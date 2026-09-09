package collectors

import (
	"context"
	"encoding/json"
	"os"
	"testing"
	"time"

	"github.com/pramaan/pramaan-next/agents-go/internal/agent"
)

func TestGovernanceCollectorSimulationAllPass(t *testing.T) {
	res := NewGovernanceCollector().Collect(simCtx(), agent.Target{
		Kind: "governance", Name: "gov-1", Application: "net-banking", Technology: "governance"})
	if res.Err != "" {
		t.Fatalf("unexpected error: %s", res.Err)
	}
	sum := agent.Summarize(res.Findings)
	if sum[agent.StatusPass] != 3 {
		t.Fatalf("expected 3 PASS, got %+v (%+v)", sum, res.Findings)
	}
	for _, want := range []string{"GOV-01", "GOV-02", "GOV-03"} {
		if statusOf(res.Findings, want) != agent.StatusPass {
			t.Fatalf("%s should pass: %+v", want, res.Findings)
		}
	}
	// one evidence bundle per Phase 2 governance control, valid JSON payload
	if len(res.Evidence) != 3 {
		t.Fatalf("expected 3 evidence bundles, got %d", len(res.Evidence))
	}
	var p agent.EvidencePayload
	if err := json.Unmarshal(res.Evidence[0].Content, &p); err != nil {
		t.Fatalf("evidence content not JSON: %v", err)
	}
	if !p.Simulated {
		t.Fatal("payload should be marked simulated")
	}
}

func TestGovernanceCollectorMapsControlsToPhase2Catalog(t *testing.T) {
	got := map[string]string{}
	for _, ck := range NewGovernanceCollector().Checks() {
		got[ck.ControlID] = ck.Framework
	}
	want := map[string]string{
		"ITPP-CHG-02":  "ITPP",
		"DPSC-SDLC-04": "DPSC",
		"PCI-DSS-6.2":  "PCI_DSS",
	}
	for control, framework := range want {
		if got[control] != framework {
			t.Fatalf("control %s: want framework %s, got %q", control, framework, got[control])
		}
	}
}

func TestGovernanceCollectorFlagsUnapprovedChangeAndWeakGates(t *testing.T) {
	cc := agent.CollectionContext{Ctx: context.Background(), Simulation: false,
		Now: func() time.Time { return time.Unix(0, 0).UTC() }}
	// inline export via a temp file referenced by the target param
	dir := t.TempDir()
	path := dir + "/gov.json"
	body := `{
      "change_management": { "production_changes": 10, "changes_with_approved_ticket": 8 },
      "source_control": { "default_branch_protected": false, "required_approving_reviews": 0 },
      "pipeline": { "sast_enabled": true, "dependency_scan_enabled": false, "build_gate_blocking": true }
    }`
	if err := os.WriteFile(path, []byte(body), 0o600); err != nil {
		t.Fatal(err)
	}
	res := NewGovernanceCollector().Collect(cc, agent.Target{
		Kind: "governance", Name: "gov-2", Application: "payments", Technology: "governance",
		Params: map[string]string{"governancePath": path}})
	if res.Err != "" {
		t.Fatalf("unexpected error: %s", res.Err)
	}
	if statusOf(res.Findings, "GOV-01") != agent.StatusFail {
		t.Fatalf("GOV-01 should FAIL on unapproved changes: %+v", res.Findings)
	}
	if statusOf(res.Findings, "GOV-02") != agent.StatusFail {
		t.Fatalf("GOV-02 should FAIL when peer review is not enforced: %+v", res.Findings)
	}
	if statusOf(res.Findings, "GOV-03") != agent.StatusFail {
		t.Fatalf("GOV-03 should FAIL when a security gate is missing: %+v", res.Findings)
	}
	if res.Facts.Get("unapproved_changes") != "2" {
		t.Fatalf("expected 2 unapproved changes, got %q", res.Facts.Get("unapproved_changes"))
	}
}

func TestGovernanceCollectorRealModeWithoutSourceDegradesGracefully(t *testing.T) {
	cc := agent.CollectionContext{Ctx: context.Background(), Simulation: false,
		Now: func() time.Time { return time.Unix(0, 0).UTC() }}
	res := NewGovernanceCollector().Collect(cc, agent.Target{
		Kind: "governance", Name: "gov-3", Application: "x"})
	if res.Err == "" {
		t.Fatal("expected an error note when no governance export is available")
	}
	if len(res.Findings) != 3 {
		t.Fatalf("checks should still run, got %d findings", len(res.Findings))
	}
}
