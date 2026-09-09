package collectors

import (
	"encoding/json"

	"github.com/pramaan/pramaan-next/agents-go/internal/agent"
)

// GovernanceCollector gathers change-management and secure-SDLC governance
// signals — the Phase 2 expected controls that the technical (OS/DB/middleware/
// TLS) collectors do not cover: production change approval, enforced peer review,
// and secure-build gating.
//
// Phase 1 bundles no Jira/GitHub client: real mode reads a JSON governance export
// from the target param "governancePath" (an ops export combining change tickets +
// branch-protection settings + pipeline gate config). Simulation mode uses a
// synthetic snapshot.
type GovernanceCollector struct{}

func NewGovernanceCollector() *GovernanceCollector { return &GovernanceCollector{} }

func (c *GovernanceCollector) Type() string           { return "governance" }
func (c *GovernanceCollector) Technologies() []string { return []string{"governance"} }
func (c *GovernanceCollector) Supports(t agent.Target) bool {
	return t.Kind == "governance" || t.Technology == "governance"
}

func (c *GovernanceCollector) Checks() []agent.Check {
	return []agent.Check{
		{
			ID: "GOV-01", ControlID: "ITPP-CHG-02", Framework: "ITPP",
			Title:    "Production changes have an approved change ticket",
			Expected: "unapproved_changes=0",
			Eval: func(f agent.Facts) (string, string, string) {
				return agent.Expect("0", f.Get("unapproved_changes"))
			},
		},
		{
			ID: "GOV-02", ControlID: "DPSC-SDLC-04", Framework: "DPSC",
			Title:    "Peer review enforced on code changes",
			Expected: "protected default branch requiring >= 1 approving review",
			Eval: func(f agent.Facts) (string, string, string) {
				return agent.Expect("true", f.Get("peer_review_enforced"))
			},
		},
		{
			ID: "GOV-03", ControlID: "PCI-DSS-6.2", Framework: "PCI_DSS",
			Title:    "Bespoke software developed securely (SAST + dependency scan gate)",
			Expected: "secure_build_gates=true",
			Eval: func(f agent.Facts) (string, string, string) {
				return agent.Expect("true", f.Get("secure_build_gates"))
			},
		},
	}
}

type governanceSnapshot struct {
	ChangeManagement struct {
		WindowDays                int `json:"window_days"`
		ProductionChanges         int `json:"production_changes"`
		ChangesWithApprovedTicket int `json:"changes_with_approved_ticket"`
		EmergencyChanges          int `json:"emergency_changes"`
		EmergencyChangesReviewed  int `json:"emergency_changes_reviewed"`
	} `json:"change_management"`
	SourceControl struct {
		DefaultBranchProtected   bool `json:"default_branch_protected"`
		RequiredApprovingReviews int  `json:"required_approving_reviews"`
		DismissStaleReviews      bool `json:"dismiss_stale_reviews"`
		RequireCodeOwnerReview   bool `json:"require_code_owner_review"`
	} `json:"source_control"`
	Pipeline struct {
		SASTEnabled           bool `json:"sast_enabled"`
		DependencyScanEnabled bool `json:"dependency_scan_enabled"`
		SecretScanEnabled     bool `json:"secret_scan_enabled"`
		BuildGateBlocking     bool `json:"build_gate_blocking"`
	} `json:"pipeline"`
}

func (c *GovernanceCollector) Collect(cc agent.CollectionContext, t agent.Target) agent.Result {
	now := cc.Time()
	res := agent.Result{Target: t, Simulated: cc.Simulation, StartedAt: now}

	raw := simGovernanceSnapshot
	if !cc.Simulation {
		raw = firstReadable(t.Param("governancePath"))
		if raw == "" {
			res.Err = "no governance export available; run with SIMULATION_MODE or set governancePath param"
		}
	}

	facts := agent.Facts{"technology": "governance"}
	var snap governanceSnapshot
	if err := json.Unmarshal([]byte(raw), &snap); err != nil {
		res.Err = "governance export is not valid JSON: " + err.Error()
	} else {
		cm := snap.ChangeManagement
		unapproved := cm.ProductionChanges - cm.ChangesWithApprovedTicket
		if unapproved < 0 {
			unapproved = 0
		}
		facts["window_days"] = numStr(cm.WindowDays)
		facts["production_changes"] = numStr(cm.ProductionChanges)
		facts["unapproved_changes"] = numStr(unapproved)
		facts["unreviewed_emergency_changes"] =
			numStr(max0(cm.EmergencyChanges - cm.EmergencyChangesReviewed))

		sc := snap.SourceControl
		facts["default_branch_protected"] = boolStr(sc.DefaultBranchProtected)
		facts["required_approving_reviews"] = numStr(sc.RequiredApprovingReviews)
		facts["peer_review_enforced"] =
			boolStr(sc.DefaultBranchProtected && sc.RequiredApprovingReviews >= 1)

		p := snap.Pipeline
		facts["sast_enabled"] = boolStr(p.SASTEnabled)
		facts["dependency_scan_enabled"] = boolStr(p.DependencyScanEnabled)
		facts["build_gate_blocking"] = boolStr(p.BuildGateBlocking)
		facts["secure_build_gates"] =
			boolStr(p.SASTEnabled && p.DependencyScanEnabled && p.BuildGateBlocking)
	}

	res.Facts = facts
	res.Findings = agent.RunChecks(c.Checks(), facts)
	res.Evidence = evidenceFor(t, c.Type(), "governance", cc.Simulation, now, facts, res.Findings)
	res.EndedAt = cc.Time()
	return res
}

func max0(n int) int {
	if n < 0 {
		return 0
	}
	return n
}
