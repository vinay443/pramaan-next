package com.pramaan.backend.evidence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EvidenceNamingTest {

    @Test
    void standardNameFollowsTheConvention() {
        String name = EvidenceNaming.standardName("Net Banking", "db-tls-in-transit", "DB-CONFIG",
                Instant.parse("2026-09-08T07:05:09Z"), 1);
        assertThat(name).isEqualTo("net-banking_DB-TLS-IN-TRANSIT_DB-CONFIG_20260908_001");
    }

    @Test
    void standardTagsCarryEveryFacet() {
        Map<String, String> tags = EvidenceNaming.standardTags("payments", "MySQL",
                List.of("PCI_DSS", "DPSC"), "DB-AUDIT-LOGGING", "DB-CONFIG",
                "payments_DB-AUDIT-LOGGING_DB-CONFIG_20260908_003", "Audit log export",
                "Scheduled", 3);
        assertThat(tags).containsEntry("application", "payments")
                .containsEntry("technology", "mysql")
                .containsEntry("control", "DB-AUDIT-LOGGING")
                .containsEntry("framework", "PCI_DSS")
                .containsEntry("frameworks", "PCI_DSS,DPSC")
                .containsEntry("evidenceType", "DB-CONFIG")
                .containsEntry("name", "payments_DB-AUDIT-LOGGING_DB-CONFIG_20260908_003")
                .containsEntry("sourceTitle", "Audit log export")
                .containsEntry("collectionMethod", "scheduled")
                .containsEntry("version", "v3");
    }

    @Test
    void evidenceTypeResolvesFromControlPrefixThenExplicitTag() {
        assertThat(EvidenceNaming.resolveEvidenceType(Map.of(), Map.of(), "OS-SSH-ROOT-LOGIN", "AGENT_OS_LINUX"))
                .isEqualTo("HOST-CONFIG");
        assertThat(EvidenceNaming.resolveEvidenceType(Map.of(), Map.of(), "ITPP-CHG-02", "MOCK_JIRA"))
                .isEqualTo("CHANGE-TICKET");
        assertThat(EvidenceNaming.resolveEvidenceType(Map.of("evidenceType", "screenshot"), Map.of(),
                "DB-TLS-IN-TRANSIT", "MANUAL")).isEqualTo("SCREENSHOT");
    }

    @Test
    void technologyResolvesFromSourceSystemWhenNoTagPresent() {
        assertThat(EvidenceNaming.resolveTechnology(Map.of(), Map.of(), "AGENT_DATABASE_POSTGRESQL"))
                .isEqualTo("postgresql");
        assertThat(EvidenceNaming.resolveTechnology(Map.of("technology", "nginx"), Map.of(), "BULK_UPLOAD"))
                .isEqualTo("nginx");
        assertThat(EvidenceNaming.resolveTechnology(Map.of(), Map.of(), "MOCK_JIRA"))
                .isEqualTo("unknown");
    }

    @Test
    void collectionMethodInfersFromChannelAndCollector() {
        assertThat(EvidenceNaming.resolveCollectionMethod(Map.of("ingest.channel", "bulk-upload"), Map.of()))
                .isEqualTo("bulk");
        assertThat(EvidenceNaming.resolveCollectionMethod(Map.of(), Map.of("collector", "database")))
                .isEqualTo("scheduled");
        assertThat(EvidenceNaming.resolveCollectionMethod(Map.of(), Map.of())).isEqualTo("manual");
    }
}
