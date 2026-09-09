package com.pramaan.backend.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Typed view of the {@code pramaan.*} configuration tree. */
@ConfigurationProperties(prefix = "pramaan")
public record PramaanProperties(
        ObjectStore objectStore,
        Integrations integrations,
        Scheduler scheduler,
        Seed seed,
        Admin admin,
        Evidence evidence) {

    /** Use Case 5 — canonical role catalogue for the ECS Admin console. */
    public record Admin(List<String> roles) {}

    /** Use Case 13 — evidence lifecycle. */
    public record Evidence(Integer retentionDays) {
        public int retentionDaysOrDefault() {
            return retentionDays != null && retentionDays > 0 ? retentionDays : 365;
        }
    }

    public Evidence evidenceOrDefault() {
        return evidence != null ? evidence : new Evidence(365);
    }

    public record ObjectStore(String driver, Filesystem filesystem) {
        public record Filesystem(String root) {}
    }

    public record Integrations(boolean mockEnabled, Endpoint sharepoint, Endpoint servicenow) {

        /** Connection info for an external system. Never holds credentials. */
        public record Endpoint(Boolean mock, String baseUrl) {
            public boolean mockOrDefault() {
                return mock == null || mock;
            }
            public String baseUrlOrDefault(String fallback) {
                return baseUrl == null || baseUrl.isBlank() ? fallback : baseUrl;
            }
        }

        public Endpoint sharepointOrDefault() {
            return sharepoint != null ? sharepoint : new Endpoint(true, null);
        }

        public Endpoint servicenowOrDefault() {
            return servicenow != null ? servicenow : new Endpoint(true, null);
        }
    }

    public record Scheduler(
            boolean autoEnabled,
            boolean dispatch,
            String cron,
            List<String> defaultSources,
            int staleAfterDays) {}

    public record Seed(boolean applicationsEnabled) {}
}
