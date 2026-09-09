package evidence

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"log/slog"
	"net/http"
	"strings"
	"time"
)

// Client submits evidence to the Pramaan backend per the OpenAPI contract.
//
// Endpoints used:
//
//	POST {BaseURL}/api/v1/evidence/ingest   -> IngestResult
//	POST {BaseURL}/api/v1/evidence/bulk     -> BulkIngestResponse
type Client struct {
	BaseURL string
	HTTP    *http.Client
	// DryRun logs requests and returns a synthetic response without any network call.
	DryRun bool
	Logger *slog.Logger

	// captured holds requests seen while DryRun is true (useful for tests/inspection).
	captured []IngestRequest
}

// Options configures NewClient.
type Options struct {
	BaseURL string
	Timeout time.Duration
	DryRun  bool
	Logger  *slog.Logger
}

// NewClient builds a Client. A nil logger falls back to slog.Default().
func NewClient(o Options) *Client {
	logger := o.Logger
	if logger == nil {
		logger = slog.Default()
	}
	timeout := o.Timeout
	if timeout <= 0 {
		timeout = 15 * time.Second
	}
	return &Client{
		BaseURL: strings.TrimRight(o.BaseURL, "/"),
		HTTP:    &http.Client{Timeout: timeout},
		DryRun:  o.DryRun,
		Logger:  logger,
	}
}

// Captured returns the dry-run request log.
func (c *Client) Captured() []IngestRequest { return append([]IngestRequest(nil), c.captured...) }

// Ingest submits a single evidence item (POST /api/v1/evidence/ingest).
func (c *Client) Ingest(ctx context.Context, req IngestRequest) (IngestResult, error) {
	if c.DryRun {
		c.captured = append(c.captured, req)
		c.Logger.Info("dry-run ingest", "control", req.ControlID, "app", req.ApplicationSlug,
			"source", req.SourceSystem)
		return IngestResult{
			ApplicationSlug: req.ApplicationSlug, ControlID: req.ControlID,
			Framework: req.Framework, SourceSystem: req.SourceSystem, Outcome: "DRY_RUN",
		}, nil
	}
	var out IngestResult
	if err := c.post(ctx, "/api/v1/evidence/ingest", req, &out); err != nil {
		return IngestResult{}, err
	}
	return out, nil
}

// SubmitBulk submits many items in one call (POST /api/v1/evidence/bulk).
func (c *Client) SubmitBulk(ctx context.Context, reqs []IngestRequest) (BulkIngestResponse, error) {
	if c.DryRun {
		c.captured = append(c.captured, reqs...)
		for _, r := range reqs {
			c.Logger.Info("dry-run submit", "control", r.ControlID, "app", r.ApplicationSlug,
				"source", r.SourceSystem, "bytes", len(r.ContentBase64))
		}
		return BulkIngestResponse{Received: len(reqs)}, nil
	}
	var out BulkIngestResponse
	if err := c.post(ctx, "/api/v1/evidence/bulk", reqs, &out); err != nil {
		return BulkIngestResponse{}, err
	}
	return out, nil
}

// Submit satisfies the agent.Submitter interface (structural).
func (c *Client) Submit(ctx context.Context, reqs []IngestRequest) (BulkIngestResponse, error) {
	return c.SubmitBulk(ctx, reqs)
}

func (c *Client) post(ctx context.Context, path string, body, out any) error {
	buf, err := json.Marshal(body)
	if err != nil {
		return fmt.Errorf("marshal %s: %w", path, err)
	}
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, c.BaseURL+path, bytes.NewReader(buf))
	if err != nil {
		return err
	}
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Accept", "application/json")

	resp, err := c.HTTP.Do(req)
	if err != nil {
		return fmt.Errorf("POST %s: %w", path, err)
	}
	defer resp.Body.Close()

	raw, _ := io.ReadAll(io.LimitReader(resp.Body, 1<<20))
	if resp.StatusCode >= 300 {
		var eb ErrorBody
		if json.Unmarshal(raw, &eb) == nil && eb.Message != "" {
			return fmt.Errorf("POST %s: %d %s", path, resp.StatusCode, eb.Message)
		}
		return fmt.Errorf("POST %s: %d %s", path, resp.StatusCode, strings.TrimSpace(string(raw)))
	}
	if out == nil {
		return nil
	}
	if err := json.Unmarshal(raw, out); err != nil {
		return fmt.Errorf("decode %s response: %w", path, err)
	}
	return nil
}
