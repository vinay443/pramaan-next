package com.pramaan.backend.predefinedquery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.pramaan.backend.predefinedquery.PredefinedQueryExecutor.Output;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

class PostgresLiveExecutorTest {

    private static final Instant NOW = Instant.parse("2026-09-15T00:00:00Z");

    private static PredefinedQuery postgresControl(String command) {
        return new PredefinedQuery("DB-001", "PostgreSQL", "SSL Enabled", command,
                List.of("PCI_DSS"), "SQL Output", true, "Ready", "Encryption in Transit");
    }

    private static PredefinedQuery linuxControl() {
        return new PredefinedQuery("LNX-007", "Linux", "SSH Root Login Disabled",
                "grep -i \"^PermitRootLogin\" /etc/ssh/sshd_config", List.of("CSITE"), "Shell Command",
                true, "Ready", "Remote Access");
    }

    @Test
    void supportsOnlyPostgresControlsWithACommand() {
        PostgresLiveExecutor executor = new PostgresLiveExecutor(mock(JdbcTemplate.class));

        assertThat(executor.supports(postgresControl("SHOW ssl;"))).isTrue();
        assertThat(executor.supports(linuxControl())).isFalse();
        assertThat(executor.supports(postgresControl(""))).isFalse();
        assertThat(executor.supports(postgresControl(null))).isFalse();
    }

    @Test
    void postgresControlExecutesForRealAndIsTaggedLive() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString())).thenReturn(List.of(Map.of("ssl", "on")));

        PostgresLiveExecutor executor = new PostgresLiveExecutor(jdbc);
        Output out = executor.execute(postgresControl("SHOW ssl;"), "net-banking", NOW);

        assertThat(out.metadata()).containsEntry("live", "true");
        assertThat(out.metadata()).containsEntry("executionMode", "LIVE");
        assertThat(out.tags()).containsEntry("live", "true");
        assertThat(out.tags()).containsEntry("executionMode", "LIVE");
        assertThat(out.content()).contains("\"ssl\"").contains("\"on\"");
    }

    @Test
    void nonReadOnlyCommandIsRefusedWithoutReachingJdbc() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        PostgresLiveExecutor executor = new PostgresLiveExecutor(jdbc);

        assertThatThrownBy(() -> executor.execute(postgresControl("DELETE FROM pg_settings"), "net-banking", NOW))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("read-only");
    }

    @Test
    void jdbcFailureIsWrappedWithTheControlId() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString())).thenThrow(new DataAccessResourceFailureException("connection refused"));
        PostgresLiveExecutor executor = new PostgresLiveExecutor(jdbc);

        assertThatThrownBy(() -> executor.execute(postgresControl("SHOW ssl;"), "net-banking", NOW))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DB-001");
    }
}
