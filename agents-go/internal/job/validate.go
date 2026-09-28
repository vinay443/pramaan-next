package job

import (
	"fmt"
	"regexp"
)

// jobIDPattern also doubles as a filesystem-safety check: the idempotency
// store uses the job ID as a file name, so it must never contain path
// separators or traversal sequences.
var jobIDPattern = regexp.MustCompile(`^[A-Za-z0-9][A-Za-z0-9_-]{0,99}$`)

// correlationIDPattern is deliberately permissive (callers may pass through
// an upstream trace ID in any reasonable format) but still excludes control
// characters and path-unsafe characters.
var correlationIDPattern = regexp.MustCompile(`^[A-Za-z0-9][A-Za-z0-9_.:-]{0,199}$`)

var allowedEnvironments = map[string]bool{
	"dev":        true,
	"test":       true,
	"staging":    true,
	"production": true,
}

// validateShape distinguishes two rejection reasons the test matrix requires
// as separate scenarios:
//
//   - malformed: a required field is simply missing/empty. The request
//     doesn't describe a job at all.
//   - invalid: every required field is present, but a value violates a
//     semantic rule (bad ID format, unknown environment, non-positive
//     timeout).
func validateShape(req JobRequest) *JobError {
	required := map[string]string{
		"jobId":            req.JobID,
		"applicationId":    req.ApplicationID,
		"environment":      req.Environment,
		"sourceId":         req.SourceID,
		"collectorId":      req.CollectorID,
		"collectorVersion": req.CollectorVersion,
		"correlationId":    req.CorrelationID,
	}
	for field, v := range required {
		if v == "" {
			return &JobError{Code: ErrMalformedRequest, Message: fmt.Sprintf("missing required field %q", field)}
		}
	}

	if !jobIDPattern.MatchString(req.JobID) {
		return &JobError{Code: ErrInvalidRequest, Message: "jobId must match ^[A-Za-z0-9][A-Za-z0-9_-]{0,99}$"}
	}
	if !correlationIDPattern.MatchString(req.CorrelationID) {
		return &JobError{Code: ErrInvalidRequest, Message: "correlationId contains unsupported characters"}
	}
	if !allowedEnvironments[req.Environment] {
		return &JobError{Code: ErrInvalidRequest, Message: fmt.Sprintf("unknown environment %q", req.Environment)}
	}
	if req.TimeoutSeconds < 0 {
		return &JobError{Code: ErrInvalidRequest, Message: "timeoutSeconds must not be negative"}
	}
	return nil
}

// validateParameters checks req.Parameters against an approved collector's
// whitelist: every required rule must be satisfied, every allowed-value
// constraint must hold, and no key outside the whitelist is permitted. This
// is the only place external, job-supplied values are allowed to influence
// execution — everything here is a fixed lookup against Params, never a
// dynamic expression or command.
func validateParameters(params map[string]string, rules []ParamRule) *JobError {
	allowed := make(map[string]ParamRule, len(rules))
	for _, r := range rules {
		allowed[r.Name] = r
	}
	for key, val := range params {
		rule, ok := allowed[key]
		if !ok {
			return &JobError{Code: ErrInvalidParameters, Message: fmt.Sprintf("parameter %q is not approved for this collector", key)}
		}
		if !rule.allowed(val) {
			return &JobError{Code: ErrInvalidParameters, Message: fmt.Sprintf("parameter %q has an unsupported value", key)}
		}
	}
	for _, r := range rules {
		if r.Required && params[r.Name] == "" {
			return &JobError{Code: ErrInvalidParameters, Message: fmt.Sprintf("missing required parameter %q", r.Name)}
		}
	}
	return nil
}
