// Command agent runs the Pramaan Next Phase 1 collectors.
//
// Subcommands:
//
//	agent describe                 print the agent registration document (JSON)
//	agent checks                   list the predefined technical checks
//	agent run [-targets f.json]    collect from targets and submit evidence
//	                               [-submit] force real submission (overrides dry-run)
//	agent job -request f.json      execute one approved, versioned evidence-pull
//	                               job (see internal/job) [-out f.json] [-submit]
//	                               [-sources f.json]
//
// SIMULATION_MODE=true makes collectors use synthetic sources and defaults
// submission to dry-run, so this works with no bank servers and no backend.
package main

import (
	"context"
	"encoding/json"
	"flag"
	"fmt"
	"os"
	"time"

	"github.com/pramaan/pramaan-next/agents-go/internal/agent"
	"github.com/pramaan/pramaan-next/agents-go/internal/collectors"
	"github.com/pramaan/pramaan-next/agents-go/internal/config"
	"github.com/pramaan/pramaan-next/agents-go/internal/evidence"
	"github.com/pramaan/pramaan-next/agents-go/internal/job"
)

func main() {
	if len(os.Args) < 2 {
		usage()
		os.Exit(2)
	}
	cfg := config.Load()
	reg := agent.NewRegistry(cfg.AgentID, cfg.AgentName, cfg.Environment, cfg.SimulationMode)
	collectors.Register(reg)

	switch os.Args[1] {
	case "describe":
		printJSON(reg.Describe(time.Now().UTC()))
	case "checks":
		// Shape mirrors contract CheckDefView (contracts/openapi/pramaan-backend.yaml).
		printJSON(reg.Describe(time.Now().UTC()).AllChecks())
	case "run":
		if err := runCmd(cfg, reg, os.Args[2:]); err != nil {
			fmt.Fprintln(os.Stderr, "error:", err)
			os.Exit(1)
		}
	case "job":
		if err := jobCmd(cfg, reg, os.Args[2:]); err != nil {
			fmt.Fprintln(os.Stderr, "error:", err)
			os.Exit(1)
		}
	default:
		usage()
		os.Exit(2)
	}
}

func runCmd(cfg config.Config, reg *agent.Registry, args []string) error {
	fs := flag.NewFlagSet("run", flag.ContinueOnError)
	targetFile := fs.String("targets", "", "path to a JSON array of targets")
	forceSubmit := fs.Bool("submit", false, "submit to the backend even if dry-run would be the default")
	if err := fs.Parse(args); err != nil {
		return err
	}

	var targets []agent.Target
	if *targetFile != "" {
		data, err := os.ReadFile(*targetFile)
		if err != nil {
			return err
		}
		if targets, err = collectors.ParseTargets(data); err != nil {
			return err
		}
	} else if cfg.SimulationMode {
		targets = collectors.SimulationTargets()
	} else {
		return fmt.Errorf("no -targets file given and SIMULATION_MODE is off")
	}

	dryRun := cfg.SubmitDryRun && !*forceSubmit
	client := evidence.NewClient(evidence.Options{
		BaseURL: cfg.BackendBaseURL,
		Timeout: cfg.HTTPTimeout,
		DryRun:  dryRun,
	})

	runner := &agent.Runner{
		Registry:    reg,
		Submitter:   client,
		CollectedBy: cfg.CollectedBy,
		Now:         func() time.Time { return time.Now().UTC() },
	}

	report, err := runner.Run(context.Background(), targets)
	printJSON(report)
	return err
}

// jobCmd runs one P01 scheduled-evidence-pull job request end to end: parse
// the request file, build an Executor wired to the same Registry as `run`,
// execute it, and always print a structured JobResult as JSON.
//
// The process exit code reflects the *CLI's* success at handling the
// request (0 = ran to completion and produced a JobResult, non-zero = a
// usage/IO problem before execution could even start, e.g. an unreadable
// request file). A rejected/failed/timed-out *job* is still a normal,
// successful CLI invocation — the JobResult.status field is how a caller
// (or a script) distinguishes SUCCEEDED from FAILED/REJECTED/TIMEOUT/CANCELLED.
func jobCmd(cfg config.Config, reg *agent.Registry, args []string) error {
	fs := flag.NewFlagSet("job", flag.ContinueOnError)
	requestFile := fs.String("request", "", "path to a JSON job request (required)")
	outFile := fs.String("out", "", "path to write the JSON job result (default: stdout only)")
	sourcesFile := fs.String("sources", "", "path to a JSON array of approved sources (default: cfg/env, or simulation fixtures)")
	forceSubmit := fs.Bool("submit", false, "submit collected evidence to the backend instead of collect-only mode")
	if err := fs.Parse(args); err != nil {
		return err
	}
	if *requestFile == "" {
		return fmt.Errorf("-request is required")
	}

	data, err := os.ReadFile(*requestFile)
	if err != nil {
		return fmt.Errorf("read request file: %w", err)
	}
	req, err := job.DecodeRequest(data)
	if err != nil {
		// Still a structured outcome, not a CLI failure: print a REJECTED
		// JobResult with whatever job ID/correlation ID could be salvaged
		// (likely empty) rather than only an opaque parse error.
		result := job.JobResult{
			Status: job.StatusRejected,
			Error:  &job.JobError{Code: job.ErrMalformedRequest, Message: err.Error()},
		}
		printJSON(result)
		if *outFile != "" {
			_ = writeJSONFile(*outFile, result)
		}
		os.Exit(1)
	}

	sourcesPath := *sourcesFile
	if sourcesPath == "" {
		sourcesPath = cfg.JobSourcesPath
	}
	var sources job.SourceStore
	if sourcesPath != "" {
		store, err := job.LoadSourceStoreFromFile(sourcesPath)
		if err != nil {
			return fmt.Errorf("load source registry: %w", err)
		}
		sources = store
	} else if cfg.SimulationMode {
		sources = job.NewStaticSourceStore(job.SimulationSources())
	} else {
		return fmt.Errorf("no -sources file given and SIMULATION_MODE is off")
	}

	idemStore, err := job.NewFileStore(cfg.JobIdempotencyDir)
	if err != nil {
		return fmt.Errorf("open idempotency store: %w", err)
	}

	var submitter agent.Submitter
	if *forceSubmit {
		submitter = evidence.NewClient(evidence.Options{
			BaseURL: cfg.BackendBaseURL,
			Timeout: cfg.HTTPTimeout,
			DryRun:  cfg.SubmitDryRun,
		})
	}

	exec := &job.Executor{
		Catalog:     job.NewCatalog(job.DefaultCatalog()),
		Sources:     sources,
		Registry:    reg,
		Idempotency: idemStore,
		Submitter:   submitter,
		CollectedBy: cfg.CollectedBy,
		Config: job.Config{
			FeatureEnabled:       cfg.JobPullEnabled,
			DefaultTimeout:       cfg.JobDefaultTimeout,
			MaxTimeout:           cfg.JobMaxTimeout,
			GlobalMaxOutputBytes: cfg.JobMaxOutputBytes,
		},
		Now: func() time.Time { return time.Now().UTC() },
	}

	result := exec.Execute(context.Background(), req)
	printJSON(result)
	if *outFile != "" {
		if err := writeJSONFile(*outFile, result); err != nil {
			return fmt.Errorf("write result file: %w", err)
		}
	}
	if result.Status != job.StatusSucceeded {
		os.Exit(1)
	}
	return nil
}

func writeJSONFile(path string, v any) error {
	b, err := json.MarshalIndent(v, "", "  ")
	if err != nil {
		return err
	}
	return os.WriteFile(path, b, 0o644)
}

func printJSON(v any) {
	b, _ := json.MarshalIndent(v, "", "  ")
	fmt.Println(string(b))
}

func usage() {
	fmt.Fprintln(os.Stderr, "usage: agent <describe|checks|run|job> [flags]")
}
