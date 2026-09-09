package collectors

import (
	"testing"

	"github.com/pramaan/pramaan-next/agents-go/internal/agent"
)

func TestDatabaseCollectorMySQLSimulation(t *testing.T) {
	res := NewDatabaseCollector().Collect(simCtx(), agent.Target{
		Kind: "database", Name: "my-1", Application: "payments", Technology: "mysql"})
	if res.Err != "" {
		t.Fatalf("unexpected error: %s", res.Err)
	}
	for _, id := range []string{"DB-MY-01", "DB-MY-02", "DB-MY-03"} {
		if statusOf(res.Findings, id) != agent.StatusPass {
			t.Fatalf("%s should PASS in simulation: %+v", id, res.Findings)
		}
	}
	// Postgres checks must be NOT_APPLICABLE for a mysql target.
	if statusOf(res.Findings, "DB-PG-01") != agent.StatusNotApplicable {
		t.Fatalf("DB-PG-01 should be N/A for mysql: %+v", res.Findings)
	}
	if len(res.Evidence) == 0 {
		t.Fatal("expected evidence bundles for mysql target")
	}
	for _, ev := range res.Evidence {
		if ev.Tags["technology"] != "mysql" {
			t.Fatalf("evidence technology tag should be mysql, got %q", ev.Tags["technology"])
		}
	}
}

func TestDatabaseCollectorPostgresStillPasses(t *testing.T) {
	res := NewDatabaseCollector().Collect(simCtx(), agent.Target{
		Kind: "database", Name: "pg-1", Application: "payments", Technology: "postgresql"})
	for _, id := range []string{"DB-PG-01", "DB-PG-02", "DB-PG-03", "DB-PG-04"} {
		if statusOf(res.Findings, id) != agent.StatusPass {
			t.Fatalf("%s should PASS: %+v", id, res.Findings)
		}
	}
	if statusOf(res.Findings, "DB-MY-01") != agent.StatusNotApplicable {
		t.Fatalf("DB-MY-01 should be N/A for postgres: %+v", res.Findings)
	}
}
