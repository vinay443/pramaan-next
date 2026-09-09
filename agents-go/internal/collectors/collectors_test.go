package collectors

import (
	"context"
	"encoding/json"
	"testing"
	"time"

	"github.com/pramaan/pramaan-next/agents-go/internal/agent"
)

func simCtx() agent.CollectionContext {
	fixed := time.Date(2026, 9, 1, 0, 0, 0, 0, time.UTC)
	return agent.CollectionContext{Ctx: context.Background(), Simulation: true,
		Now: func() time.Time { return fixed }}
}

func statusOf(findings []agent.Finding, checkID string) string {
	for _, f := range findings {
		if f.CheckID == checkID {
			return f.Status
		}
	}
	return "MISSING"
}

func TestOSCollectorSimulationAllPass(t *testing.T) {
	res := NewOSCollector().Collect(simCtx(), agent.Target{
		Kind: "os", Name: "app-1", Application: "net-banking", Technology: "linux"})
	if res.Err != "" {
		t.Fatalf("unexpected error: %s", res.Err)
	}
	sum := agent.Summarize(res.Findings)
	if sum[agent.StatusPass] != 4 {
		t.Fatalf("expected 4 PASS, got %+v", sum)
	}
	if len(res.Evidence) == 0 || res.Evidence[0].ContentType != "application/json" {
		t.Fatalf("evidence not produced correctly: %+v", res.Evidence)
	}
	// evidence content must be valid JSON payload
	var p agent.EvidencePayload
	if err := json.Unmarshal(res.Evidence[0].Content, &p); err != nil {
		t.Fatalf("evidence content not JSON: %v", err)
	}
	if p.Simulated != true {
		t.Fatal("payload should be marked simulated")
	}
}

func TestDatabaseCollectorSimulation(t *testing.T) {
	res := NewDatabaseCollector().Collect(simCtx(), agent.Target{
		Kind: "database", Name: "pg-1", Application: "payments", Technology: "postgresql"})
	if statusOf(res.Findings, "DB-PG-01") != agent.StatusPass {
		t.Fatalf("TLS check should pass: %+v", res.Findings)
	}
	if res.Facts.Get("hba_trust_rules") != "0" {
		t.Fatalf("expected 0 trust rules, got %q", res.Facts.Get("hba_trust_rules"))
	}
}

func TestMiddlewareCollectorNginxAndTomcat(t *testing.T) {
	nginx := NewMiddlewareCollector().Collect(simCtx(), agent.Target{
		Kind: "middleware", Name: "nginx-1", Application: "net-banking", Technology: "nginx"})
	if statusOf(nginx.Findings, "MW-01") != agent.StatusPass {
		t.Fatalf("nginx TLS check should pass: %+v", nginx.Findings)
	}
	if statusOf(nginx.Findings, "MW-04") != agent.StatusNotApplicable {
		t.Fatalf("autodeploy check should be N/A for nginx: %+v", nginx.Findings)
	}
	tomcat := NewMiddlewareCollector().Collect(simCtx(), agent.Target{
		Kind: "middleware", Name: "tc-1", Application: "payments", Technology: "tomcat"})
	if statusOf(tomcat.Findings, "MW-04") != agent.StatusPass {
		t.Fatalf("tomcat autodeploy check should pass: %+v", tomcat.Findings)
	}
}

func TestTLSCollectorSimulation(t *testing.T) {
	res := NewTLSCollector().Collect(simCtx(), agent.Target{
		Kind: "tls", Name: "edge", Application: "payments", Technology: "tls",
		Address: "payments.internal:443"})
	if res.Err != "" {
		t.Fatalf("sim TLS should not error: %s", res.Err)
	}
	if res.Facts.Get("tls_version") != "TLS1.3" {
		t.Fatalf("unexpected tls version: %q", res.Facts.Get("tls_version"))
	}
	for _, id := range []string{"TLS-01", "TLS-02", "TLS-03", "TLS-04"} {
		if statusOf(res.Findings, id) != agent.StatusPass {
			t.Fatalf("%s should pass: %+v", id, res.Findings)
		}
	}
}

func TestRealModeWithoutSourcesDegradesGracefully(t *testing.T) {
	cc := agent.CollectionContext{Ctx: context.Background(), Simulation: false,
		Now: func() time.Time { return time.Unix(0, 0).UTC() }}
	// database with no snapshot path -> error recorded, still returns findings, no panic
	res := NewDatabaseCollector().Collect(cc, agent.Target{Kind: "database", Name: "x", Application: "a"})
	if res.Err == "" {
		t.Fatal("expected an error note when no snapshot is available")
	}
	if len(res.Findings) != 7 {
		t.Fatalf("checks should still run, got %d findings", len(res.Findings))
	}
}

func TestCatalogRegistersAllCollectors(t *testing.T) {
	reg := agent.NewRegistry("a", "b", "dev", true)
	Register(reg)
	if len(reg.Collectors()) != 5 {
		t.Fatalf("expected 5 collectors, got %d", len(reg.Collectors()))
	}
	if len(AllChecks()) < 15 {
		t.Fatalf("expected the full check catalog, got %d", len(AllChecks()))
	}
	for _, tgt := range SimulationTargets() {
		if _, ok := reg.Match(tgt); !ok {
			t.Fatalf("no collector matched simulation target %s", tgt.Name)
		}
	}
}
