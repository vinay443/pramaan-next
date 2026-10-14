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
    void environmentIsIncludedWhenPresentAndOmittedCleanlyWhenBlank() {
        Instant at = Instant.parse("2026-10-13T07:05:09Z");
        assertThat(EvidenceNaming.standardName("net-banking", "PCI-DSS-6.2", "CODE-REVIEW", "uat", at, 1))
                .isEqualTo("net-banking_PCI-DSS-6.2_CODE-REVIEW_UAT_20261013_001");
        for (String blank : new String[]{null, "", "  "}) {
            String name = EvidenceNaming.standardName("net-banking", "PCI-DSS-6.2", "CODE-REVIEW", blank, at, 1);
            assertThat(name).isEqualTo("net-banking_PCI-DSS-6.2_CODE-REVIEW_20261013_001")
                    .doesNotContain("__").doesNotContain("null");
        }
    }

    @Test
    void distinctRecordsOnTheSameDayGetDifferentNames() {
        Instant at = Instant.parse("2026-10-13T07:05:09Z");
        String prefix = EvidenceNaming.namePrefix("net-banking", "PCI-DSS-6.2", "CODE-REVIEW", "UAT", at);
        int first = EvidenceNaming.resolveSequence(prefix, null, List.of());
        String firstName = EvidenceNaming.standardName("net-banking", "PCI-DSS-6.2", "CODE-REVIEW", "UAT", at, first);
        int second = EvidenceNaming.resolveSequence(prefix, null, List.of(firstName));
        String secondName = EvidenceNaming.standardName("net-banking", "PCI-DSS-6.2", "CODE-REVIEW", "UAT", at, second);
        assertThat(firstName).endsWith("_001");
        assertThat(secondName).endsWith("_002").isNotEqualTo(firstName);
        // a different environment is its own series
        assertThat(EvidenceNaming.resolveSequence(
                EvidenceNaming.namePrefix("net-banking", "PCI-DSS-6.2", "CODE-REVIEW", "DEV", at), null,
                List.of(firstName, secondName))).isEqualTo(1);
    }

    @Test
    void newVersionOfTheSameRecordKeepsItsIdentity() {
        Instant at = Instant.parse("2026-10-13T07:05:09Z");
        String prefix = EvidenceNaming.namePrefix("net-banking", "PCI-DSS-6.2", "CODE-REVIEW", "UAT", at);
        String current = prefix + "_002";
        // other records hold 001..003, but this record keeps 002 when a new version arrives
        assertThat(EvidenceNaming.resolveSequence(prefix, current,
                List.of(prefix + "_001", prefix + "_003"))).isEqualTo(2);
        // legacy titles without an environment are never matched by an environment-bearing prefix
        assertThat(EvidenceNaming.resolveSequence(prefix, "net-banking_PCI-DSS-6.2_CODE-REVIEW_20261013_001",
                List.of())).isEqualTo(1);
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

    @Test
    void editedSequenceIsKeptWhenFreeAndAdvancesPastEveryHolderOfTheNewPrefix() {
        String prefix = "app_CTRL_TYPE_PROD_20260305";
        String old = "app_CTRL_TYPE_UAT_20260305_004";
        assertThat(EvidenceNaming.resolveEditedSequence(prefix, old, java.util.List.of())).isEqualTo(4);
        assertThat(EvidenceNaming.resolveEditedSequence(prefix, old,
                java.util.List.of(prefix + "_001", prefix + "_002"))).isEqualTo(4);
        assertThat(EvidenceNaming.resolveEditedSequence(prefix, old,
                java.util.List.of(prefix + "_004", prefix + "_002"))).isEqualTo(5);
        // a title that does not look like a generated name falls back to the next free number
        assertThat(EvidenceNaming.resolveEditedSequence(prefix, "report.pdf", java.util.List.of(prefix + "_001")))
                .isEqualTo(2);
        assertThat(EvidenceNaming.titlePattern("a_b%c")).isEqualTo("a!_b!%c!_%");
    }
}
