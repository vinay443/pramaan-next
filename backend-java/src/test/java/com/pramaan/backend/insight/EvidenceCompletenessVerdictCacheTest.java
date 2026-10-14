package com.pramaan.backend.insight;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.pramaan.backend.evidence.EvidenceDtos.IngestRequest;
import com.pramaan.backend.evidence.EvidenceIngestionService;
import com.pramaan.backend.storage.ObjectStore;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * FT016 - the completeness scoring verifies each immutable version once. The aggregate cache is off in the test profile
 * (completeness-cache-seconds=0), so every call below is a "cold" aggregate; only the per-version verdicts persist.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:completeness-verdicts;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
@Transactional
class EvidenceCompletenessVerdictCacheTest {

    private static final int RECORDS = 700;

    @Autowired EvidenceIngestionService ingestion;
    @Autowired EvidenceCompletenessService completeness;
    @SpyBean ObjectStore objectStore;

    private void ingest(int i) {
        ingestion.ingest(new IngestRequest("perf-app", "PERF-CTRL-" + (i % 100), "PCI_DSS", "BULK_UPLOAD",
                "obj-" + i, "obj-" + i + ".txt", "text/plain", null, "evidence body number " + i,
                Instant.now(), "tester", Map.of(), Map.of("technology", "nginx")));
    }

    @Test
    void aSecondColdCallAfterAnUnrelatedUploadRereadsOnlyTheNewVersion() {
        for (int i = 0; i < RECORDS; i++) ingest(i);

        clearInvocations(objectStore);
        long t0 = System.nanoTime();
        int first = completeness.forScope(null, null).totalItems();
        long firstMs = (System.nanoTime() - t0) / 1_000_000;
        assertThat(first).isEqualTo(RECORDS);
        verify(objectStore, times(RECORDS)).get(anyString()); // nothing verified yet: one read per version

        clearInvocations(objectStore);
        t0 = System.nanoTime();
        var again = completeness.forScope(null, null);
        long secondMs = (System.nanoTime() - t0) / 1_000_000;
        verify(objectStore, times(0)).get(anyString()); // unchanged versions are not re-read or re-hashed
        assertThat(again.totalItems()).isEqualTo(RECORDS);

        ingest(RECORDS); // an unrelated upload
        clearInvocations(objectStore);
        t0 = System.nanoTime();
        int afterUpload = completeness.forScope(null, null).totalItems();
        long thirdMs = (System.nanoTime() - t0) / 1_000_000;
        assertThat(afterUpload).isEqualTo(RECORDS + 1);
        verify(objectStore, times(1)).get(anyString()); // only the new version

        System.out.printf("COMPLETENESS-TIMING records=%d first(cold, all versions read)=%d ms, second(cold aggregate, "
                + "verdicts cached)=%d ms, after one upload=%d ms%n", RECORDS, firstMs, secondMs, thirdMs);
        assertThat(secondMs).as("cold aggregate with cached verdicts").isLessThan(1000);
        assertThat(thirdMs).isLessThan(1000);
    }
}
