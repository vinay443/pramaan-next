package com.pramaan.backend.predefinedquery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class OracleLiveExecutorTest {

    private static final Instant NOW = Instant.parse("2026-09-15T00:00:00Z");

    private static PredefinedQuery oracleControl(String controlId, String command) {
        return new PredefinedQuery(controlId, "Oracle", "Oracle control", command,
                List.of("DB Baselining"), "SQL Output", true, "Ready", "Secure Configuration");
    }

    private static PredefinedQuery postgresControl() {
        return new PredefinedQuery("DB-001", "PostgreSQL", "SSL Enabled", "SHOW ssl;",
                List.of("PCI_DSS"), "SQL Output", true, "Ready", "Encryption in Transit");
    }

    private static OracleLiveExecutor executor() {
        return new OracleLiveExecutor("localhost", 1521, "FREEPDB1", "system", "pramaan");
    }

    @Test
    void supportsOnlyOracleControlsWithACommand() {
        OracleLiveExecutor executor = executor();

        assertThat(executor.supports(oracleControl("ORX-001", "SELECT * FROM v$version;"))).isTrue();
        assertThat(executor.supports(oracleControl("ORX-006", "SELECT username FROM dba_users;"))).isTrue();
        assertThat(executor.supports(oracleControl("ORX-EMPTY", ""))).isFalse();
        assertThat(executor.supports(oracleControl("ORX-NULL", null))).isFalse();
    }

    @Test
    void doesNotClaimNonOracleControls() {
        assertThat(executor().supports(postgresControl())).isFalse();
    }

    @Test
    void nonSelectCommandIsRefusedWithoutReachingTheDatabase() {
        OracleLiveExecutor executor = executor();

        assertThatThrownBy(() -> executor.execute(
                oracleControl("ORX-DROP", "DROP TABLE dba_users"), "net-banking", NOW))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("read-only");
    }

    @Test
    void showIsNotTreatedAsReadOnlyOnOracleUnlikePostgresMysqlYugabyte() {
        // Oracle has no bare SHOW statement — the read-only gate here is
        // deliberately narrower than Postgres/Aurora/Yugabyte's SELECT|SHOW.
        OracleLiveExecutor executor = executor();

        assertThatThrownBy(() -> executor.execute(
                oracleControl("ORX-SHOW", "SHOW PARAMETER audit_trail"), "net-banking", NOW))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("read-only");
    }

    @Test
    void unreachableDatabaseFailsLoudlyRatherThanSilentlyFallingBackToSimulated() {
        // Port 1 has nothing listening — the same shape of failure as the container
        // being stopped, without needing one running for this test.
        OracleLiveExecutor executor = new OracleLiveExecutor("localhost", 1, "FREEPDB1", "system", "pramaan");
        PredefinedQuery q = oracleControl("ORX-001", "SELECT * FROM v$version;");

        assertThatThrownBy(() -> executor.execute(q, "net-banking", NOW))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ORX-001");
    }
}
