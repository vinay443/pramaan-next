// Command agent runs the Pramaan Next Phase 1 collectors.
//
// Subcommands:
//
//	agent describe                 print the agent registration document (JSON)
//	agent checks                   list the predefined technical checks
//	agent run [-targets f.json]    collect from targets and submit evidence
//	                               [-submit] force real submission (overrides dry-run)
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

func printJSON(v any) {
	b, _ := json.MarshalIndent(v, "", "  ")
	fmt.Println(string(b))
}

func usage() {
	fmt.Fprintln(os.Stderr, "usage: agent <describe|checks|run> [flags]")
}
