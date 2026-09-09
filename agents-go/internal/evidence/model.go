// Package evidence contains the wire types and HTTP client for submitting
// evidence to the Pramaan backend.
//
// The request/response shapes here MUST match contracts/openapi/pramaan-backend.yaml
// (components.schemas.IngestRequest / IngestResult / BulkIngestResponse). Do not
// add fields that are not in the contract.
package evidence

// IngestRequest mirrors contract schema "IngestRequest".
// Exactly one of ContentBase64 / ContentText must be set.
type IngestRequest struct {
	ApplicationSlug string            `json:"applicationSlug"`
	ControlID       string            `json:"controlId"`
	Framework       string            `json:"framework"`
	SourceSystem    string            `json:"sourceSystem"`
	SourceObjectID  string            `json:"sourceObjectId,omitempty"`
	Title           string            `json:"title,omitempty"`
	ContentType     string            `json:"contentType,omitempty"`
	ContentBase64   string            `json:"contentBase64,omitempty"`
	ContentText     string            `json:"contentText,omitempty"`
	CollectedAt     string            `json:"collectedAt,omitempty"` // RFC3339
	CollectedBy     string            `json:"collectedBy,omitempty"`
	Metadata        map[string]string `json:"metadata,omitempty"`
	Tags            map[string]string `json:"tags,omitempty"`
}

// IngestResult mirrors contract schema "IngestResult".
type IngestResult struct {
	EvidenceID      string `json:"evidenceId"`
	EvidenceKey     string `json:"evidenceKey"`
	ApplicationSlug string `json:"applicationSlug"`
	ControlID       string `json:"controlId"`
	Framework       string `json:"framework"`
	SourceSystem    string `json:"sourceSystem"`
	Outcome         string `json:"outcome"` // CREATED | NEW_VERSION | DUPLICATE
	Version         int    `json:"version"`
	SHA256          string `json:"sha256"`
	SizeBytes       int64  `json:"sizeBytes"`
}

// BulkError mirrors contract schema BulkIngestResponse.errors[].
type BulkError struct {
	Index   int    `json:"index"`
	Message string `json:"message"`
}

// BulkIngestResponse mirrors contract schema "BulkIngestResponse".
type BulkIngestResponse struct {
	Received    int            `json:"received"`
	Created     int            `json:"created"`
	NewVersions int            `json:"newVersions"`
	Duplicates  int            `json:"duplicates"`
	Failed      int            `json:"failed"`
	Results     []IngestResult `json:"results"`
	Errors      []BulkError    `json:"errors"`
}

// ErrorBody mirrors contract schema "ErrorBody".
type ErrorBody struct {
	Timestamp string `json:"timestamp"`
	Status    int    `json:"status"`
	Error     string `json:"error"`
	Message   string `json:"message"`
}
