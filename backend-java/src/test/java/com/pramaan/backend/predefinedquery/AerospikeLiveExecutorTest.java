package com.pramaan.backend.predefinedquery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class AerospikeLiveExecutorTest {

    private static final Instant NOW = Instant.parse("2026-09-15T00:00:00Z");

    private static PredefinedQuery aerospikeControl(String controlId, String command) {
        return new PredefinedQuery(controlId, "Aerospike", "Aerospike control", command,
                List.of("DB Baselining"), "Aerospike asinfo/asadm output", true, "Ready",
                "Capacity & Performance Management");
    }

    private static PredefinedQuery postgresControl() {
        return new PredefinedQuery("DB-001", "PostgreSQL", "SSL Enabled", "SHOW ssl;",
                List.of("PCI_DSS"), "SQL Output", true, "Ready", "Encryption in Transit");
    }

    @Test
    void supportsTheSeventeenAsinfoControls() {
        AerospikeLiveExecutor executor = new AerospikeLiveExecutor("localhost", 3000, "test");

        assertThat(executor.supports(aerospikeControl("ASX-001", "asinfo -v \"build\""))).isTrue();
        assertThat(executor.supports(aerospikeControl("ASX-004",
                "asinfo -v \"get-config:context=namespace;id=${AEROSPIKE_NAMESPACE:-test}\""))).isTrue();
    }

    @Test
    void supportsShowUsersAndShowStatButNotShowConfig() {
        AerospikeLiveExecutor executor = new AerospikeLiveExecutor("localhost", 3000, "test");

        assertThat(executor.supports(aerospikeControl("ASX-006", "asadm -e \"show users\""))).isTrue();
        assertThat(executor.supports(aerospikeControl("ASX-013", "asadm -e \"show stat\""))).isTrue();
        // No 1:1 client call for asadm's aggregated "show config" report — stays on
        // SIMULATED_FALLBACK rather than being approximated (see class Javadoc).
        assertThat(executor.supports(aerospikeControl("ASX-010", "asadm -e \"show config\""))).isFalse();
    }

    @Test
    void doesNotClaimNonAerospikeControls() {
        AerospikeLiveExecutor executor = new AerospikeLiveExecutor("localhost", 3000, "test");

        assertThat(executor.supports(postgresControl())).isFalse();
    }

    @Test
    void unreachableClusterFailsLoudlyRatherThanSilentlyFallingBackToSimulated() {
        // Port 1 is not Aerospike and (per ClientPolicy's default failIfNotConnected=true
        // and a 1s timeout) the client constructor rejects it quickly — the same shape of
        // failure as the container being stopped, without needing one running for this test.
        AerospikeLiveExecutor executor = new AerospikeLiveExecutor("localhost", 1, "test");
        PredefinedQuery q = aerospikeControl("ASX-001", "asinfo -v \"build\"");

        assertThatThrownBy(() -> executor.execute(q, "net-banking", NOW))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ASX-001");
    }
}
