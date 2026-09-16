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
                        new String[]{"PCI_DSS", "PCI-C6", "change_ticket"},
                        new String[]{"ITPP", "ITPP-C12", "change_ticket"});
            }
        };
    }

    @Bean
    EnterpriseIntegration mockServiceNow() {
        return new AbstractMockIntegration() {
            @Override public String sourceSystem() { return "MOCK_SERVICENOW"; }
            @Override protected List<String[]> catalog() {
                return List.of(
                        new String[]{"ITPP", "ITPP-C14", "incident_record"},
                        new String[]{"PCI_DSS", "PCI-C12", "incident_record"});
            }
        };
    }

    @Bean
    EnterpriseIntegration mockGithub() {
        return new AbstractMockIntegration() {
            @Override public String sourceSystem() { return "MOCK_GITHUB"; }
            @Override protected List<String[]> catalog() {
                return List.of(
                        new String[]{"PCI_DSS", "PCI-C6", "branch_protection"},
                        new String[]{"DPSC", "DPSC-C14", "pull_request_review"});
            }
        };
    }

    @Bean
    EnterpriseIntegration mockConfluence() {
        return new AbstractMockIntegration() {
            @Override public String sourceSystem() { return "MOCK_CONFLUENCE"; }
            @Override protected List<String[]> catalog() {
                return List.of(
                        new String[]{"C-SITE", "CSITE-C1", "policy_page"},
                        new String[]{"DPSC", "DPSC-C1", "policy_page"});
            }
        };
    }
}
