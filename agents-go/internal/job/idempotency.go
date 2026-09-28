package job

import (
	"encoding/json"
	"os"
	"path/filepath"
	"sync"
)

// Store records the terminal result of a completed job, keyed by job ID, so
// a retried submission of the same job ID replays the prior outcome instead
// of re-running the collector. Only deterministic terminal outcomes
// (SUCCEEDED / FAILED / REJECTED) are ever stored — see executor.go for why
// TIMEOUT and CANCELLED are excluded.
type Store interface {
	Get(jobID string) (JobResult, bool)
	Put(jobID string, result JobResult)
}

// MemoryStore is an in-process idempotency store, suitable for tests and for
// any long-lived process embedding this package directly.
type MemoryStore struct {
	mu   sync.Mutex
	data map[string]JobResult
}

// NewMemoryStore builds an empty in-memory store.
func NewMemoryStore() *MemoryStore { return &MemoryStore{data: map[string]JobResult{}} }

// Get implements Store.
func (s *MemoryStore) Get(jobID string) (JobResult, bool) {
	s.mu.Lock()
	defer s.mu.Unlock()
	r, ok := s.data[jobID]
	return r, ok
}

// Put implements Store.
func (s *MemoryStore) Put(jobID string, result JobResult) {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.data[jobID] = result
}

// FileStore persists results as one JSON file per job ID under a directory,
// so idempotent replay survives across separate CLI invocations of `agent
// job` (the process is short-lived; a purely in-memory store would not help
// a caller that retries by re-running the binary).
//
// The job ID is validated by validateShape (jobIDPattern) before it ever
// reaches this store, so it is always a safe file-name component — this
// store does not re-sanitize it, by design, to keep the safety check in one
// place (validate.go).
type FileStore struct {
	dir string
}

// NewFileStore ensures dir exists and returns a store backed by it.
func NewFileStore(dir string) (*FileStore, error) {
	if err := os.MkdirAll(dir, 0o755); err != nil {
		return nil, err
	}
	return &FileStore{dir: dir}, nil
}

func (s *FileStore) path(jobID string) string {
	return filepath.Join(s.dir, jobID+".json")
}

// Get implements Store.
func (s *FileStore) Get(jobID string) (JobResult, bool) {
	data, err := os.ReadFile(s.path(jobID))
	if err != nil {
		return JobResult{}, false
	}
	var r JobResult
	if json.Unmarshal(data, &r) != nil {
		return JobResult{}, false
	}
	return r, true
}

// Put implements Store.
func (s *FileStore) Put(jobID string, result JobResult) {
	data, err := json.MarshalIndent(result, "", "  ")
	if err != nil {
		return
	}
	_ = os.WriteFile(s.path(jobID), data, 0o644)
}
