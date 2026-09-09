// Package collectors implements the Phase 1 technical collectors:
// server/OS, database, middleware, and TLS/certificate. Each runs a set of
// predefined technical checks and emits evidence in the backend contract shape.
//
// Every collector supports SIMULATION_MODE: with no real target reachable it
// reads deterministic synthetic sources so development needs no bank servers.
package collectors

import (
	"os"
	"sort"
	"strconv"
	"strings"
	"time"

	"github.com/pramaan/pramaan-next/agents-go/internal/agent"
)

// evidenceFor bundles facts + findings into one submittable Evidence artifact
// grouped by (framework, control). One collector run yields one Evidence per
// distinct control touched by its checks.
func evidenceFor(t agent.Target, collector, technology string, simulated bool,
	now time.Time, facts agent.Facts, findings []agent.Finding) []agent.Evidence {

	byControl := map[string][]agent.Finding{}
	order := []string{}
	for _, f := range findings {
		key := f.Framework + "|" + f.ControlID
		if _, seen := byControl[key]; !seen {
			order = append(order, key)
		}
		byControl[key] = append(byControl[key], f)
	}
	sort.Strings(order)

	out := make([]agent.Evidence, 0, len(order))
	for _, key := range order {
		parts := strings.SplitN(key, "|", 2)
		framework, control := parts[0], parts[1]
		fs := byControl[key]
		payload := agent.EvidencePayload{
			Collector:   collector,
			Technology:  technology,
			Target:      t.Name,
			Simulated:   simulated,
			CollectedAt: now.UTC().Format(time.RFC3339),
			Facts:       facts,
			Findings:    fs,
		}
		status := agent.StatusPass
		for _, f := range fs {
			if f.Status == agent.StatusFail || f.Status == agent.StatusError {
				status = f.Status
				break
			}
		}
		out = append(out, agent.Evidence{
			Application:    t.Application,
			ControlID:      control,
			Framework:      framework,
			SourceSystem:   sourceSystem(collector, technology),
			SourceObjectID: t.Name + "/" + control,
			Title:          strings.ToUpper(collector) + " check bundle for " + control,
			ContentType:    "application/json",
			Content:        agent.MarshalPayload(payload),
			CollectedAt:    now,
			Metadata: map[string]string{
				"collector":     collector,
				"technology":    technology,
				"target":        t.Name,
				"simulated":     boolStr(simulated),
				"bundle.status": status,
				"bundle.checks": strconv.Itoa(len(fs)),
			},
			Tags: map[string]string{
				"agent":      collector,
				"technology": technology,
			},
		})
	}
	return out
}

func sourceSystem(collector, technology string) string {
	c := strings.ToUpper(collector)
	tech := strings.ToUpper(strings.ReplaceAll(technology, "-", "_"))
	if tech == "" || tech == c {
		return "AGENT_" + c
	}
	return "AGENT_" + c + "_" + tech
}

func boolStr(b bool) string {
	if b {
		return "true"
	}
	return "false"
}

func numStr(n int) string { return strconv.Itoa(n) }

// readFileMaybe returns the file contents, or ("", false) if absent/unreadable.
func readFileMaybe(path string) (string, bool) {
	if path == "" {
		return "", false
	}
	b, err := os.ReadFile(path)
	if err != nil {
		return "", false
	}
	return string(b), true
}
