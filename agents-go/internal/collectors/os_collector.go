package collectors

import (
	"runtime"
	"strings"

	"github.com/pramaan/pramaan-next/agents-go/internal/agent"
)

// OSCollector gathers server / operating-system hardening signals.
// Technology: linux. Real mode reads /etc/os-release and /etc/ssh/sshd_config
// (or paths from target params); simulation mode uses synthetic sources.
type OSCollector struct{}

func NewOSCollector() *OSCollector { return &OSCollector{} }

func (c *OSCollector) Type() string           { return "os" }
func (c *OSCollector) Technologies() []string { return []string{"linux"} }
func (c *OSCollector) Supports(t agent.Target) bool {
	return t.Kind == "os" || t.Technology == "linux"
}

func (c *OSCollector) Checks() []agent.Check {
	return []agent.Check{
		{
			ID: "OS-LNX-01", ControlID: "OS-BASELINE-VERSION", Framework: "ITPP",
			Title: "Supported OS version is identifiable", Expected: "os_version present",
			Eval: func(f agent.Facts) (string, string, string) {
				if v := f.Get("os_version"); v != "" {
					return agent.StatusPass, v, ""
				}
				return agent.StatusFail, "", "os-release did not yield a version"
			},
		},
		{
			ID: "OS-LNX-02", ControlID: "OS-SSH-ROOT-LOGIN", Framework: "C-SITE",
			Title: "SSH root login disabled", Expected: "PermitRootLogin=no",
			Eval: func(f agent.Facts) (string, string, string) {
				return agent.Expect("no", strings.ToLower(f.Get("sshd_permitrootlogin")))
			},
		},
		{
			ID: "OS-LNX-03", ControlID: "OS-SSH-PASSWORD-AUTH", Framework: "C-SITE",
			Title: "SSH password authentication disabled", Expected: "PasswordAuthentication=no",
			Eval: func(f agent.Facts) (string, string, string) {
				return agent.Expect("no", strings.ToLower(f.Get("sshd_passwordauthentication")))
			},
		},
		{
			ID: "OS-LNX-04", ControlID: "OS-AUDIT-LOGGING", Framework: "PCI_DSS",
			Title: "Host audit logging enabled", Expected: "auditd enabled",
			Eval: func(f agent.Facts) (string, string, string) {
				return agent.Expect("1", f.Get("auditd_enabled"))
			},
		},
	}
}

func (c *OSCollector) Collect(cc agent.CollectionContext, t agent.Target) agent.Result {
	now := cc.Time()
	res := agent.Result{Target: t, Simulated: cc.Simulation, StartedAt: now}

	var osRelease, sshd, audit string
	if cc.Simulation {
		osRelease, sshd, audit = simOSReleaseLinux, simSSHDConfig, simAuditStatus
	} else {
		osRelease = firstReadable(t.Param("osReleasePath"), "/etc/os-release")
		sshd = firstReadable(t.Param("sshdConfigPath"), "/etc/ssh/sshd_config")
		audit = firstReadable(t.Param("auditStatusPath"))
		if osRelease == "" && sshd == "" {
			res.Err = "no OS sources readable on this host; run with SIMULATION_MODE or provide *Path params"
		}
	}

	facts := agent.Facts{
		"host_goos": runtime.GOOS,
	}
	for k, v := range parseKV(osRelease, "=") {
		switch k {
		case "ID":
			facts["os_id"] = strings.Trim(v, `"`)
		case "VERSION_ID":
			facts["os_version"] = strings.Trim(v, `"`)
		case "NAME":
			facts["os_name"] = strings.Trim(v, `"`)
		}
	}
	for k, v := range parseKV(sshd, " ") {
		facts["sshd_"+strings.ToLower(k)] = v
	}
	for k, v := range parseKV(audit, " ") {
		if k == "enabled" {
			facts["auditd_enabled"] = v
		}
	}

	res.Facts = facts
	res.Findings = agent.RunChecks(c.Checks(), facts)
	res.Evidence = evidenceFor(t, c.Type(), "linux", cc.Simulation, now, facts, res.Findings)
	res.EndedAt = cc.Time()
	return res
}

// parseKV parses simple "key<sep>value" lines (last one wins), ignoring blanks/comments.
func parseKV(text, sep string) map[string]string {
	out := map[string]string{}
	for _, line := range strings.Split(text, "\n") {
		line = strings.TrimSpace(line)
		if line == "" || strings.HasPrefix(line, "#") {
			continue
		}
		k, v, ok := strings.Cut(line, sep)
		if !ok {
			continue
		}
		out[strings.TrimSpace(k)] = strings.TrimSpace(v)
	}
	return out
}

func firstReadable(paths ...string) string {
	for _, p := range paths {
		if s, ok := readFileMaybe(p); ok {
			return s
		}
	}
	return ""
}
