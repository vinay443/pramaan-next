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
 * has a real target — currently PostgreSQL (reuses the app's own datasource),
 * Aerospike ({@code pramaan.aerospike.*}), NGINX ({@code pramaan.nginx.*}),
 * Aurora MySQL ({@code pramaan.aurora-mysql.*}), YugabyteDB
 * ({@code pramaan.yugabytedb.*}), Oracle ({@code pramaan.oracle.*}), and RHEL
 * ({@code pramaan.rhel.*}, over SSH). A
 * technology with no live executor registered simply falls back to
 * SIMULATED_FALLBACK per-control, it doesn't disable LIVE mode for the
 * technologies that do have one.
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
            @Value("${pramaan.aerospike.namespace:test}") String aerospikeNamespace,
            @Value("${pramaan.nginx.host:localhost}") String nginxHost,
            @Value("${pramaan.nginx.port:8081}") int nginxPort,
            @Value("${pramaan.nginx.status-path:/nginx_status}") String nginxStatusPath,
            @Value("${pramaan.nginx.config-path:../infra/nginx/nginx.conf}") String nginxConfigPath,
            @Value("${pramaan.aurora-mysql.host:localhost}") String auroraHost,
            @Value("${pramaan.aurora-mysql.port:3307}") int auroraPort,
            @Value("${pramaan.aurora-mysql.database:pramaan}") String auroraDatabase,
            @Value("${pramaan.aurora-mysql.user:root}") String auroraUser,
            @Value("${pramaan.aurora-mysql.password:pramaan}") String auroraPassword,
            @Value("${pramaan.yugabytedb.host:localhost}") String yugabyteHost,
            @Value("${pramaan.yugabytedb.port:5435}") int yugabytePort,
            @Value("${pramaan.yugabytedb.database:yugabyte}") String yugabyteDatabase,
            @Value("${pramaan.yugabytedb.user:yugabyte}") String yugabyteUser,
            @Value("${pramaan.yugabytedb.password:}") String yugabytePassword,
            @Value("${pramaan.oracle.host:localhost}") String oracleHost,
            @Value("${pramaan.oracle.port:1521}") int oraclePort,
            @Value("${pramaan.oracle.service-name:FREEPDB1}") String oracleServiceName,
            @Value("${pramaan.oracle.user:system}") String oracleUser,
            @Value("${pramaan.oracle.password:pramaan}") String oraclePassword,
            @Value("${pramaan.rhel.host:localhost}") String rhelHost,
            @Value("${pramaan.rhel.port:2222}") int rhelPort,
            @Value("${pramaan.rhel.user:root}") String rhelUser,
            @Value("${pramaan.rhel.password:}") String rhelPassword,
            @Value("${pramaan.rhel.private-key-path:}") String rhelPrivateKeyPath) {
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
        liveExecutors.add(new NginxLiveExecutor(nginxHost, nginxPort, nginxStatusPath, nginxConfigPath));
        liveExecutors.add(new AuroraMysqlLiveExecutor(auroraHost, auroraPort, auroraDatabase, auroraUser, auroraPassword));
        liveExecutors.add(new YugabyteLiveExecutor(
                yugabyteHost, yugabytePort, yugabyteDatabase, yugabyteUser, yugabytePassword));
        liveExecutors.add(new OracleLiveExecutor(
                oracleHost, oraclePort, oracleServiceName, oracleUser, oraclePassword));
        liveExecutors.add(new RhelLiveExecutor(
                rhelHost, rhelPort, rhelUser, rhelPassword, rhelPrivateKeyPath));

        log.info("predefined-query executor: LIVE (PostgreSQL + Aerospike + NGINX + Aurora MySQL + YugabyteDB "
                + "+ Oracle + RHEL controls executed for real when their target is reachable; every other "
                + "technology falls back to SIMULATED_FALLBACK)");
        return new LivePredefinedQueryExecutor(liveExecutors);
    }
}
