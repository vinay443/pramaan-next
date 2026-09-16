package com.pramaan.backend.predefinedquery;

import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Chooses the active {@link PredefinedQueryExecutor} from
 * {@code pramaan.predefined-queries.mode} (SIMULATED | LIVE, default SIMULATED).
 * In LIVE mode, wires up one {@link TechnologyLiveExecutor} per technology that
 * has a real target — currently PostgreSQL (reuses the app's own datasource) and
 * Aerospike ({@code pramaan.aerospike.*}). A technology with no live executor
 * registered simply falls back to SIMULATED_FALLBACK per-control, it doesn't
 * disable LIVE mode for the technologies that do have one.
 */
@Configuration
class PredefinedQueryConfig {

    private static final Logger log = LoggerFactory.getLogger(PredefinedQueryConfig.class);

    @Bean
    PredefinedQueryExecutor predefinedQueryExecutor(
            @Value("${pramaan.predefined-queries.mode:SIMULATED}") String mode,
            ObjectProvider<JdbcTemplate> jdbcTemplate,
            @Value("${pramaan.aerospike.host:localhost}") String aerospikeHost,
            @Value("${pramaan.aerospike.port:3000}") int aerospikePort,
            @Value("${pramaan.aerospike.namespace:test}") String aerospikeNamespace) {
        boolean live = "LIVE".equalsIgnoreCase(mode == null ? "" : mode.trim());
        if (!live) {
            log.info("predefined-query executor: SIMULATED");
            return new SimulatedPredefinedQueryExecutor();
        }

        List<TechnologyLiveExecutor> liveExecutors = new ArrayList<>();
        JdbcTemplate jdbc = jdbcTemplate.getIfAvailable();
        if (jdbc != null) {
            liveExecutors.add(new PostgresLiveExecutor(jdbc));
        } else {
            log.warn("predefined-query mode=LIVE requested but no JdbcTemplate/datasource is configured "
                    + "— PostgreSQL controls will fall back to SIMULATED_FALLBACK");
        }
        liveExecutors.add(new AerospikeLiveExecutor(aerospikeHost, aerospikePort, aerospikeNamespace));

        log.info("predefined-query executor: LIVE (PostgreSQL + Aerospike controls executed for real when "
                + "their target is reachable; every other technology falls back to SIMULATED_FALLBACK)");
        return new LivePredefinedQueryExecutor(liveExecutors);
    }
}
