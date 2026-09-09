package com.pramaan.backend.insight;

import static org.assertj.core.api.Assertions.assertThat;

import com.pramaan.backend.evidence.EvidenceDtos.IngestRequest;
import com.pramaan.backend.evidence.EvidenceDtos.IngestResult;
import com.pramaan.backend.evidence.EvidenceIngestionService;
import com.pramaan.backend.insight.InsightDtos.EvidenceContext;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class EvidenceContextServiceTest {

    @Autowired EvidenceIngestionService ingestion;
    @Autowired EvidenceContextService context;

    @Test
    void contextExposesTechnologyMethodAndFrameworksForTheControl() {
        IngestResult r = ingestion.ingest(new IngestRequest("net-banking", "DB-TLS-IN-TRANSIT",
                "PCI_DSS", "SIM_POSTGRES", "pg/db-tls", null, "application/json", null,
                "{\"status\":\"pass\"}", Instant.parse("2026-09-01T00:00:00Z"), "agent",
                Map.of("collector", "database", "technology", "postgresql"),
                Map.of("technology", "postgresql", "collectionMethod", "scheduled")));

        EvidenceContext c = context.forEvidence(UUID.fromString(r.evidenceId()));
        assertThat(c.technology()).isEqualTo("postgresql");
        assertThat(c.collectionMethod()).isEqualTo("scheduled");
        assertThat(c.satisfiesControls()).containsExactly("DB-TLS-IN-TRANSIT");
        assertThat(c.frameworks()).contains("PCI_DSS");
        assertThat(c.collectedAt()).isEqualTo(Instant.parse("2026-09-01T00:00:00Z"));
    }
}
