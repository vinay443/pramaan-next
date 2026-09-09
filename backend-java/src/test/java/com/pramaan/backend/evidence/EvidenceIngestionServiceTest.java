package com.pramaan.backend.evidence;

import static org.assertj.core.api.Assertions.assertThat;

import com.pramaan.backend.evidence.EvidenceDtos.EvidenceView;
import com.pramaan.backend.evidence.EvidenceDtos.IngestOutcome;
import com.pramaan.backend.evidence.EvidenceDtos.IngestRequest;
import com.pramaan.backend.evidence.EvidenceDtos.IngestResult;
import static org.mockito.Mockito.doReturn;

import com.pramaan.backend.evidence.EvidenceQueryService.EvidenceFilter;
import com.pramaan.backend.storage.ObjectStore;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class EvidenceIngestionServiceTest {

    @Autowired EvidenceIngestionService ingestion;
    @Autowired EvidenceQueryService queries;
    @SpyBean ObjectStore objectStore;

    private IngestRequest req(String text, Map<String, String> tags) {
        return new IngestRequest("net-banking", "PCI-DSS-6.3", "PCI_DSS", "MOCK_JIRA",
                "JIRA-1", "Change ticket", "text/plain", null, text,
                Instant.parse("2026-09-01T00:00:00Z"), "tester", Map.of("k", "v"), tags);
    }

    @Test
    void firstIngestCreatesVersionOneWithSha256() {
        IngestResult r = ingestion.ingest(req("hello-1", Map.of("stage", "collected")));
        assertThat(r.outcome()).isEqualTo(IngestOutcome.CREATED);
        assertThat(r.version()).isEqualTo(1);
        assertThat(r.sha256()).hasSize(64);

        EvidenceView v = queries.get(UUID.fromString(r.evidenceId()));
        assertThat(v.currentVersion()).isEqualTo(1);
        assertThat(v.tags()).containsEntry("stage", "collected");
        assertThat(v.latest().metadata()).containsEntry("k", "v");
    }

    @Test
    void sameContentIsDeduplicatedNoNewVersion() {
        IngestResult first = ingestion.ingest(req("identical", Map.of()));
        IngestResult second = ingestion.ingest(req("identical", Map.of()));
        assertThat(second.outcome()).isEqualTo(IngestOutcome.DUPLICATE);
        assertThat(second.evidenceId()).isEqualTo(first.evidenceId());
        assertThat(second.version()).isEqualTo(1);
        assertThat(queries.versions(UUID.fromString(first.evidenceId()))).hasSize(1);
    }

    @Test
    void changedContentAddsNewVersionAndVerifies() {
        IngestResult v1 = ingestion.ingest(req("content-A", Map.of()));
        IngestResult v2 = ingestion.ingest(req("content-B", Map.of()));
        assertThat(v2.outcome()).isEqualTo(IngestOutcome.NEW_VERSION);
        assertThat(v2.version()).isEqualTo(2);

        UUID id = UUID.fromString(v1.evidenceId());
        assertThat(queries.versions(id)).hasSize(2);
        assertThat(queries.verify(id, null).intact()).isTrue();
        assertThat(queries.verify(id, 1).intact()).isTrue();
    }

    @Test
    void tamperedStoredObjectIsReportedAsIntegrityMismatch() {
        IngestResult r = ingestion.ingest(req("original-content", Map.of()));
        UUID id = UUID.fromString(r.evidenceId());
        String objectKey = queries.versions(id).get(0).objectKey();

        // stored bytes no longer match the recorded SHA-256
        doReturn(Optional.of("tampered-content".getBytes(StandardCharsets.UTF_8)))
                .when(objectStore).get(objectKey);

        var report = queries.verify(id, null);
        assertThat(report.intact()).isFalse();
        assertThat(report.expectedSha256()).isEqualTo(r.sha256());
        assertThat(report.actualSha256()).isNotEqualTo(r.sha256());
        assertThat(report.detail()).contains("mismatch");

        var integrity = queries.dashboard().integrity();
        assertThat(integrity.mismatch()).isEqualTo(1);
        assertThat(integrity.intact()).isZero();
    }

    @Test
    void unknownApplicationIsAutoCreated() {
        IngestResult r = ingestion.ingest(new IngestRequest("brand-new-app", "C-1", "ITPP",
                "MOCK_GITHUB", null, null, null, null, "x", null, null, null, null));
        assertThat(r.applicationSlug()).isEqualTo("brand-new-app");
        assertThat(queries.search(new EvidenceFilter("brand-new-app", null, null, null,
                null, null, null, 0, 10)).totalItems()).isEqualTo(1);
    }
}
