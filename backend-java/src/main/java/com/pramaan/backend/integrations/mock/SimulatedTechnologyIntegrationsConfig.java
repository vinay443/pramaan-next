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
                        new SimCheck("PCI_DSS", "DB-TLS-IN-TRANSIT", "DB-PG-01",
                                "TLS enabled for database connections", "on", "on", "PASS"),
                        new SimCheck("PCI_DSS", "DB-AUDIT-LOGGING", "DB-PG-02",
                                "Connection/statement auditing enabled", "on", "on", "PASS"),
                        new SimCheck("C-SITE", "DB-PASSWORD-STORAGE", "DB-PG-03",
                                "Strong password hashing", "scram-sha-256", "scram-sha-256", "PASS"),
                        new SimCheck("C-SITE", "DB-AUTH-NO-TRUST", "DB-PG-04",
                                "No trust auth in pg_hba", "0", "0", "PASS"));
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
                        new SimCheck("PCI_DSS", "DB-TLS-IN-TRANSIT", "DB-MY-01",
                                "TLS required for MySQL connections", "on", "on", "PASS"),
                        new SimCheck("PCI_DSS", "DB-AUDIT-LOGGING", "DB-MY-02",
                                "MySQL audit/general logging enabled", "true", "true", "PASS"),
                        new SimCheck("C-SITE", "DB-AUTH-NO-TRUST", "DB-MY-03",
                                "Local file import disabled", "off", "off", "PASS"));
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
                        new SimCheck("PCI_DSS", "MW-TLS-VERSION", "MW-01",
                                "Only TLS 1.2+ protocols enabled", "TLSv1.2,TLSv1.3", "no legacy TLS", "PASS"),
                        new SimCheck("C-SITE", "MW-BANNER-SUPPRESSION", "MW-02",
                                "Server version banner suppressed", "server_tokens off", "off", "PASS"),
                        new SimCheck("DPSC", "MW-HSTS", "MW-03",
                                "HSTS response header configured", "present", "present", "PASS"));
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
                return List.of(
                        new SimCheck("PCI_DSS", "MW-TLS-VERSION", "MW-01",
                                "Only TLS 1.2+ protocols enabled", "TLSv1.2,TLSv1.3", "no legacy TLS", "PASS"),
                        new SimCheck("C-SITE", "MW-AUTODEPLOY", "MW-04",
                                "Hot auto-deploy disabled", "false", "false", "PASS"));
            }
        };
    }
}
