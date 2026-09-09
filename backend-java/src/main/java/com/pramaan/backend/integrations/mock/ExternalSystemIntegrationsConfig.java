package com.pramaan.backend.integrations.mock;

import com.pramaan.backend.config.PramaanProperties;
import com.pramaan.backend.config.PramaanProperties.Integrations.Endpoint;
import com.pramaan.backend.integrations.EnterpriseIntegration;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Use Case 15 — SharePoint and ServiceNow evidence sources. Deterministic mock
 * implementations (the {@code mock} flag is on by default and no credentials are
 * read or stored — only a secret-free base URL from {@code pramaan.integrations.*}).
 * They register as scheduler sources {@code SHAREPOINT} / {@code SERVICENOW} and
 * feed the same {@code SchedulerRunExecutor → EvidenceIngestionService} path as
 * every other source; a live adapter can replace either bean behind
 * {@link EnterpriseIntegration} with no caller changes.
 */
@Configuration
@ConditionalOnProperty(prefix = "pramaan.integrations", name = "mock-enabled",
        havingValue = "true", matchIfMissing = true)
class ExternalSystemIntegrationsConfig {

    private static final Logger log = LoggerFactory.getLogger(ExternalSystemIntegrationsConfig.class);

    @Bean
    EnterpriseIntegration sharePointIntegration(PramaanProperties props) {
        Endpoint cfg = props.integrations().sharepointOrDefault();
        String baseUrl = cfg.baseUrlOrDefault("https://contoso.sharepoint.com/sites/compliance");
        log.info("SharePoint integration: mock={} site={}", cfg.mockOrDefault(), baseUrl);
        return new AbstractMockIntegration() {
            @Override public String sourceSystem() { return "SHAREPOINT"; }
            @Override public boolean mock() { return cfg.mockOrDefault(); }
            @Override protected Map<String, String> extraMetadata() {
                return Map.of("system", "sharepoint", "system.baseUrl", baseUrl, "system.mode",
                        cfg.mockOrDefault() ? "mock" : "live");
            }
            @Override protected List<String[]> catalog() {
                return List.of(
                        new String[]{"ITPP", "ITPP-DOC-03", "policy_document"},
                        new String[]{"DPSC", "DPSC-GOV-01", "governance_pack"},
                        new String[]{"PCI_DSS", "PCI-DSS-12.1", "security_policy"});
            }
        };
    }

    @Bean
    EnterpriseIntegration serviceNowIntegration(PramaanProperties props) {
        Endpoint cfg = props.integrations().servicenowOrDefault();
        String baseUrl = cfg.baseUrlOrDefault("https://dev00000.service-now.com");
        log.info("ServiceNow integration: mock={} instance={}", cfg.mockOrDefault(), baseUrl);
        return new AbstractMockIntegration() {
            @Override public String sourceSystem() { return "SERVICENOW"; }
            @Override public boolean mock() { return cfg.mockOrDefault(); }
            @Override protected Map<String, String> extraMetadata() {
                return Map.of("system", "servicenow", "system.baseUrl", baseUrl, "system.mode",
                        cfg.mockOrDefault() ? "mock" : "live");
            }
            @Override protected List<String[]> catalog() {
                return List.of(
                        new String[]{"ITPP", "ITPP-CHG-02", "change_request"},
                        new String[]{"ITPP", "ITPP-INC-01", "incident_record"},
                        new String[]{"PCI_DSS", "PCI-DSS-12.10", "incident_response_plan"});
            }
        };
    }
}
