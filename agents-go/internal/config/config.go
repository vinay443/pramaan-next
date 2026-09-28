// Package config loads agent runtime configuration from the environment.
//
// SIMULATION_MODE lets the agents run end-to-end with no bank servers and no
// backend: collectors emit deterministic synthetic evidence and the submission
// client defaults to dry-run.
package config

import (
	"os"
	"path/filepath"
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

	// JobPullEnabled is the feature flag gating the scheduled evidence-pull
	// job pipeline (internal/job). When false, every job request is rejected
	// before any validation, collection, or submission occurs.
	JobPullEnabled bool
	// JobDefaultTimeout is used when a job request omits timeoutSeconds.
	JobDefaultTimeout time.Duration
	// JobMaxTimeout caps the timeout a job request may ask for.
	JobMaxTimeout time.Duration
	// JobMaxOutputBytes bounds total evidence content size for one job run,
	// used when the approved collector definition doesn't set its own limit.
	JobMaxOutputBytes int
	// JobIdempotencyDir stores completed job results, keyed by job ID, so a
	// retried submission with the same job ID replays the prior result
	// instead of re-running the collector.
	JobIdempotencyDir string
	// JobSourcesPath points at a JSON file of approved source records. Empty
	// means: use the built-in simulation sources (valid only when
	// SimulationMode is also true).
	JobSourcesPath string
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
//	AGENT_JOB_PULL_ENABLED   bool   (default true)
//	AGENT_JOB_DEFAULT_TIMEOUT duration (default 15s)
//	AGENT_JOB_MAX_TIMEOUT    duration (default 60s)
//	AGENT_JOB_MAX_OUTPUT_BYTES int  (default 1048576)
//	AGENT_JOB_IDEMPOTENCY_DIR string (default <TMP>/pramaan-agent-jobs)
//	AGENT_JOB_SOURCES_PATH   string (default "": simulation sources only)
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

		JobPullEnabled:    boolEnv("AGENT_JOB_PULL_ENABLED", true),
		JobDefaultTimeout: durEnv("AGENT_JOB_DEFAULT_TIMEOUT", 15*time.Second),
		JobMaxTimeout:     durEnv("AGENT_JOB_MAX_TIMEOUT", 60*time.Second),
		JobMaxOutputBytes: intEnv("AGENT_JOB_MAX_OUTPUT_BYTES", 1<<20),
		JobIdempotencyDir: strEnv("AGENT_JOB_IDEMPOTENCY_DIR", filepath.Join(os.TempDir(), "pramaan-agent-jobs")),
		JobSourcesPath:    strEnv("AGENT_JOB_SOURCES_PATH", ""),
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

func intEnv(key string, def int) int {
	v, ok := os.LookupEnv(key)
	if !ok || strings.TrimSpace(v) == "" {
		return def
	}
	n, err := strconv.Atoi(strings.TrimSpace(v))
	if err != nil || n <= 0 {
		return def
	}
	return n
}
