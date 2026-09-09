package config

import "testing"

func TestSimulationModeDefaultsSubmitToDryRun(t *testing.T) {
	t.Setenv("SIMULATION_MODE", "true")
	cfg := Load()
	if !cfg.SimulationMode {
		t.Fatal("SimulationMode should be true")
	}
	if !cfg.SubmitDryRun {
		t.Fatal("SubmitDryRun should default to true under SIMULATION_MODE")
	}
}

func TestExplicitDryRunOverridesSimulationDefault(t *testing.T) {
	t.Setenv("SIMULATION_MODE", "true")
	t.Setenv("AGENT_SUBMIT_DRY_RUN", "false")
	cfg := Load()
	if cfg.SubmitDryRun {
		t.Fatal("explicit AGENT_SUBMIT_DRY_RUN=false must win")
	}
}

func TestDefaultsWithoutSimulation(t *testing.T) {
	t.Setenv("SIMULATION_MODE", "")
	cfg := Load()
	if cfg.SimulationMode || cfg.SubmitDryRun {
		t.Fatalf("expected non-sim defaults, got %+v", cfg)
	}
	if cfg.BackendBaseURL != "http://localhost:8080" {
		t.Fatalf("unexpected backend url %q", cfg.BackendBaseURL)
	}
	if cfg.HTTPTimeout <= 0 {
		t.Fatal("timeout must be positive")
	}
}
