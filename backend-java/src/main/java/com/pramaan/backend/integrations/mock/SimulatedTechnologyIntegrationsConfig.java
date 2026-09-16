package com.pramaan.backend.integrations.mock;

import com.pramaan.backend.integrations.EnterpriseIntegration;
import com.pramaan.backend.integrations.mock.SimulatedTechnologyIntegration.SimCheck;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the Use Case 1 simulated infra-technology collectors as scheduler
 * evidence sources: {@code SIM_POSTGRES}, {@code SIM_MYSQL}, {@code SIM_NGINX},
 * {@code SIM_TOMCAT}. Enabled with the Phase 1 mock integrations.
 */
@Configuration
@ConditionalOnProperty(prefix = "pramaan.integrations", name = "mock-enabled",
        havingValue = "true", matchIfMissing = true)
class SimulatedTechnologyIntegrationsConfig {

    @Bean
    EnterpriseIntegration simPostgres() {
        return new SimulatedTechnologyIntegration() {
            @Override public String sourceSystem() { return "SIM_POSTGRES"; }
            @Override protected String technology() { return "postgresql"; }
            @Override protected String collector() { return "database"; }
            @Override protected List<SimCheck> checks() {
                return List.of(
                        new SimCheck("DB_BASELINING", "DBBL-C8", "DB-PG-01",
                                "Encryption in Transit", "on", "on", "PASS"),
                        new SimCheck("DB_BASELINING", "DBBL-C9", "DB-PG-02",
                                "Database Audit Logging", "on", "on", "PASS"),
                        new SimCheck("DB_BASELINING", "DBBL-C4", "DB-PG-03",
                                "Password Policy", "scram-sha-256", "scram-sha-256", "PASS"),
                        new SimCheck("DB_BASELINING", "DBBL-C11", "DB-PG-04",
                                "Secure Database Configuration", "0", "0", "PASS"));
            }
        };
    }

    @Bean
    EnterpriseIntegration simMysql() {
        return new SimulatedTechnologyIntegration() {
            @Override public String sourceSystem() { return "SIM_MYSQL"; }
            @Override protected String technology() { return "mysql"; }
            @Override protected String collector() { return "database"; }
            @Override protected List<SimCheck> checks() {
                return List.of(
                        new SimCheck("DB_BASELINING", "DBBL-C8", "DB-MY-01",
                                "Encryption in Transit", "on", "on", "PASS"),
                        new SimCheck("DB_BASELINING", "DBBL-C9", "DB-MY-02",
                                "Database Audit Logging", "true", "true", "PASS"),
                        new SimCheck("DB_BASELINING", "DBBL-C12", "DB-MY-03",
                                "Unnecessary Feature Disablement (local file import)", "off", "off", "PASS"));
            }
        };
    }

    @Bean
    EnterpriseIntegration simNginx() {
        return new SimulatedTechnologyIntegration() {
            @Override public String sourceSystem() { return "SIM_NGINX"; }
            @Override protected String technology() { return "nginx"; }
            @Override protected String collector() { return "middleware"; }
            @Override protected List<SimCheck> checks() {
                return List.of(
                        new SimCheck("NGINX_BASELINING", "NGBL-C5", "MW-01",
                                "TLS Protocol Compliance", "TLSv1.2,TLSv1.3", "no legacy TLS", "PASS"),
                        new SimCheck("NGINX_BASELINING", "NGBL-C9", "MW-02",
                                "Server Information Protection", "server_tokens off", "off", "PASS"),
                        new SimCheck("NGINX_BASELINING", "NGBL-C8", "MW-03",
                                "Secure HTTP Headers (HSTS)", "present", "present", "PASS"));
            }
        };
    }

    @Bean
    EnterpriseIntegration simTomcat() {
        return new SimulatedTechnologyIntegration() {
            @Override public String sourceSystem() { return "SIM_TOMCAT"; }
            @Override protected String technology() { return "tomcat"; }
            @Override protected String collector() { return "middleware"; }
            @Override protected List<SimCheck> checks() {
                // No xlsx framework covers Tomcat/middleware baselining specifically
                // (only NGINX Baselining is technology-specific) — filed against ITPP,
                // the general IT-policy framework, as the nearest catalog fit.
                return List.of(
                        new SimCheck("ITPP", "ITPP-C9", "MW-01",
                                "Encryption in Transit", "TLSv1.2,TLSv1.3", "no legacy TLS", "PASS"),
                        new SimCheck("ITPP", "ITPP-C5", "MW-04",
                                "Secure Configuration (hot auto-deploy disabled)", "false", "false", "PASS"));
            }
        };
    }
}
