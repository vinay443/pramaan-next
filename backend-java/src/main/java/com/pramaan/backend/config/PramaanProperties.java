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
        Evidence evidence,
        /** Dev/demo only. Gates throwaway helpers like the demo-evidence seeder. Never true in prod. */
        boolean demoMode) {

    /** Use Case 5 — canonical role catalogue for the ECS Admin console. */
    public record Admin(List<String> roles) {}

    /** Use Case 13 — evidence lifecycle. */
    public record Evidence(Integer retentionDays, Integer auditorSlaDays) {
        public int retentionDaysOrDefault() {
            return retentionDays != null && retentionDays > 0 ? retentionDays : 365;
        }

        /** App Owner dashboard — Auditor SLA target: days from submission to review. */
        public int auditorSlaDaysOrDefault() {
            return auditorSlaDays != null && auditorSlaDays > 0 ? auditorSlaDays : 5;
        }
    }

    public Evidence evidenceOrDefault() {
        return evidence != null ? evidence : new Evidence(365, 5);
    }

    public record ObjectStore(String driver, Filesystem filesystem, Minio minio) {
        public record Filesystem(String root) {}

        /** MinIO (S3-compatible) connection — used when {@code driver=minio}. */
        public record Minio(String endpoint, String accessKey, String secretKey, String bucket) {
            public String endpointOrDefault() {
                return endpoint == null || endpoint.isBlank() ? "http://localhost:9000" : endpoint;
            }
            public String accessKeyOrDefault() {
                return accessKey == null || accessKey.isBlank() ? "pramaan" : accessKey;
            }
            public String secretKeyOrDefault() {
                return secretKey == null || secretKey.isBlank() ? "pramaan-secret" : secretKey;
            }
            public String bucketOrDefault() {
                return bucket == null || bucket.isBlank() ? "pramaan-evidence" : bucket;
            }
        }
    }

    public record Integrations(boolean mockEnabled, Endpoint sharepoint, Endpoint servicenow, Endpoint grc) {

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

        /** Outbound GRC sync target. Mock by default — see {@code GrcSyncService}. */
        public Endpoint grcOrDefault() {
            return grc != null ? grc : new Endpoint(true, null);
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
