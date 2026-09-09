// Package config loads agent runtime configuration from the environment.
//
// SIMULATION_MODE lets the agents run end-to-end with no bank servers and no
// backend: collectors emit deterministic synthetic evidence and the submission
// client defaults to dry-run.
package config

import (
	"os"
	"strconv"
	"strings"
	"time"
)

// Config is the resolved agent configuration.
type Config struct {
	// SimulationMode makes every collector use deterministic synthetic sources.
	SimulationMode bool
	// BackendBaseURL is the Pramaan backend (Teammate 1's Spring service).
	BackendBaseURL string
	// SubmitDryRun, when true, makes the evidence client log instead of POSTing.
	SubmitDryRun bool
	// AgentID / AgentName identify this agent instance during registration.
	AgentID     string
	AgentName   string
	Environment string
	// CollectedBy is stamped onto submitted evidence.
	CollectedBy string
	HTTPTimeout time.Duration
}

// Load reads configuration from environment variables, applying Phase 1 defaults.
//
//	SIMULATION_MODE          bool   (default false)
//	PRAMAAN_BACKEND_URL      string (default http://localhost:8080)
//	AGENT_SUBMIT_DRY_RUN     bool   (default: true when SIMULATION_MODE, else false)
//	AGENT_ID                 string (default "agent-local")
//	AGENT_NAME               string (default hostname or AGENT_ID)
//	AGENT_ENV                string (default "dev")
//	AGENT_COLLECTED_BY       string (default "pramaan-agent")
//	AGENT_HTTP_TIMEOUT       duration (default 15s)
func Load() Config {
	sim := boolEnv("SIMULATION_MODE", false)

	cfg := Config{
		SimulationMode: sim,
		BackendBaseURL: strEnv("PRAMAAN_BACKEND_URL", "http://localhost:8080"),
		SubmitDryRun:   boolEnv("AGENT_SUBMIT_DRY_RUN", sim),
		AgentID:        strEnv("AGENT_ID", "agent-local"),
		Environment:    strEnv("AGENT_ENV", "dev"),
		CollectedBy:    strEnv("AGENT_COLLECTED_BY", "pramaan-agent"),
		HTTPTimeout:    durEnv("AGENT_HTTP_TIMEOUT", 15*time.Second),
	}
	cfg.AgentName = strEnv("AGENT_NAME", defaultName(cfg.AgentID))
	return cfg
}

func defaultName(fallback string) string {
	if h, err := os.Hostname(); err == nil && h != "" {
		return h
	}
	return fallback
}

func strEnv(key, def string) string {
	if v, ok := os.LookupEnv(key); ok && strings.TrimSpace(v) != "" {
		return strings.TrimSpace(v)
	}
	return def
}

func boolEnv(key string, def bool) bool {
	v, ok := os.LookupEnv(key)
	if !ok || strings.TrimSpace(v) == "" {
		return def
	}
	b, err := strconv.ParseBool(strings.TrimSpace(v))
	if err != nil {
		return def
	}
	return b
}

func durEnv(key string, def time.Duration) time.Duration {
	v, ok := os.LookupEnv(key)
	if !ok {
		return def
	}
	d, err := time.ParseDuration(strings.TrimSpace(v))
	if err != nil || d <= 0 {
		return def
	}
	return d
}
