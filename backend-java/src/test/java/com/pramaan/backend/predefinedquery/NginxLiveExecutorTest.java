package com.pramaan.backend.predefinedquery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class NginxLiveExecutorTest {

    private static final Instant NOW = Instant.parse("2026-09-15T00:00:00Z");

    private static PredefinedQuery nginxControl(String controlId, String command) {
        return new PredefinedQuery(controlId, "NGINX", "NGINX control", command,
                List.of("Middleware Baselining"), "Configuration output", true, "Ready",
                "Secure Configuration");
    }

    private static PredefinedQuery postgresControl() {
        return new PredefinedQuery("DB-001", "PostgreSQL", "SSL Enabled", "SHOW ssl;",
                List.of("PCI_DSS"), "SQL Output", true, "Ready", "Encryption in Transit");
    }

    @Test
    void supportsNginxTAndTheGrepBasedConfigControls() {
        NginxLiveExecutor executor = new NginxLiveExecutor("localhost", 8081, "/nginx_status", "nginx.conf");

        assertThat(executor.supports(nginxControl("MW-001", "nginx -T"))).isTrue();
        assertThat(executor.supports(nginxControl("NGX-003",
                "grep -R \"ssl_protocols\" /etc/nginx /etc/nginx/conf.d 2>/dev/null || true"))).isTrue();
        assertThat(executor.supports(nginxControl("NGX-005",
                "grep -R \"server_tokens\" /etc/nginx /etc/nginx/conf.d 2>/dev/null || true"))).isTrue();
    }

    @Test
    void supportsAStubStatusCommandByContent() {
        NginxLiveExecutor executor = new NginxLiveExecutor("localhost", 8081, "/nginx_status", "nginx.conf");

        assertThat(executor.supports(nginxControl("NGX-STATUS", "curl -s http://localhost/nginx_status"))).isTrue();
    }

    @Test
    void doesNotClaimVersionConfigTestOrEnabledSites() {
        NginxLiveExecutor executor = new NginxLiveExecutor("localhost", 8081, "/nginx_status", "nginx.conf");

        // No 1:1 client call without shell exec into the container — see class Javadoc.
        assertThat(executor.supports(nginxControl("NGX-001", "nginx -v 2>&1"))).isFalse();
        assertThat(executor.supports(nginxControl("NGX-002", "nginx -t 2>&1"))).isFalse();
        assertThat(executor.supports(nginxControl("NGX-008",
                "find /etc/nginx/sites-enabled /etc/nginx/conf.d -maxdepth 2 -type f 2>/dev/null"))).isFalse();
    }

    @Test
    void doesNotClaimNonNginxControls() {
        NginxLiveExecutor executor = new NginxLiveExecutor("localhost", 8081, "/nginx_status", "nginx.conf");

        assertThat(executor.supports(postgresControl())).isFalse();
    }

    @Test
    void unreachableStatusEndpointFailsLoudlyRatherThanSilentlyFallingBackToSimulated() {
        // Port 1 has nothing listening — the same shape of failure as the container
        // being stopped, without needing one running for this test.
        NginxLiveExecutor executor = new NginxLiveExecutor("localhost", 1, "/nginx_status", "nginx.conf");
        PredefinedQuery q = nginxControl("NGX-STATUS", "curl -s http://localhost/nginx_status");

        assertThatThrownBy(() -> executor.execute(q, "net-banking", NOW))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("NGX-STATUS");
    }

    @Test
    void missingConfigFileFailsLoudlyRatherThanSilentlyFallingBackToSimulated() {
        NginxLiveExecutor executor = new NginxLiveExecutor("localhost", 8081, "/nginx_status",
                "does-not-exist/nginx.conf");
        PredefinedQuery q = nginxControl("MW-001", "nginx -T");

        assertThatThrownBy(() -> executor.execute(q, "net-banking", NOW))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MW-001");
    }
}
