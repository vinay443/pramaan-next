package com.pramaan.backend.predefinedquery;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Chooses the active {@link PredefinedQueryExecutor} from
 * {@code pramaan.predefined-queries.mode} (SIMULATED | LIVE, default SIMULATED).
 */
@Configuration
class PredefinedQueryConfig {

    private static final Logger log = LoggerFactory.getLogger(PredefinedQueryConfig.class);

    @Bean
    PredefinedQueryExecutor predefinedQueryExecutor(
            @Value("${pramaan.predefined-queries.mode:SIMULATED}") String mode) {
        boolean live = "LIVE".equalsIgnoreCase(mode == null ? "" : mode.trim());
        log.info("predefined-query executor: {}", live ? "LIVE (stub)" : "SIMULATED");
        return live ? new LivePredefinedQueryExecutor() : new SimulatedPredefinedQueryExecutor();
    }
}
