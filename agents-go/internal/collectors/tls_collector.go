package collectors

import (
	"context"
	"crypto/ecdsa"
	"crypto/rsa"
	"crypto/tls"
	"crypto/x509"
	"fmt"
	"net"
	"strings"
	"time"

	"github.com/pramaan/pramaan-next/agents-go/internal/agent"
)

// TLSCollector inspects the TLS endpoint and leaf certificate of a target.
// Real mode dials target.Address (host:port) with verification disabled to read
// the presented chain; simulation mode uses deterministic synthetic values.
type TLSCollector struct {
	// DialTimeout bounds a real handshake. Zero -> 5s.
	DialTimeout time.Duration
}

func NewTLSCollector() *TLSCollector { return &TLSCollector{DialTimeout: 5 * time.Second} }

func (c *TLSCollector) Type() string           { return "tls" }
func (c *TLSCollector) Technologies() []string { return []string{"tls"} }
func (c *TLSCollector) Supports(t agent.Target) bool {
	return t.Kind == "tls" || t.Technology == "tls"
}

func (c *TLSCollector) Checks() []agent.Check {
	return []agent.Check{
		{
			ID: "TLS-01", ControlID: "TLS-PROTOCOL-VERSION", Framework: "PCI_DSS",
			Title: "Negotiated TLS version is 1.2 or higher", Expected: ">= TLS1.2",
			Eval: func(f agent.Facts) (string, string, string) {
				return agent.ExpectOneOf([]string{"TLS1.2", "TLS1.3"}, f.Get("tls_version"))
			},
		},
		{
			ID: "TLS-02", ControlID: "TLS-CERT-EXPIRY", Framework: "C-SITE",
			Title: "Leaf certificate has > 30 days remaining", Expected: "days_to_expiry > 30",
			Eval: func(f agent.Facts) (string, string, string) {
				d := f.Get("cert_days_to_expiry")
				n, ok := atoiSigned(d)
				if !ok {
					return agent.StatusError, d, "expiry not collected"
				}
				if n <= 0 {
					return agent.StatusFail, d, "certificate expired"
				}
				if n <= 30 {
					return agent.StatusFail, d, "expires within 30 days"
				}
				return agent.StatusPass, d, ""
			},
		},
		{
			ID: "TLS-03", ControlID: "TLS-KEY-STRENGTH", Framework: "C-SITE",
			Title: "Certificate key meets minimum strength", Expected: "RSA>=2048 or ECDSA",
			Eval: func(f agent.Facts) (string, string, string) {
				return agent.Expect("true", f.Get("cert_key_strong"))
			},
		},
		{
			ID: "TLS-04", ControlID: "TLS-CERT-TRUST", Framework: "DPSC",
			Title: "Certificate is CA-issued (not self-signed)", Expected: "self_signed=false",
			Eval: func(f agent.Facts) (string, string, string) {
				return agent.Expect("false", f.Get("cert_self_signed"))
			},
		},
	}
}

func (c *TLSCollector) Collect(cc agent.CollectionContext, t agent.Target) agent.Result {
	now := cc.Time()
	res := agent.Result{Target: t, Simulated: cc.Simulation, StartedAt: now}

	var facts agent.Facts
	if cc.Simulation {
		facts = c.simulatedFacts(t, now)
	} else {
		f, err := c.liveFacts(cc.Ctx, t, now)
		facts = f
		if err != nil {
			res.Err = err.Error()
		}
	}

	res.Facts = facts
	res.Findings = agent.RunChecks(c.Checks(), facts)
	res.Evidence = evidenceFor(t, c.Type(), "tls", cc.Simulation, now, facts, res.Findings)
	res.EndedAt = cc.Time()
	return res
}

func (c *TLSCollector) simulatedFacts(t agent.Target, now time.Time) agent.Facts {
	notAfter := simCertNotAfter(now)
	host := hostOnly(t.Address)
	if host == "" {
		host = "app.internal"
	}
	return agent.Facts{
		"target_address":      t.Address,
		"tls_version":         "TLS1.3",
		"cert_subject_cn":     host,
		"cert_issuer_cn":      "Pramaan Internal Issuing CA",
		"cert_not_after":      notAfter.UTC().Format(time.RFC3339),
		"cert_days_to_expiry": numStr(int(notAfter.Sub(now).Hours() / 24)),
		"cert_key_type":       "RSA",
		"cert_key_bits":       "2048",
		"cert_key_strong":     "true",
		"cert_self_signed":    "false",
		"cert_sans":           host,
	}
}

func (c *TLSCollector) liveFacts(ctx context.Context, t agent.Target, now time.Time) (agent.Facts, error) {
	facts := agent.Facts{"target_address": t.Address}
	if t.Address == "" {
		return facts, fmt.Errorf("tls target %q has no address", t.Name)
	}
	timeout := c.DialTimeout
	if timeout <= 0 {
		timeout = 5 * time.Second
	}
	if ctx == nil {
		ctx = context.Background()
	}
	dialCtx, cancel := context.WithTimeout(ctx, timeout)
	defer cancel()

	dialer := &tls.Dialer{
		NetDialer: &net.Dialer{},
		Config:    &tls.Config{InsecureSkipVerify: true, ServerName: hostOnly(t.Address)}, //nolint:gosec // inspection only
	}
	conn, err := dialer.DialContext(dialCtx, "tcp", withDefaultPort(t.Address))
	if err != nil {
		return facts, fmt.Errorf("tls dial %s: %w", t.Address, err)
	}
	defer conn.Close()

	state := conn.(*tls.Conn).ConnectionState()
	facts["tls_version"] = tlsVersionString(state.Version)
	if len(state.PeerCertificates) == 0 {
		return facts, fmt.Errorf("no peer certificate presented by %s", t.Address)
	}
	leaf := state.PeerCertificates[0]
	facts["cert_subject_cn"] = leaf.Subject.CommonName
	facts["cert_issuer_cn"] = leaf.Issuer.CommonName
	facts["cert_not_after"] = leaf.NotAfter.UTC().Format(time.RFC3339)
	facts["cert_days_to_expiry"] = numStr(int(leaf.NotAfter.Sub(now).Hours() / 24))
	facts["cert_self_signed"] = boolStr(isSelfSigned(leaf))
	facts["cert_sans"] = strings.Join(leaf.DNSNames, ",")
	kt, bits, strong := keyStrength(leaf)
	facts["cert_key_type"] = kt
	facts["cert_key_bits"] = numStr(bits)
	facts["cert_key_strong"] = boolStr(strong)
	return facts, nil
}

// --- helpers --------------------------------------------------------------

func tlsVersionString(v uint16) string {
	switch v {
	case tls.VersionTLS13:
		return "TLS1.3"
	case tls.VersionTLS12:
		return "TLS1.2"
	case tls.VersionTLS11:
		return "TLS1.1"
	case tls.VersionTLS10:
		return "TLS1.0"
	default:
		return fmt.Sprintf("0x%04x", v)
	}
}

func isSelfSigned(c *x509.Certificate) bool {
	if c.Subject.String() != c.Issuer.String() {
		return false
	}
	return c.CheckSignatureFrom(c) == nil
}

func keyStrength(c *x509.Certificate) (string, int, bool) {
	switch pub := c.PublicKey.(type) {
	case *rsa.PublicKey:
		bits := pub.N.BitLen()
		return "RSA", bits, bits >= 2048
	case *ecdsa.PublicKey:
		bits := pub.Curve.Params().BitSize
		return "ECDSA", bits, bits >= 256
	default:
		return "UNKNOWN", 0, false
	}
}

func hostOnly(addr string) string {
	if addr == "" {
		return ""
	}
	if h, _, err := net.SplitHostPort(addr); err == nil {
		return h
	}
	return addr
}

func withDefaultPort(addr string) string {
	if _, _, err := net.SplitHostPort(addr); err == nil {
		return addr
	}
	return net.JoinHostPort(addr, "443")
}

func atoiSigned(s string) (int, bool) {
	s = strings.TrimSpace(s)
	if s == "" {
		return 0, false
	}
	neg := false
	if strings.HasPrefix(s, "-") {
		neg = true
		s = s[1:]
	}
	n := 0
	for _, r := range s {
		if r < '0' || r > '9' {
			return 0, false
		}
		n = n*10 + int(r-'0')
	}
	if neg {
		n = -n
	}
	return n, true
}
