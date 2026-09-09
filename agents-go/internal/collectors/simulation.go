package collectors

import "time"

// simClock is a fixed reference used to derive deterministic synthetic dates
// (e.g. certificate validity) relative to the run time.
func simCertNotAfter(now time.Time) time.Time  { return now.AddDate(0, 0, 210) }
func simCertNotBefore(now time.Time) time.Time { return now.AddDate(0, 0, -30) }

// Synthetic raw sources. Content is stable so repeated runs dedup in the backend.

const simOSReleaseLinux = `NAME="Red Hat Enterprise Linux"
VERSION="9.4 (Plow)"
ID="rhel"
VERSION_ID="9.4"
PLATFORM_ID="platform:el9"
`

const simSSHDConfig = `# managed sshd_config (simulated)
Protocol 2
PermitRootLogin no
PasswordAuthentication no
PermitEmptyPasswords no
X11Forwarding no
ClientAliveInterval 300
MaxAuthTries 4
`

const simAuditStatus = `enabled 1
flag 2
pid 1123
backlog_limit 8192
`

// simulated PostgreSQL settings snapshot (subset of pg_settings + pg_hba summary)
const simPostgresSettings = `{
  "ssl": "on",
  "log_connections": "on",
  "log_disconnections": "on",
  "password_encryption": "scram-sha-256",
  "log_statement": "ddl",
  "shared_preload_libraries": "pgaudit",
  "pg_hba": [
    {"type": "hostssl", "database": "all", "user": "all", "address": "10.0.0.0/8", "method": "scram-sha-256"},
    {"type": "local", "database": "all", "user": "all", "method": "peer"}
  ]
}`

// simulated MySQL global-variables snapshot (subset of SHOW GLOBAL VARIABLES)
const simMySQLSettings = `{
  "require_secure_transport": "ON",
  "have_ssl": "YES",
  "general_log": "ON",
  "audit_log": "ACTIVE",
  "local_infile": "OFF",
  "log_bin_trust_function_creators": "OFF"
}`

// simulated NGINX config fragment
const simNginxConf = `server {
    listen 443 ssl;
    server_name app.internal;
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_ciphers HIGH:!aNULL:!MD5;
    ssl_prefer_server_ciphers on;
    server_tokens off;
    add_header Strict-Transport-Security "max-age=63072000; includeSubDomains" always;
}
`

// simulated Tomcat server.xml fragment
const simTomcatServerXML = `<Server port="8005" shutdown="SHUTDOWN">
  <Service name="Catalina">
    <Connector port="8443" scheme="https" secure="true" SSLEnabled="true"
               sslProtocol="TLSv1.2" sslEnabledProtocols="TLSv1.2,TLSv1.3"/>
    <Engine name="Catalina" defaultHost="localhost">
      <Host name="localhost" appBase="webapps" autoDeploy="false" unpackWARs="false"/>
    </Engine>
  </Service>
</Server>
`

// simulated governance export (change management + source control + pipeline gates)
const simGovernanceSnapshot = `{
  "change_management": {
    "window_days": 30,
    "production_changes": 14,
    "changes_with_approved_ticket": 14,
    "emergency_changes": 1,
    "emergency_changes_reviewed": 1
  },
  "source_control": {
    "default_branch_protected": true,
    "required_approving_reviews": 2,
    "dismiss_stale_reviews": true,
    "require_code_owner_review": true
  },
  "pipeline": {
    "sast_enabled": true,
    "dependency_scan_enabled": true,
    "secret_scan_enabled": true,
    "build_gate_blocking": true
  }
}`
