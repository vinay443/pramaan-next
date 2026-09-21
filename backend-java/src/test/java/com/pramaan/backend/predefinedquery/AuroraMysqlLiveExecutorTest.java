package com.pramaan.backend.predefinedquery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class AuroraMysqlLiveExecutorTest {

    private static final Instant NOW = Instant.parse("2026-09-15T00:00:00Z");

    private static PredefinedQuery auroraControl(String controlId, String command) {
        return new PredefinedQuery(controlId, "Aurora MySQL", "Aurora MySQL control", command,
                List.of("DB Baselining"), "Database configuration query output", true, "Ready",
                "Secure Configuration");
    }

    private static PredefinedQuery postgresControl() {
        return new PredefinedQuery("DB-001", "PostgreSQL", "SSL Enabled", "SHOW ssl;",
                List.of("PCI_DSS"), "SQL Output", true, "Ready", "Encryption in Transit");
    }

    private static AuroraMysqlLiveExecutor executor() {
        return new AuroraMysqlLiveExecutor("localhost", 3306, "pramaan", "root", "pramaan");
    }

    @Test
    void supportsTheCatalogueSShowAndSelectControls() {
        AuroraMysqlLiveExecutor executor = executor();

        assertThat(executor.supports(auroraControl("MYX-001", "SHOW VARIABLES LIKE 'have_ssl';"))).isTrue();
        assertThat(executor.supports(auroraControl("MYX-005", "SELECT user, host, plugin FROM mysql.user;"))).isTrue();
        assertThat(executor.supports(auroraControl("MYX-012",
                "SELECT id, user, host, db, time, state FROM information_schema.processlist "
                        + "WHERE command <> 'Sleep' AND time > 300;"))).isTrue();
    }

    @Test
    void doesNotClaimGenuineAuroraOnlySurfaces() {
        AuroraMysqlLiveExecutor executor = executor();

        // No plain-MySQL equivalent — see class Javadoc.
        assertThat(executor.supports(auroraControl("MYX-REPLICA",
                "SELECT * FROM information_schema.replica_host_status;"))).isFalse();
        assertThat(executor.supports(auroraControl("MYX-FAILOVER", "SELECT aurora_version();"))).isFalse();
        assertThat(executor.supports(auroraControl("MYX-RDS", "CALL mysql.rds_set_configuration(...);"))).isFalse();
    }

    @Test
    void doesNotClaimNonAuroraControls() {
        assertThat(executor().supports(postgresControl())).isFalse();
    }

    @Test
    void nonReadOnlyCommandIsRefusedWithoutReachingTheDatabase() {
        AuroraMysqlLiveExecutor executor = executor();

        assertThatThrownBy(() -> executor.execute(
                auroraControl("MYX-DROP", "DROP TABLE mysql.user"), "net-banking", NOW))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("read-only");
    }

    @Test
    void unreachableDatabaseFailsLoudlyRatherThanSilentlyFallingBackToSimulated() {
        // Port 1 has nothing listening — the same shape of failure as the container
        // being stopped, without needing one running for this test.
        AuroraMysqlLiveExecutor executor = new AuroraMysqlLiveExecutor("localhost", 1, "pramaan", "root", "pramaan");
        PredefinedQuery q = auroraControl("MYX-001", "SHOW VARIABLES LIKE 'have_ssl';");

        assertThatThrownBy(() -> executor.execute(q, "net-banking", NOW))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MYX-001");
    }
}
