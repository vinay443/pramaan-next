package job

import (
	"encoding/json"
	"fmt"
)

// DecodeRequest parses one JSON job request. A malformed (non-JSON, or
// structurally wrong-typed) payload is rejected here, before it ever reaches
// validateShape — this is the MALFORMED_REQUEST case for input that isn't
// even valid JSON, as distinct from valid JSON that's missing fields.
func DecodeRequest(data []byte) (JobRequest, error) {
	var req JobRequest
	if err := json.Unmarshal(data, &req); err != nil {
		return JobRequest{}, fmt.Errorf("malformed job request: %w", err)
	}
	return req, nil
}
