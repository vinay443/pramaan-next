package com.pramaan.backend.predefinedquery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class YugabyteLiveExecutorTest {

    private static final Instant NOW = Instant.parse("2026-09-15T00:00:00Z");

    private static PredefinedQuery yugabyteControl(String controlId, String command) {
        return new PredefinedQuery(controlId, "YugabyteDB", "YugabyteDB control", command,
                List.of("DB Baselining"), "Database configuration query output", true, "Ready",
                "Secure Configuration");
    }

    private static PredefinedQuery postgresControl() {
        return new PredefinedQuery("DB-001", "PostgreSQL", "SSL Enabled", "SHOW ssl;",
                List.of("PCI_DSS"), "SQL Output", true, "Ready", "Encryption in Transit");
    }

    private static YugabyteLiveExecutor executor() {
        return new YugabyteLiveExecutor("localhost", 5435, "yugabyte", "yugabyte", "");
    }

    @Test
    void supportsOnlyYugabyteDbControlsWithACommand() {
        YugabyteLiveExecutor executor = executor();

        assertThat(executor.supports(yugabyteControl("YBX-001", "SELECT * FROM yb_servers();"))).isTrue();
        assertThat(executor.supports(yugabyteControl("YBX-002", "SELECT version();"))).isTrue();
        assertThat(executor.supports(yugabyteControl("YBX-008", "SHOW ssl;"))).isTrue();
        assertThat(executor.supports(yugabyteControl("YBX-EMPTY", ""))).isFalse();
        assertThat(executor.supports(yugabyteControl("YBX-NULL", null))).isFalse();
    }

    @Test
    void doesNotClaimNonYugabyteControls() {
        assertThat(executor().supports(postgresControl())).isFalse();
    }

    @Test
    void nonReadOnlyCommandIsRefusedWithoutReachingTheDatabase() {
        YugabyteLiveExecutor executor = executor();

        assertThatThrownBy(() -> executor.execute(
                yugabyteControl("YBX-DROP", "DROP TABLE pg_roles"), "net-banking", NOW))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("read-only");
    }

    @Test
    void unreachableClusterFailsLoudlyRatherThanSilentlyFallingBackToSimulated() {
        // Port 1 has nothing listening — the same shape of failure as the container
        // being stopped, without needing one running for this test.
        YugabyteLiveExecutor executor = new YugabyteLiveExecutor("localhost", 1, "yugabyte", "yugabyte", "");
        PredefinedQuery q = yugabyteControl("YBX-002", "SELECT version();");

        assertThatThrownBy(() -> executor.execute(q, "net-banking", NOW))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("YBX-002");
    }
}
