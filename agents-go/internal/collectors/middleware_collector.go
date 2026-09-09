package collectors

import (
	"strings"

	"github.com/pramaan/pramaan-next/agents-go/internal/agent"
)

// MiddlewareCollector gathers web/app-server hardening signals from a config file.
// Technologies: nginx, tomcat. Real mode reads the config from target param
// "configPath"; simulation mode uses a synthetic config.
type MiddlewareCollector struct{}

func NewMiddlewareCollector() *MiddlewareCollector { return &MiddlewareCollector{} }

func (c *MiddlewareCollector) Type() string           { return "middleware" }
func (c *MiddlewareCollector) Technologies() []string { return []string{"nginx", "tomcat"} }
func (c *MiddlewareCollector) Supports(t agent.Target) bool {
	return t.Kind == "middleware" || t.Technology == "nginx" || t.Technology == "tomcat"
}

func (c *MiddlewareCollector) Checks() []agent.Check {
	return []agent.Check{
		{
			ID: "MW-01", ControlID: "MW-TLS-VERSION", Framework: "PCI_DSS",
			Title: "Only TLS 1.2+ protocols enabled", Expected: "no TLSv1/TLSv1.1",
			Eval: func(f agent.Facts) (string, string, string) {
				return agent.Expect("true", f.Get("tls_modern_only"))
			},
		},
		{
			ID: "MW-02", ControlID: "MW-BANNER-SUPPRESSION", Framework: "C-SITE",
			Title: "Server version banner suppressed", Expected: "server_tokens off",
			Eval: func(f agent.Facts) (string, string, string) {
				return agent.Expect("true", f.Get("banner_suppressed"))
			},
		},
		{
			ID: "MW-03", ControlID: "MW-HSTS", Framework: "DPSC",
			Title: "HSTS response header configured", Expected: "Strict-Transport-Security present",
			Eval: func(f agent.Facts) (string, string, string) {
				return agent.Expect("true", f.Get("hsts_configured"))
			},
		},
		{
			ID: "MW-04", ControlID: "MW-AUTODEPLOY", Framework: "C-SITE",
			Title: "Hot auto-deploy disabled (Tomcat)", Expected: "autoDeploy=false",
			Eval: func(f agent.Facts) (string, string, string) {
				if f.Get("technology") != "tomcat" {
					return agent.StatusNotApplicable, f.Get("technology"), "check applies to Tomcat"
				}
				return agent.Expect("true", f.Get("autodeploy_disabled"))
			},
		},
	}
}

func (c *MiddlewareCollector) Collect(cc agent.CollectionContext, t agent.Target) agent.Result {
	now := cc.Time()
	res := agent.Result{Target: t, Simulated: cc.Simulation, StartedAt: now}

	tech := t.Technology
	if tech == "" {
		tech = "nginx"
	}
	var conf string
	if cc.Simulation {
		if tech == "tomcat" {
			conf = simTomcatServerXML
		} else {
			conf = simNginxConf
		}
	} else {
		conf = firstReadable(t.Param("configPath"))
		if conf == "" {
			res.Err = "no middleware config readable; run with SIMULATION_MODE or set configPath param"
		}
	}

	lower := strings.ToLower(conf)
	hasLegacyTLS := strings.Contains(lower, "tlsv1 ") || strings.Contains(lower, "tlsv1.0") ||
		strings.Contains(lower, "tlsv1.1") || strings.Contains(lower, "sslv3")
	hasModernTLS := strings.Contains(lower, "tlsv1.2") || strings.Contains(lower, "tlsv1.3")

	facts := agent.Facts{
		"technology":          tech,
		"tls_modern_only":     boolStr(hasModernTLS && !hasLegacyTLS),
		"banner_suppressed":   boolStr(strings.Contains(lower, "server_tokens off")),
		"hsts_configured":     boolStr(strings.Contains(lower, "strict-transport-security")),
		"autodeploy_disabled": boolStr(strings.Contains(lower, `autodeploy="false"`)),
	}

	res.Facts = facts
	res.Findings = agent.RunChecks(c.Checks(), facts)
	res.Evidence = evidenceFor(t, c.Type(), tech, cc.Simulation, now, facts, res.Findings)
	res.EndedAt = cc.Time()
	return res
}
