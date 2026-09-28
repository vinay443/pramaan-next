package job

import (
	"context"
	"errors"
	"fmt"
	"log/slog"
	"time"

	"github.com/pramaan/pramaan-next/agents-go/internal/agent"
	"github.com/pramaan/pramaan-next/agents-go/internal/evidence"
)

// Config holds the executor's operating limits, all sourced from
// internal/config so they follow the same env-var conventions as the rest of
// agents-go.
type Config struct {
	// FeatureEnabled gates the entire pipeline (AGENT_JOB_PULL_ENABLED).
	FeatureEnabled bool
	// DefaultTimeout is used when a request omits timeoutSeconds.
	DefaultTimeout time.Duration
	// MaxTimeout caps whatever timeout a request asks for.
	MaxTimeout time.Duration
	// GlobalMaxOutputBytes bounds evidence size when the matched
	// ApprovedCollector doesn't set its own MaxOutputBytes.
	GlobalMaxOutputBytes int
}

// Executor runs one JobRequest at a time against the approved Catalog,
// reusing the existing agent.Registry / agent.Collector / agent.Runner /
// evidence.Client machinery for the actual collection and submission.
type Executor struct {
	Catalog     *Catalog
	Sources     SourceStore
	Registry    *agent.Registry // the same registry collectors.Register populates
	Idempotency Store
	// Submitter, if non-nil, receives collected evidence for submission to
	// the backend. Nil means "collect only" — evidence is gathered and
	// validated but never POSTed (used for local/offline demonstration).
	//
	// Whether collectors themselves run in simulation mode is controlled by
	// Registry.Simulation (set at startup from SIMULATION_MODE), the same
	// switch agent.Runner already uses — this executor does not duplicate
	// that flag.
	Submitter agent.Submitter
	Config    Config
	Now       func() time.Time
	Logger    *slog.Logger

	// CollectedBy is stamped onto submitted evidence, same as agent.Runner.
	CollectedBy string
}

func (e *Executor) now() time.Time {
	if e.Now != nil {
		return e.Now()
	}
	return time.Now().UTC()
}

func (e *Executor) logger() *slog.Logger {
	if e.Logger != nil {
		return e.Logger
	}
	return slog.Default()
}

// Execute validates and runs one job request. It always returns a JobResult
// — it never panics and never leaves the caller without a structured
// outcome, per the P01 contract.
func (e *Executor) Execute(ctx context.Context, req JobRequest) JobResult {
	started := e.now()

	// 1. Structural validation first: even a cache hit or a disabled-feature
	// rejection needs a well-formed job ID (idempotency keys off it) and
	// should still be logged with a correlation ID.
	if jobErr := validateShape(req); jobErr != nil {
		return e.reject(req, started, jobErr)
	}

	log := e.logger().With("jobId", req.JobID, "correlationId", req.CorrelationID,
		"collectorId", req.CollectorID, "collectorVersion", req.CollectorVersion)

	// 2. Feature flag gate — checked before any other business validation.
	if !e.Config.FeatureEnabled {
		log.Info("job rejected: feature disabled")
		return e.reject(req, started, &JobError{Code: ErrFeatureDisabled, Message: "scheduled evidence pull is disabled"})
	}

	// 3. Idempotent replay: a request with a job ID already recorded returns
	// the stored terminal result without re-running anything.
	if e.Idempotency != nil {
		if prior, ok := e.Idempotency.Get(req.JobID); ok {
			prior.IdempotentReplay = true
			log.Info("job replayed from idempotency store", "status", prior.Status)
			return prior
		}
	}

	// 4. Approved-collector lookup.
	approved, lookup := e.Catalog.Lookup(req.CollectorID, req.CollectorVersion)
	switch lookup {
	case LookupUnknownID:
		return e.rejectAndStore(req, started, &JobError{Code: ErrUnknownCollector,
			Message: fmt.Sprintf("collector %q is not approved", req.CollectorID)}, log)
	case LookupUnsupportedVersion:
		return e.rejectAndStore(req, started, &JobError{Code: ErrUnsupportedVersion,
			Message: fmt.Sprintf("collector %q version %q is not approved", req.CollectorID, req.CollectorVersion)}, log)
	}

	// 5. Parameter validation against that collector's approved contract.
	if jobErr := validateParameters(req.Parameters, approved.Params); jobErr != nil {
		log.Info("job rejected: invalid parameters", "params", paramKeys(req.Parameters))
		return e.rejectAndStore(req, started, jobErr, log)
	}

	// 6. Source validation.
	src, jobErr := e.validateSource(req, approved)
	if jobErr != nil {
		if jobErr.Code == ErrSourceUnavailable {
			// Infra-side problem: FAILED, not REJECTED — the request itself
			// was fine and may succeed on retry.
			return e.finishAndStore(req, started, JobResult{Status: StatusFailed, Error: jobErr}, log)
		}
		return e.rejectAndStore(req, started, jobErr, log)
	}

	// 7. Build the target and find the matching collector via the *same*
	// shared registry the rest of agents-go uses — the catalog and the
	// registry must agree, or this is an internal wiring error.
	target := buildTarget(req, approved, src)
	if _, ok := e.Registry.Match(target); !ok {
		return e.finishAndStore(req, started, JobResult{Status: StatusFailed, Error: &JobError{
			Code: ErrInternal, Message: "approved collector has no matching registered collector",
		}}, log)
	}

	// 8. Timeout: request-supplied, clamped to [1s, MaxTimeout], with
	// DefaultTimeout used when the request didn't specify one.
	timeout := e.effectiveTimeout(req)
	runCtx, cancel := context.WithTimeout(ctx, timeout)
	defer cancel()

	// 9. Run the approved collector through agent.Runner (reusing its
	// match+collect+contract-mapping logic exactly), capturing the mapped
	// IngestRequests instead of submitting them yet, so the output-size
	// bound can be enforced before anything is sent to the backend.
	capture := &capturingSubmitter{}
	runner := &agent.Runner{Registry: e.Registry, Submitter: capture, CollectedBy: e.CollectedBy, Now: e.Now}

	type runOutcome struct {
		report agent.RunReport
		err    error
	}
	done := make(chan runOutcome, 1)
	go func() {
		report, err := runner.Run(runCtx, []agent.Target{target})
		done <- runOutcome{report, err}
	}()

	select {
	case <-runCtx.Done():
		if errors.Is(runCtx.Err(), context.DeadlineExceeded) {
			log.Warn("job timed out", "timeoutSeconds", int(timeout.Seconds()))
			return e.finish(req, started, JobResult{Status: StatusTimeout, Error: &JobError{
				Code: ErrTimeout, Message: fmt.Sprintf("collector did not finish within %s", timeout),
			}})
		}
		log.Warn("job cancelled")
		return e.finish(req, started, JobResult{Status: StatusCancelled, Error: &JobError{
			Code: ErrCancelled, Message: "context cancelled before completion",
		}})

	case out := <-done:
		return e.handleRunOutcome(req, started, approved, out.report, out.err, capture, log)
	}
}

func (e *Executor) handleRunOutcome(req JobRequest, started time.Time, approved ApprovedCollector,
	report agent.RunReport, runErr error, capture *capturingSubmitter, log *slog.Logger,
) JobResult {
	if len(report.Unmatched) > 0 {
		return e.finishAndStore(req, started, JobResult{Status: StatusFailed, Error: &JobError{
			Code: ErrInternal, Message: "approved collector did not match the built target",
		}}, log)
	}
	if runErr != nil {
		// capturingSubmitter never errors, so a non-nil error here can only
		// come from Runner's own submit wrapping — treated defensively.
		return e.finishAndStore(req, started, JobResult{Status: StatusFailed, Error: &JobError{
			Code: ErrCollectorError, Message: runErr.Error(),
		}}, log)
	}

	var findings []agent.Finding
	var collectorErr string
	if len(report.Results) > 0 {
		findings = report.Results[0].Findings
		collectorErr = report.Results[0].Err
	}
	if collectorErr != "" {
		log.Warn("collector reported an error", "detail", collectorErr)
		return e.finishAndStore(req, started, JobResult{
			Status: StatusFailed, Findings: findings,
			Error: &JobError{Code: ErrCollectorError, Message: collectorErr},
		}, log)
	}

	captured := capture.requests
	outputBytes := 0
	for _, r := range captured {
		outputBytes += len(r.ContentBase64) + len(r.ContentText)
	}
	limit := approved.MaxOutputBytes
	if limit <= 0 {
		limit = e.Config.GlobalMaxOutputBytes
	}
	if limit > 0 && outputBytes > limit {
		log.Warn("job output exceeded bound", "outputBytes", outputBytes, "limit", limit)
		return e.finishAndStore(req, started, JobResult{
			Status: StatusFailed, Findings: findings, OutputBytes: outputBytes,
			Error: &JobError{Code: ErrExcessiveOutput, Message: fmt.Sprintf("evidence size %d bytes exceeds limit %d bytes", outputBytes, limit)},
		}, log)
	}

	result := JobResult{
		Status:        StatusSucceeded,
		Findings:      findings,
		EvidenceCount: len(captured),
		OutputBytes:   outputBytes,
	}

	if e.Submitter != nil && len(captured) > 0 {
		// Reuse the exact IngestRequests already mapped by agent.Runner —
		// no second collection pass, no re-derivation of the wire shape.
		if _, err := e.Submitter.Submit(context.Background(), captured); err != nil {
			log.Error("evidence submission failed", "error", err.Error())
			result.Status = StatusFailed
			result.Error = &JobError{Code: ErrSubmitFailed, Message: err.Error()}
			return e.finishAndStore(req, started, result, log)
		}
		result.Submitted = true
	}

	log.Info("job succeeded", "evidenceCount", result.EvidenceCount, "outputBytes", result.OutputBytes, "submitted", result.Submitted)
	return e.finishAndStore(req, started, result, log)
}

func (e *Executor) validateSource(req JobRequest, approved ApprovedCollector) (Source, *JobError) {
	src, err := e.Sources.Lookup(req.SourceID)
	if err != nil {
		if errors.Is(err, ErrSourceNotFound) {
			return Source{}, &JobError{Code: ErrInvalidSource, Message: fmt.Sprintf("source %q is not an approved source", req.SourceID)}
		}
		// Registry itself unreadable/corrupt: an infrastructure problem, not
		// a bad request.
		return Source{}, &JobError{Code: ErrSourceUnavailable, Message: "approved source registry is unavailable"}
	}
	if src.Environment != req.Environment {
		return Source{}, &JobError{Code: ErrInvalidSource, Message: "source environment does not match job environment"}
	}
	wantTech := approved.Technology
	if wantTech == "" {
		wantTech = req.Parameters["technology"]
	}
	if wantTech != "" && src.Technology != wantTech {
		return Source{}, &JobError{Code: ErrInvalidSource, Message: "source technology does not match the requested collector"}
	}
	if !src.Available {
		return Source{}, &JobError{Code: ErrSourceUnavailable, Message: fmt.Sprintf("source %q is currently unavailable", req.SourceID)}
	}
	return src, nil
}

func buildTarget(req JobRequest, approved ApprovedCollector, src Source) agent.Target {
	name := req.Parameters["name"]
	if name == "" {
		name = req.SourceID
	}
	address := req.Parameters["address"]
	if address == "" {
		address = src.Address
	}
	technology := approved.Technology
	if technology == "" {
		technology = req.Parameters["technology"]
	}

	params := make(map[string]string, len(req.Parameters))
	for k, v := range req.Parameters {
		if k == "name" || k == "address" || k == "technology" {
			continue
		}
		params[k] = v
	}

	return agent.Target{
		Kind:        approved.AgentType,
		Name:        name,
		Application: req.ApplicationID,
		Technology:  technology,
		Address:     address,
		Params:      params,
	}
}

func (e *Executor) effectiveTimeout(req JobRequest) time.Duration {
	timeout := e.Config.DefaultTimeout
	if req.TimeoutSeconds > 0 {
		timeout = time.Duration(req.TimeoutSeconds) * time.Second
	}
	if e.Config.MaxTimeout > 0 && timeout > e.Config.MaxTimeout {
		timeout = e.Config.MaxTimeout
	}
	if timeout <= 0 {
		timeout = 15 * time.Second
	}
	return timeout
}

// reject builds a REJECTED result without touching the idempotency store —
// used only for failures discovered before we even know the request is
// well-formed enough to safely key a store entry off it (shape errors,
// feature-disabled before catalog lookup).
func (e *Executor) reject(req JobRequest, started time.Time, jobErr *JobError) JobResult {
	e.logger().Info("job rejected", "jobId", req.JobID, "correlationId", req.CorrelationID,
		"code", jobErr.Code, "reason", jobErr.Message)
	return e.finish(req, started, JobResult{Status: StatusRejected, Error: jobErr})
}

func (e *Executor) rejectAndStore(req JobRequest, started time.Time, jobErr *JobError, log *slog.Logger) JobResult {
	log.Info("job rejected", "code", jobErr.Code, "reason", jobErr.Message)
	return e.finishAndStore(req, started, JobResult{Status: StatusRejected, Error: jobErr}, log)
}

func (e *Executor) finish(req JobRequest, started time.Time, partial JobResult) JobResult {
	ended := e.now()
	partial.JobID = req.JobID
	partial.CorrelationID = req.CorrelationID
	partial.CollectorID = req.CollectorID
	partial.CollectorVersion = req.CollectorVersion
	partial.StartedAt = started.Format(time.RFC3339)
	partial.EndedAt = ended.Format(time.RFC3339)
	partial.DurationMs = ended.Sub(started).Milliseconds()
	return partial
}

// finishAndStore completes the result and, for deterministic terminal
// outcomes only (SUCCEEDED / FAILED / REJECTED), records it in the
// idempotency store. TIMEOUT and CANCELLED are never stored: they describe
// the execution environment on this attempt, not a deterministic property of
// the request, so a retry with the same job ID should genuinely retry.
func (e *Executor) finishAndStore(req JobRequest, started time.Time, partial JobResult, _ *slog.Logger) JobResult {
	result := e.finish(req, started, partial)
	if e.Idempotency != nil {
		switch result.Status {
		case StatusSucceeded, StatusFailed, StatusRejected:
			e.Idempotency.Put(req.JobID, result)
		}
	}
	return result
}

// capturingSubmitter records the IngestRequests agent.Runner mapped from
// collected evidence, without sending them anywhere. The executor uses it to
// measure output size and, on success, to hand the exact same payload to the
// real Submitter — avoiding a second collection pass.
type capturingSubmitter struct {
	requests []evidence.IngestRequest
}

func (c *capturingSubmitter) Submit(_ context.Context, reqs []evidence.IngestRequest) (evidence.BulkIngestResponse, error) {
	c.requests = append(c.requests, reqs...)
	return evidence.BulkIngestResponse{Received: len(reqs)}, nil
}
