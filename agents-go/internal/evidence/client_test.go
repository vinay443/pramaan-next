package evidence

import (
	"context"
	"encoding/json"
	"io"
	"net/http"
	"net/http/httptest"
	"testing"
)

func TestSubmitBulkMatchesContract(t *testing.T) {
	var gotPath string
	var gotBody []IngestRequest

	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		gotPath = r.URL.Path
		raw, _ := io.ReadAll(r.Body)
		if err := json.Unmarshal(raw, &gotBody); err != nil {
			t.Errorf("body not a JSON array of IngestRequest: %v", err)
		}
		// assert exact contract field names are present on the wire
		var generic []map[string]any
		_ = json.Unmarshal(raw, &generic)
		if _, ok := generic[0]["applicationSlug"]; !ok {
			t.Errorf("missing contract field applicationSlug in %v", generic[0])
		}
		if _, ok := generic[0]["contentBase64"]; !ok {
			t.Errorf("missing contract field contentBase64")
		}
		w.Header().Set("Content-Type", "application/json")
		_ = json.NewEncoder(w).Encode(BulkIngestResponse{Received: 1, Created: 1})
	}))
	defer srv.Close()

	c := NewClient(Options{BaseURL: srv.URL})
	resp, err := c.SubmitBulk(context.Background(), []IngestRequest{{
		ApplicationSlug: "payments", ControlID: "C-1", Framework: "ITPP",
		SourceSystem: "AGENT_OS_LINUX", ContentBase64: "eyJvayI6dHJ1ZX0=", ContentType: "application/json",
	}})
	if err != nil {
		t.Fatal(err)
	}
	if gotPath != "/api/v1/evidence/bulk" {
		t.Fatalf("wrong path: %s", gotPath)
	}
	if resp.Received != 1 || resp.Created != 1 {
		t.Fatalf("unexpected response: %+v", resp)
	}
	if gotBody[0].SourceSystem != "AGENT_OS_LINUX" {
		t.Fatalf("round-trip mismatch: %+v", gotBody[0])
	}
}

func TestDryRunDoesNotCallNetwork(t *testing.T) {
	c := NewClient(Options{BaseURL: "http://127.0.0.1:0", DryRun: true})
	resp, err := c.SubmitBulk(context.Background(), []IngestRequest{
		{ApplicationSlug: "a", ControlID: "C-1", Framework: "ITPP", SourceSystem: "S"},
		{ApplicationSlug: "a", ControlID: "C-2", Framework: "ITPP", SourceSystem: "S"},
	})
	if err != nil {
		t.Fatalf("dry-run must not error: %v", err)
	}
	if resp.Received != 2 {
		t.Fatalf("want Received=2, got %d", resp.Received)
	}
	if len(c.Captured()) != 2 {
		t.Fatalf("want 2 captured requests, got %d", len(c.Captured()))
	}
}

func TestServerErrorSurfacesMessage(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusBadRequest)
		_ = json.NewEncoder(w).Encode(ErrorBody{Status: 400, Error: "Bad Request", Message: "controlId is required"})
	}))
	defer srv.Close()

	c := NewClient(Options{BaseURL: srv.URL})
	_, err := c.Ingest(context.Background(), IngestRequest{ApplicationSlug: "a"})
	if err == nil {
		t.Fatal("expected an error")
	}
	if got := err.Error(); got == "" || !contains(got, "controlId is required") {
		t.Fatalf("error message not surfaced: %q", got)
	}
}

func contains(s, sub string) bool {
	for i := 0; i+len(sub) <= len(s); i++ {
		if s[i:i+len(sub)] == sub {
			return true
		}
	}
	return false
}
