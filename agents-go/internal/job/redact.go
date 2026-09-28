package job

// paramKeys returns only the parameter *names* of a job request, never their
// values, for safe inclusion in log lines. Parameter values routinely
// contain file paths and, in a real deployment, potentially other
// operator-supplied strings that should not be persisted to logs — only the
// fixed, approved key names are ever logged.
func paramKeys(params map[string]string) []string {
	keys := make([]string, 0, len(params))
	for k := range params {
		keys = append(keys, k)
	}
	return keys
}
