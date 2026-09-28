// Package job implements the P01 "Automated Scheduled Evidence Pull" pipeline:
// a validated job request comes in (job ID, application, environment, source,
// an approved+versioned collector/query ID, validated parameters, a timeout,
// and a correlation ID), and the package executes exactly one pre-approved,
// deterministic collector from internal/collectors against it, enforcing a
// timeout, a bounded output size, and safe idempotent replay.
//
// This package contains NO LLM, NO autonomous planning, NO natural-language
// interpretation, NO arbitrary SQL, and NO arbitrary shell execution. Every
// operation it can perform is a named, versioned entry in a Catalog compiled
// into the binary ahead of time (see catalog.go); the only external input a
// job carries is a whitelisted set of parameter values, validated against
// that entry's contract before anything runs.
package job

import "github.com/pramaan/pramaan-next/agents-go/internal/agent"

// JobRequest is the validated input contract for one evidence-pull job.
type JobRequest struct {
	JobID            string            `json:"jobId"`
	ApplicationID    string            `json:"applicationId"`
	Environment      string            `json:"environment"`
	SourceID         string            `json:"sourceId"`
	CollectorID      string            `json:"collectorId"`
	CollectorVersion string            `json:"collectorVersion"`
	Parameters       map[string]string `json:"parameters,omitempty"`
	TimeoutSeconds   int               `json:"timeoutSeconds,omitempty"`
	CorrelationID    string            `json:"correlationId"`
}

// Status is the terminal (or non-terminal-but-final-for-this-attempt) outcome
// of one job execution.
type Status string

const (
	// StatusSucceeded: the approved collector ran and evidence was gathered
	// (and submitted, if a Submitter was configured). This is independent of
	// whether individual technical checks PASSed or FAILed — a completed
	// collection with FAIL findings is still a successful pull.
	StatusSucceeded Status = "SUCCEEDED"
	// StatusFailed: the request was valid and approved, but execution could
	// not complete (collector error, source unavailable, output too large,
	// or evidence submission failed).
	StatusFailed Status = "FAILED"
	// StatusRejected: the request itself is not eligible to run (malformed,
	// invalid, feature disabled, unknown/unsupported collector, or bad
	// parameters/source reference). Nothing was executed.
	StatusRejected Status = "REJECTED"
	// StatusTimeout: the collector did not finish within the job's timeout.
	StatusTimeout Status = "TIMEOUT"
	// StatusCancelled: the caller's context was cancelled before completion
	// or before the timeout elapsed.
	StatusCancelled Status = "CANCELLED"
)

// ErrorCode enumerates the reasons a job did not SUCCEED.
type ErrorCode string

const (
	ErrMalformedRequest   ErrorCode = "MALFORMED_REQUEST"
	ErrInvalidRequest     ErrorCode = "INVALID_REQUEST"
	ErrFeatureDisabled    ErrorCode = "FEATURE_DISABLED"
	ErrUnknownCollector   ErrorCode = "UNKNOWN_COLLECTOR"
	ErrUnsupportedVersion ErrorCode = "UNSUPPORTED_COLLECTOR_VERSION"
	ErrInvalidParameters  ErrorCode = "INVALID_PARAMETERS"
	ErrInvalidSource      ErrorCode = "INVALID_SOURCE"
	ErrSourceUnavailable  ErrorCode = "SOURCE_UNAVAILABLE"
	ErrCollectorError     ErrorCode = "COLLECTOR_ERROR"
	ErrExcessiveOutput    ErrorCode = "EXCESSIVE_OUTPUT"
	ErrTimeout            ErrorCode = "TIMEOUT"
	ErrCancelled          ErrorCode = "CANCELLED"
	ErrSubmitFailed       ErrorCode = "EVIDENCE_SUBMIT_FAILED"
	ErrInternal           ErrorCode = "INTERNAL_ERROR"
)

// JobError is the structured error contract returned on any non-success status.
type JobError struct {
	Code    ErrorCode `json:"code"`
	Message string    `json:"message"`
}

// JobResult is the structured output contract for one job execution. It is
// always returned — a job never panics or exits without one.
type JobResult struct {
	JobID            string `json:"jobId"`
	CorrelationID    string `json:"correlationId"`
	Status           Status `json:"status"`
	CollectorID      string `json:"collectorId,omitempty"`
	CollectorVersion string `json:"collectorVersion,omitempty"`

	Error *JobError `json:"error,omitempty"`

	Findings      []agent.Finding `json:"findings,omitempty"`
	EvidenceCount int             `json:"evidenceCount"`
	OutputBytes   int             `json:"outputBytes"`

	// Submitted is true only if evidence was actually POSTed to the backend.
	Submitted bool `json:"submitted"`
	// IdempotentReplay is true when this result was served from the
	// idempotency store instead of re-running the collector.
	IdempotentReplay bool `json:"idempotentReplay"`

	StartedAt  string `json:"startedAt,omitempty"`
	EndedAt    string `json:"endedAt,omitempty"`
	DurationMs int64  `json:"durationMs"`
}

// Success reports whether the job produced usable evidence.
func (r JobResult) Success() bool { return r.Status == StatusSucceeded }
