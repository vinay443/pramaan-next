package com.pramaan.backend.integrations.mock;

import com.pramaan.backend.integrations.EnterpriseIntegration;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Registers deterministic mock enterprise integrations (Phase 1 default). */
@Configuration
@ConditionalOnProperty(prefix = "pramaan.integrations", name = "mock-enabled",
        havingValue = "true", matchIfMissing = true)
class MockIntegrationsConfig {

    @Bean
    EnterpriseIntegration mockJira() {
        return new AbstractMockIntegration() {
            @Override public String sourceSystem() { return "MOCK_JIRA"; }
            @Override protected List<String[]> catalog() {
                return List.of(
                        new String[]{"PCI_DSS", "PCI-DSS-6.3", "change_ticket"},
                        new String[]{"ITPP", "ITPP-CHG-02", "change_ticket"});
            }
        };
    }

    @Bean
    EnterpriseIntegration mockServiceNow() {
        return new AbstractMockIntegration() {
            @Override public String sourceSystem() { return "MOCK_SERVICENOW"; }
            @Override protected List<String[]> catalog() {
                return List.of(
                        new String[]{"ITPP", "ITPP-INC-01", "incident_record"},
                        new String[]{"PCI_DSS", "PCI-DSS-12.10", "incident_record"});
            }
        };
    }

    @Bean
    EnterpriseIntegration mockGithub() {
        return new AbstractMockIntegration() {
            @Override public String sourceSystem() { return "MOCK_GITHUB"; }
            @Override protected List<String[]> catalog() {
                return List.of(
                        new String[]{"PCI_DSS", "PCI-DSS-6.2", "branch_protection"},
                        new String[]{"DPSC", "DPSC-SDLC-04", "pull_request_review"});
            }
        };
    }

    @Bean
    EnterpriseIntegration mockConfluence() {
        return new AbstractMockIntegration() {
            @Override public String sourceSystem() { return "MOCK_CONFLUENCE"; }
            @Override protected List<String[]> catalog() {
                return List.of(
                        new String[]{"ITPP", "ITPP-DOC-03", "policy_page"},
                        new String[]{"DPSC", "DPSC-GOV-01", "policy_page"});
            }
        };
    }
}
