package agent

import (
	"testing"
	"time"
)

type fakeCollector struct {
	typ  string
	tech []string
	kind string
}

func (f fakeCollector) Type() string           { return f.typ }
func (f fakeCollector) Technologies() []string { return f.tech }
func (f fakeCollector) Checks() []Check {
	return []Check{{ID: f.typ + "-01", ControlID: "C-1", Framework: "ITPP", Title: "t"}}
}
func (f fakeCollector) Supports(t Target) bool { return t.Kind == f.kind }
func (f fakeCollector) Collect(cc CollectionContext, t Target) Result {
	return Result{Target: t}
}

func TestRegistryRegisterMatchDescribe(t *testing.T) {
	reg := NewRegistry("agent-1", "host-a", "dev", true)
	reg.Register(fakeCollector{typ: "os", tech: []string{"linux"}, kind: "os"})
	reg.Register(fakeCollector{typ: "tls", tech: []string{"tls"}, kind: "tls"})

	if got := len(reg.Collectors()); got != 2 {
		t.Fatalf("want 2 collectors, got %d", got)
	}
	if _, ok := reg.Get("os"); !ok {
		t.Fatal("Get(os) should succeed")
	}
	c, ok := reg.Match(Target{Kind: "tls"})
	if !ok || c.Type() != "tls" {
		t.Fatalf("Match returned %v ok=%v", c, ok)
	}
	if _, ok := reg.Match(Target{Kind: "database"}); ok {
		t.Fatal("no collector should match database")
	}

	d := reg.Describe(time.Unix(0, 0).UTC())
	if d.AgentID != "agent-1" || !d.Simulation {
		t.Fatalf("bad descriptor: %+v", d)
	}
	if d.TotalChecks != 2 {
		t.Fatalf("want 2 checks advertised, got %d", d.TotalChecks)
	}

	// Advertised checks carry the contract CheckDef fields (collector + technology).
	all := d.AllChecks()
	if len(all) != 2 {
		t.Fatalf("want 2 flattened checks, got %d", len(all))
	}
	for _, ck := range all {
		if ck.Collector == "" || ck.ControlID == "" || ck.Framework == "" {
			t.Fatalf("check missing contract fields: %+v", ck)
		}
	}
	if all[0].Collector != "os" || all[0].Technology != "linux" {
		t.Fatalf("want os/linux, got %s/%s", all[0].Collector, all[0].Technology)
	}
}

func TestRegisterDuplicatePanics(t *testing.T) {
	defer func() {
		if recover() == nil {
			t.Fatal("expected panic on duplicate collector type")
		}
	}()
	reg := NewRegistry("a", "b", "dev", false)
	reg.Register(fakeCollector{typ: "os", kind: "os"})
	reg.Register(fakeCollector{typ: "os", kind: "os"})
}
