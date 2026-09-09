package collectors

import (
	"encoding/json"
	"strings"

	"github.com/pramaan/pramaan-next/agents-go/internal/agent"
)

// DatabaseCollector gathers database hardening signals from a settings snapshot.
//
// Technologies: postgresql, mysql. Phase 1 bundles no DB driver: real mode reads
// a JSON settings export from the target param "snapshotPath" (produced by an ops
// export of pg_settings + pg_hba, or SHOW GLOBAL VARIABLES for MySQL). Simulation
// mode uses a synthetic snapshot per technology.
type DatabaseCollector struct{}

func NewDatabaseCollector() *DatabaseCollector { return &DatabaseCollector{} }

func (c *DatabaseCollector) Type() string           { return "database" }
func (c *DatabaseCollector) Technologies() []string { return []string{"postgresql", "mysql"} }
func (c *DatabaseCollector) Supports(t agent.Target) bool {
	return t.Kind == "database" || t.Technology == "postgresql" || t.Technology == "mysql"
}

func (c *DatabaseCollector) Checks() []agent.Check {
	pg := func(fn func(agent.Facts) (string, string, string)) func(agent.Facts) (string, string, string) {
		return func(f agent.Facts) (string, string, string) {
			if f.Get("technology") != "postgresql" {
				return agent.StatusNotApplicable, f.Get("technology"), "check applies to postgresql"
			}
			return fn(f)
		}
	}
	my := func(fn func(agent.Facts) (string, string, string)) func(agent.Facts) (string, string, string) {
		return func(f agent.Facts) (string, string, string) {
			if f.Get("technology") != "mysql" {
				return agent.StatusNotApplicable, f.Get("technology"), "check applies to mysql"
			}
			return fn(f)
		}
	}
	return []agent.Check{
		{
			ID: "DB-PG-01", ControlID: "DB-TLS-IN-TRANSIT", Framework: "PCI_DSS",
			Title: "TLS enabled for database connections", Expected: "ssl=on",
			Eval: pg(func(f agent.Facts) (string, string, string) {
				return agent.Expect("on", f.Get("ssl"))
			}),
		},
		{
			ID: "DB-PG-02", ControlID: "DB-AUDIT-LOGGING", Framework: "PCI_DSS",
			Title: "Connection / statement auditing enabled", Expected: "log_connections=on",
			Eval: pg(func(f agent.Facts) (string, string, string) {
				return agent.Expect("on", f.Get("log_connections"))
			}),
		},
		{
			ID: "DB-PG-03", ControlID: "DB-PASSWORD-STORAGE", Framework: "C-SITE",
			Title: "Strong password hashing", Expected: "password_encryption=scram-sha-256",
			Eval: pg(func(f agent.Facts) (string, string, string) {
				return agent.Expect("scram-sha-256", f.Get("password_encryption"))
			}),
		},
		{
			ID: "DB-PG-04", ControlID: "DB-AUTH-NO-TRUST", Framework: "C-SITE",
			Title: "No 'trust' authentication in pg_hba", Expected: "hba_trust_rules=0",
			Eval: pg(func(f agent.Facts) (string, string, string) {
				return agent.Expect("0", f.Get("hba_trust_rules"))
			}),
		},
		{
			ID: "DB-MY-01", ControlID: "DB-TLS-IN-TRANSIT", Framework: "PCI_DSS",
			Title: "TLS required for MySQL connections", Expected: "require_secure_transport=on",
			Eval: my(func(f agent.Facts) (string, string, string) {
				return agent.Expect("on", f.Get("require_secure_transport"))
			}),
		},
		{
			ID: "DB-MY-02", ControlID: "DB-AUDIT-LOGGING", Framework: "PCI_DSS",
			Title: "MySQL audit / general logging enabled", Expected: "audit_log or general_log on",
			Eval: my(func(f agent.Facts) (string, string, string) {
				return agent.Expect("true", f.Get("audit_logging_enabled"))
			}),
		},
		{
			ID: "DB-MY-03", ControlID: "DB-AUTH-NO-TRUST", Framework: "C-SITE",
			Title: "Local file import disabled (local_infile off)", Expected: "local_infile=off",
			Eval: my(func(f agent.Facts) (string, string, string) {
				return agent.Expect("off", f.Get("local_infile"))
			}),
		},
	}
}

type pgSnapshot struct {
	SSL                string `json:"ssl"`
	LogConnections     string `json:"log_connections"`
	LogDisconnections  string `json:"log_disconnections"`
	PasswordEncryption string `json:"password_encryption"`
	LogStatement       string `json:"log_statement"`
	SharedPreload      string `json:"shared_preload_libraries"`
	PgHBA              []struct {
		Type   string `json:"type"`
		Method string `json:"method"`
	} `json:"pg_hba"`
}

type mySQLSnapshot struct {
	RequireSecureTransport string `json:"require_secure_transport"`
	HaveSSL                string `json:"have_ssl"`
	GeneralLog             string `json:"general_log"`
	AuditLogPlugin         string `json:"audit_log"`
	LocalInfile            string `json:"local_infile"`
	LogBinTrustFunc        string `json:"log_bin_trust_function_creators"`
}

func (c *DatabaseCollector) Collect(cc agent.CollectionContext, t agent.Target) agent.Result {
	now := cc.Time()
	res := agent.Result{Target: t, Simulated: cc.Simulation, StartedAt: now}

	tech := t.Technology
	if tech != "mysql" {
		tech = "postgresql"
	}

	raw := simPostgresSettings
	if tech == "mysql" {
		raw = simMySQLSettings
	}
	if !cc.Simulation {
		raw = firstReadable(t.Param("snapshotPath"))
		if raw == "" {
			res.Err = "no database snapshot available; run with SIMULATION_MODE or set snapshotPath param"
		}
	}

	facts := agent.Facts{"technology": tech}
	if tech == "mysql" {
		var snap mySQLSnapshot
		if err := json.Unmarshal([]byte(raw), &snap); err != nil {
			res.Err = "snapshot is not valid JSON: " + err.Error()
		} else {
			facts["require_secure_transport"] = onOff(snap.RequireSecureTransport)
			facts["have_ssl"] = strings.ToLower(snap.HaveSSL)
			facts["general_log"] = onOff(snap.GeneralLog)
			facts["local_infile"] = onOff(snap.LocalInfile)
			auditOn := onOff(snap.GeneralLog) == "on" ||
				strings.Contains(strings.ToLower(snap.AuditLogPlugin), "active") ||
				onOff(snap.AuditLogPlugin) == "on"
			facts["audit_logging_enabled"] = boolStr(auditOn)
		}
	} else {
		var snap pgSnapshot
		if err := json.Unmarshal([]byte(raw), &snap); err != nil {
			res.Err = "snapshot is not valid JSON: " + err.Error()
		} else {
			facts["ssl"] = strings.ToLower(snap.SSL)
			facts["log_connections"] = strings.ToLower(snap.LogConnections)
			facts["log_disconnections"] = strings.ToLower(snap.LogDisconnections)
			facts["password_encryption"] = strings.ToLower(snap.PasswordEncryption)
			facts["log_statement"] = strings.ToLower(snap.LogStatement)
			facts["shared_preload_libraries"] = snap.SharedPreload
			trust := 0
			for _, r := range snap.PgHBA {
				if strings.EqualFold(r.Method, "trust") {
					trust++
				}
			}
			facts["hba_trust_rules"] = numStr(trust)
			facts["hba_rules"] = numStr(len(snap.PgHBA))
		}
	}

	res.Facts = facts
	res.Findings = agent.RunChecks(c.Checks(), facts)
	res.Evidence = evidenceFor(t, c.Type(), tech, cc.Simulation, now, facts, res.Findings)
	res.EndedAt = cc.Time()
	return res
}

// onOff normalizes MySQL boolean-ish variable values (ON/OFF/1/0/YES/NO) to "on"/"off".
func onOff(v string) string {
	switch strings.ToLower(strings.TrimSpace(v)) {
	case "on", "1", "yes", "true":
		return "on"
	case "off", "0", "no", "false", "":
		return "off"
	default:
		return strings.ToLower(strings.TrimSpace(v))
	}
}
