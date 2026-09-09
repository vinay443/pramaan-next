package agent

import "time"

// Check is one predefined technical check. It is deterministic: given the same
// Facts it always yields the same Finding. Checks encode TECHNICAL expectations
// (e.g. "TLS >= 1.2"), never GRC scoring or workflow.
type Check struct {
	ID          string
	ControlID   string
	Framework   string
	Title       string
	Description string
	Expected    string
	// Eval inspects normalized facts and returns a status plus what was observed.
	Eval func(f Facts) (status string, observed string, detail string)
}

// Run evaluates the check against facts and returns a Finding.
func (c Check) Run(f Facts) Finding {
	status, observed, detail := c.Eval(f)
	return Finding{
		CheckID:   c.ID,
		ControlID: c.ControlID,
		Framework: c.Framework,
		Title:     c.Title,
		Status:    status,
		Observed:  observed,
		Expected:  c.Expected,
		Detail:    detail,
	}
}

// RunChecks evaluates every check against the same facts, in order.
func RunChecks(checks []Check, f Facts) []Finding {
	out := make([]Finding, 0, len(checks))
	for _, c := range checks {
		out = append(out, c.Run(f))
	}
	return out
}

// Summarize counts findings by status.
func Summarize(findings []Finding) map[string]int {
	m := map[string]int{StatusPass: 0, StatusFail: 0, StatusNotApplicable: 0, StatusError: 0}
	for _, f := range findings {
		m[f.Status]++
	}
	return m
}

// --- small helpers shared by collectors -------------------------------------

// Expect returns PASS when got==want, else FAIL.
func Expect(want, got string) (string, string, string) {
	if got == "" {
		return StatusError, got, "value not collected"
	}
	if got == want {
		return StatusPass, got, ""
	}
	return StatusFail, got, "expected " + want
}

// ExpectOneOf returns PASS when got is in allowed.
func ExpectOneOf(allowed []string, got string) (string, string, string) {
	if got == "" {
		return StatusError, got, "value not collected"
	}
	for _, a := range allowed {
		if got == a {
			return StatusPass, got, ""
		}
	}
	return StatusFail, got, "expected one of allowed values"
}

// DaysUntil parses an RFC3339 timestamp fact and returns whole days from now.
func DaysUntil(rfc3339 string, now time.Time) (int, bool) {
	t, err := time.Parse(time.RFC3339, rfc3339)
	if err != nil {
		return 0, false
	}
	return int(t.Sub(now).Hours() / 24), true
}
