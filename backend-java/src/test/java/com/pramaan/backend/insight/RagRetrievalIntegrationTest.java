package com.pramaan.backend.insight;

import static org.assertj.core.api.Assertions.assertThat;

import com.pramaan.backend.evidence.EvidenceDtos.IngestRequest;
import com.pramaan.backend.evidence.EvidenceIngestionService;
import com.pramaan.backend.insight.InsightDtos.NlQueryResult;
import com.pramaan.backend.insight.InsightDtos.ReuseResult;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * Defect 1 — RAG retrieval must return matches against LIVE-indexed data, not just
 * data present when the index was first built. Reproduces the "index built early,
 * evidence ingested after → retrieved: [] forever" scenario end-to-end.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RagRetrievalIntegrationTest {

    @Autowired EvidenceIngestionService ingestion;
    @Autowired EvidenceReuseService reuse;
    @Autowired NlQueryService nlQuery;
    @Autowired EvidenceEmbeddingIndexer indexer;

    private void ingest(String app, String control, String framework, String prose) {
        ingestion.ingest(new IngestRequest(app, control, framework, "SHAREPOINT",
                app + "/" + control, control, "text/plain", null, prose,
                Instant.now(), "agent", Map.of(), Map.of()));
    }

    @Test
    void freeTextRetrievalPicksUpEvidenceIngestedAfterTheIndexWasFirstBuilt() {
        // index gets built while the repository is small
        ingest("net-banking", "MW-HSTS", "DPSC", "HSTS header configured on the reverse proxy");
        indexer.ensureIndexed();

        // a scheduler run / bulk upload lands many more records afterwards
        for (int i = 0; i < 12; i++) {
            ingest("payments", "PAY-CTRL-" + i, "PCI_DSS", "payment control note number " + i);
        }
        ingest("net-banking", "OS-SSH-ROOT-LOGIN", "C-SITE",
                "sshd_config sets PermitRootLogin no — ssh root login is disabled on every linux host");

        ReuseResult r = reuse.similarToText(
                "evidence that ssh root login is disabled on a linux host", 6, 0.1);
        assertThat(r.indexed()).isGreaterThanOrEqualTo(14);
        assertThat(r.matches()).isNotEmpty();
        assertThat(r.matches()).anyMatch(m -> m.controlId().equals("OS-SSH-ROOT-LOGIN"));
    }

    @Test
    void nlEvidenceLookupIsGroundedAgainstLiveData() {
        indexer.ensureIndexed();
        for (int i = 0; i < 10; i++) {
            ingest("payments", "NOISE-" + i, "PCI_DSS", "unrelated note " + i);
        }
        ingest("net-banking", "MW-HSTS", "DPSC",
                "Strict-Transport-Security HSTS response header enabled on nginx for all responses");

        NlQueryResult r = nlQuery.answer("what evidence do we have for HSTS on nginx?", null);
        assertThat(r.matchedQuery()).isEqualTo("evidence-lookup");
        assertThat((Boolean) r.answer().get("grounded")).isTrue();
        assertThat((List<?>) r.answer().get("evidenceCitations")).isNotEmpty();
    }
}
