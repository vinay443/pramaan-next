package job

import (
	"context"
	"errors"
	"sync/atomic"
	"testing"
	"time"

	"github.com/pramaan/pramaan-next/agents-go/internal/agent"
)

var errFakeIO = errors.New("simulated io error")

// --- test fixtures -----------------------------------------------------

// fakeCollector is a controllable agent.Collector for exercising the
// executor's timeout/cancellation/error/output-bound paths without touching
// any of the real Phase 1 collectors.
type fakeCollector struct {
	kind    string
	tech    string
	sleep   time.Duration
	err     string
	content []byte
	calls   atomic.Int32
}

func (f *fakeCollector) Type() string           { return f.kind }
func (f *fakeCollector) Technologies() []string { return []string{f.tech} }
func (f *fakeCollector) Checks() []agent.Check  { return nil }
func (f *fakeCollector) Supports(t agent.Target) bool {
	return t.Kind == f.kind
}
func (f *fakeCollector) Collect(cc agent.CollectionContext, t agent.Target) agent.Result {
	f.calls.Add(1)
	if f.sleep > 0 {
		time.Sleep(f.sleep)
	}
	res := agent.Result{Target: t, Simulated: cc.Simulation, StartedAt: cc.Time(), EndedAt: cc.Time(), Err: f.err}
	if f.err == "" {
		content := f.content
		if content == nil {
			content = []byte(`{"ok":true}`)
		}
		res.Evidence = []agent.Evidence{{
			Application: t.Application, ControlID: "TEST-01", Framework: "TEST",
			SourceSystem: "AGENT_TEST", SourceObjectID: t.Name, Title: "test evidence",
			ContentType: "application/json", Content: content, CollectedAt: cc.Time(),
		}}
		res.Findings = []agent.Finding{{CheckID: "TEST-01", ControlID: "TEST-01", Framework: "TEST", Title: "t", Status: agent.StatusPass}}
	}
	return res
}

func newTestRegistry(collectors ...agent.Collector) *agent.Registry {
	reg := agent.NewRegistry("test-agent", "test-agent", "dev", true)
	for _, c := range collectors {
		reg.Register(c)
	}
	return reg
}

func testCatalog(entries ...ApprovedCollector) *Catalog {
	return NewCatalog(entries)
}

func baseRequest() JobRequest {
	return JobRequest{
		JobID:            "job-001",
		ApplicationID:    "payments",
		Environment:      "dev",
		SourceID:         "sim-fake-1",
		CollectorID:      "fake-collector",
		CollectorVersion: "v1",
		CorrelationID:    "corr-001",
		TimeoutSeconds:   5,
	}
}

func fakeSources(available bool) SourceStore {
	return NewStaticSourceStore([]Source{
		{SourceID: "sim-fake-1", Environment: "dev", Technology: "fake", Address: "sim-fake-1", Available: available},
	})
}

func newExecutor(t *testing.T, collector agent.Collector, catalogEntry ApprovedCollector, opts ...func(*Executor)) (*Executor, *fakeCollector) {
	t.Helper()
	reg := newTestRegistry(collector)
	fc, _ := collector.(*fakeCollector)
	exec := &Executor{
		Catalog:     testCatalog(catalogEntry),
		Sources:     fakeSources(true),
		Registry:    reg,
		Idempotency: NewMemoryStore(),
		Submitter:   nil,
		Config: Config{
			FeatureEnabled:       true,
			DefaultTimeout:       5 * time.Second,
			MaxTimeout:           5 * time.Second,
			GlobalMaxOutputBytes: 1 << 20,
		},
		Now: func() time.Time { return time.Unix(1700000000, 0).UTC() },
	}
	for _, o := range opts {
		o(exec)
	}
	return exec, fc
}

func fakeCatalogEntry() ApprovedCollector {
	return ApprovedCollector{
		ID: "fake-collector", Version: "v1", AgentType: "fake", Technology: "fake",
		Params: []ParamRule{{Name: "name"}, {Name: "address"}},
	}
}

// --- scenarios -----------------------------------------------------------

func TestValidRequestSucceeds(t *testing.T) {
	fc := &fakeCollector{kind: "fake", tech: "fake"}
	exec, _ := newExecutor(t, fc, fakeCatalogEntry())

	res := exec.Execute(context.Background(), baseRequest())

	if res.Status != StatusSucceeded {
		t.Fatalf("status = %v, want SUCCEEDED (error=%+v)", res.Status, res.Error)
	}
	if res.JobID != "job-001" || res.CorrelationID != "corr-001" {
		t.Fatalf("job/correlation id not propagated: %+v", res)
	}
	if res.EvidenceCount != 1 || res.OutputBytes == 0 {
		t.Fatalf("expected evidence to be captured: %+v", res)
	}
	if res.Submitted {
		t.Fatalf("expected Submitted=false with no Submitter configured")
	}
}

func TestSuccessfulCollectionWithSubmission(t *testing.T) {
	fc := &fakeCollector{kind: "fake", tech: "fake"}
	sub := &capturingSubmitter{}
	exec, _ := newExecutor(t, fc, fakeCatalogEntry(), func(e *Executor) { e.Submitter = sub })

	res := exec.Execute(context.Background(), baseRequest())

	if res.Status != StatusSucceeded || !res.Submitted {
		t.Fatalf("expected submitted success, got %+v", res)
	}
	if len(sub.requests) != 1 {
		t.Fatalf("expected 1 submitted request, got %d", len(sub.requests))
	}
	if sub.requests[0].ApplicationSlug != "payments" {
		t.Fatalf("unexpected mapped request: %+v", sub.requests[0])
	}
}

func TestMalformedRequestMissingFields(t *testing.T) {
	fc := &fakeCollector{kind: "fake", tech: "fake"}
	exec, _ := newExecutor(t, fc, fakeCatalogEntry())

	req := baseRequest()
	req.JobID = ""

	res := exec.Execute(context.Background(), req)
	if res.Status != StatusRejected || res.Error == nil || res.Error.Code != ErrMalformedRequest {
		t.Fatalf("expected MALFORMED_REQUEST rejection, got %+v", res)
	}
}

func TestInvalidRequestBadTimeout(t *testing.T) {
	fc := &fakeCollector{kind: "fake", tech: "fake"}
	exec, _ := newExecutor(t, fc, fakeCatalogEntry())

	req := baseRequest()
	req.TimeoutSeconds = -5

	res := exec.Execute(context.Background(), req)
	if res.Status != StatusRejected || res.Error == nil || res.Error.Code != ErrInvalidRequest {
		t.Fatalf("expected INVALID_REQUEST rejection, got %+v", res)
	}
}

func TestInvalidRequestUnknownEnvironment(t *testing.T) {
	fc := &fakeCollector{kind: "fake", tech: "fake"}
	exec, _ := newExecutor(t, fc, fakeCatalogEntry())

	req := baseRequest()
	req.Environment = "quantum-staging"

	res := exec.Execute(context.Background(), req)
	if res.Status != StatusRejected || res.Error == nil || res.Error.Code != ErrInvalidRequest {
		t.Fatalf("expected INVALID_REQUEST rejection, got %+v", res)
	}
}

func TestMalformedRequestJSON(t *testing.T) {
	if _, err := DecodeRequest([]byte(`{"jobId": "job-1", `)); err == nil {
		t.Fatalf("expected a JSON decode error for truncated input")
	}
}

func TestUnknownCollectorRejected(t *testing.T) {
	fc := &fakeCollector{kind: "fake", tech: "fake"}
	exec, _ := newExecutor(t, fc, fakeCatalogEntry())

	req := baseRequest()
	req.CollectorID = "does-not-exist"

	res := exec.Execute(context.Background(), req)
	if res.Status != StatusRejected || res.Error == nil || res.Error.Code != ErrUnknownCollector {
		t.Fatalf("expected UNKNOWN_COLLECTOR rejection, got %+v", res)
	}
}

func TestUnsupportedVersionRejected(t *testing.T) {
	fc := &fakeCollector{kind: "fake", tech: "fake"}
	exec, _ := newExecutor(t, fc, fakeCatalogEntry())

	req := baseRequest()
	req.CollectorVersion = "v99"

	res := exec.Execute(context.Background(), req)
	if res.Status != StatusRejected || res.Error == nil || res.Error.Code != ErrUnsupportedVersion {
		t.Fatalf("expected UNSUPPORTED_COLLECTOR_VERSION rejection, got %+v", res)
	}
}

func TestInvalidParametersUnknownKey(t *testing.T) {
	fc := &fakeCollector{kind: "fake", tech: "fake"}
	exec, _ := newExecutor(t, fc, fakeCatalogEntry())

	req := baseRequest()
	req.Parameters = map[string]string{"shellCommand": "rm -rf /"}

	res := exec.Execute(context.Background(), req)
	if res.Status != StatusRejected || res.Error == nil || res.Error.Code != ErrInvalidParameters {
		t.Fatalf("expected INVALID_PARAMETERS rejection, got %+v", res)
	}
}

func TestInvalidParametersMissingRequired(t *testing.T) {
	fc := &fakeCollector{kind: "database", tech: "postgresql"}
	entry := ApprovedCollector{
		ID: "db-fake", Version: "v1", AgentType: "database",
		Params: []ParamRule{
			{Name: "technology", Required: true, AllowedValues: []string{"postgresql", "mysql"}},
			{Name: "snapshotPath", Required: true},
		},
	}
	exec, _ := newExecutor(t, fc, entry)
	exec.Sources = NewStaticSourceStore([]Source{
		{SourceID: "sim-fake-1", Environment: "dev", Technology: "postgresql", Address: "x", Available: true},
	})

	req := baseRequest()
	req.CollectorID, req.CollectorVersion = "db-fake", "v1"
	req.Parameters = map[string]string{"technology": "postgresql"} // missing snapshotPath

	res := exec.Execute(context.Background(), req)
	if res.Status != StatusRejected || res.Error == nil || res.Error.Code != ErrInvalidParameters {
		t.Fatalf("expected INVALID_PARAMETERS rejection, got %+v", res)
	}
}

func TestInvalidSourceUnknownID(t *testing.T) {
	fc := &fakeCollector{kind: "fake", tech: "fake"}
	exec, _ := newExecutor(t, fc, fakeCatalogEntry())

	req := baseRequest()
	req.SourceID = "no-such-source"

	res := exec.Execute(context.Background(), req)
	if res.Status != StatusRejected || res.Error == nil || res.Error.Code != ErrInvalidSource {
		t.Fatalf("expected INVALID_SOURCE rejection, got %+v", res)
	}
}

func TestSourceUnavailable(t *testing.T) {
	fc := &fakeCollector{kind: "fake", tech: "fake"}
	exec, _ := newExecutor(t, fc, fakeCatalogEntry(), func(e *Executor) {
		e.Sources = fakeSources(false)
	})

	res := exec.Execute(context.Background(), baseRequest())
	if res.Status != StatusFailed || res.Error == nil || res.Error.Code != ErrSourceUnavailable {
		t.Fatalf("expected FAILED/SOURCE_UNAVAILABLE, got %+v", res)
	}
}

func TestSourceRegistryUnreadableIsSourceUnavailable(t *testing.T) {
	fc := &fakeCollector{kind: "fake", tech: "fake"}
	exec, _ := newExecutor(t, fc, fakeCatalogEntry(), func(e *Executor) {
		e.Sources = brokenSourceStore{}
	})

	res := exec.Execute(context.Background(), baseRequest())
	if res.Status != StatusFailed || res.Error == nil || res.Error.Code != ErrSourceUnavailable {
		t.Fatalf("expected FAILED/SOURCE_UNAVAILABLE for a broken registry, got %+v", res)
	}
}

type brokenSourceStore struct{}

func (brokenSourceStore) Lookup(string) (Source, error) {
	return Source{}, errFakeIO
}

func TestCollectorFailureHandledSafely(t *testing.T) {
	fc := &fakeCollector{kind: "fake", tech: "fake", err: "snapshot file unreadable"}
	exec, _ := newExecutor(t, fc, fakeCatalogEntry())

	res := exec.Execute(context.Background(), baseRequest())
	if res.Status != StatusFailed || res.Error == nil || res.Error.Code != ErrCollectorError {
		t.Fatalf("expected FAILED/COLLECTOR_ERROR, got %+v", res)
	}
}

func TestExcessiveOutputRejectedBeforeSubmission(t *testing.T) {
	big := make([]byte, 2048)
	fc := &fakeCollector{kind: "fake", tech: "fake", content: big}
	entry := fakeCatalogEntry()
	entry.MaxOutputBytes = 64 // far smaller than the base64 encoding of 2048 bytes
	sub := &capturingSubmitter{}
	exec, _ := newExecutor(t, fc, entry, func(e *Executor) { e.Submitter = sub })

	res := exec.Execute(context.Background(), baseRequest())
	if res.Status != StatusFailed || res.Error == nil || res.Error.Code != ErrExcessiveOutput {
		t.Fatalf("expected FAILED/EXCESSIVE_OUTPUT, got %+v", res)
	}
	if len(sub.requests) != 0 {
		t.Fatalf("oversized evidence must never reach the submitter, got %d requests", len(sub.requests))
	}
}

func TestTimeoutReturnsPromptly(t *testing.T) {
	fc := &fakeCollector{kind: "fake", tech: "fake", sleep: 500 * time.Millisecond}
	exec, _ := newExecutor(t, fc, fakeCatalogEntry(), func(e *Executor) {
		e.Config.DefaultTimeout = 50 * time.Millisecond
		e.Config.MaxTimeout = 50 * time.Millisecond
	})

	req := baseRequest()
	req.TimeoutSeconds = 0 // use DefaultTimeout

	start := time.Now()
	res := exec.Execute(context.Background(), req)
	elapsed := time.Since(start)

	if res.Status != StatusTimeout || res.Error == nil || res.Error.Code != ErrTimeout {
		t.Fatalf("expected TIMEOUT, got %+v", res)
	}
	if elapsed > 300*time.Millisecond {
		t.Fatalf("Execute took %s, expected it to return promptly at the timeout boundary", elapsed)
	}
}

func TestContextCancellationDistinctFromTimeout(t *testing.T) {
	fc := &fakeCollector{kind: "fake", tech: "fake", sleep: 500 * time.Millisecond}
	exec, _ := newExecutor(t, fc, fakeCatalogEntry(), func(e *Executor) {
		e.Config.DefaultTimeout = 5 * time.Second // long enough that cancellation, not timeout, fires first
		e.Config.MaxTimeout = 5 * time.Second
	})

	ctx, cancel := context.WithCancel(context.Background())
	go func() {
		time.Sleep(30 * time.Millisecond)
		cancel()
	}()

	res := exec.Execute(ctx, baseRequest())
	if res.Status != StatusCancelled || res.Error == nil || res.Error.Code != ErrCancelled {
		t.Fatalf("expected CANCELLED, got %+v", res)
	}
}

func TestDuplicateJobIDIsIdempotent(t *testing.T) {
	fc := &fakeCollector{kind: "fake", tech: "fake"}
	exec, _ := newExecutor(t, fc, fakeCatalogEntry())

	req := baseRequest()
	first := exec.Execute(context.Background(), req)
	if first.Status != StatusSucceeded || first.IdempotentReplay {
		t.Fatalf("first execution should be a fresh success, got %+v", first)
	}
	second := exec.Execute(context.Background(), req)
	if !second.IdempotentReplay {
		t.Fatalf("second execution with the same jobId should be an idempotent replay, got %+v", second)
	}
	if fc.calls.Load() != 1 {
		t.Fatalf("collector should run exactly once across both calls, ran %d times", fc.calls.Load())
	}
}

func TestFeatureFlagDisabledRejectsBeforeExecution(t *testing.T) {
	fc := &fakeCollector{kind: "fake", tech: "fake"}
	exec, _ := newExecutor(t, fc, fakeCatalogEntry(), func(e *Executor) {
		e.Config.FeatureEnabled = false
	})

	res := exec.Execute(context.Background(), baseRequest())
	if res.Status != StatusRejected || res.Error == nil || res.Error.Code != ErrFeatureDisabled {
		t.Fatalf("expected REJECTED/FEATURE_DISABLED, got %+v", res)
	}
	if fc.calls.Load() != 0 {
		t.Fatalf("collector must never run when the feature flag is disabled, ran %d times", fc.calls.Load())
	}
}

func TestJobErrorNeverContainsRawParameterValues(t *testing.T) {
	fc := &fakeCollector{kind: "fake", tech: "fake"}
	exec, _ := newExecutor(t, fc, fakeCatalogEntry())

	req := baseRequest()
	req.Parameters = map[string]string{"apiSecretDoNotLog": "super-secret-value-12345"}

	res := exec.Execute(context.Background(), req)
	if res.Status != StatusRejected || res.Error == nil {
		t.Fatalf("expected a rejection for the unapproved parameter, got %+v", res)
	}
	if containsSubstring(res.Error.Message, "super-secret-value-12345") {
		t.Fatalf("job error message must not echo raw parameter values: %q", res.Error.Message)
	}
}

func containsSubstring(s, substr string) bool {
	for i := 0; i+len(substr) <= len(s); i++ {
		if s[i:i+len(substr)] == substr {
			return true
		}
	}
	return false
}
