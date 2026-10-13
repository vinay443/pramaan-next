package com.pramaan.backend.insight;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pramaan.backend.evidence.EvidenceDtos.IngestRequest;
import com.pramaan.backend.evidence.EvidenceIngestionService;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * FT012: a free-text NL query runs under the service's normal (class-level read-only) transaction, which triggers a
 * re-index (clear + upsert) when evidence was ingested since the last one. It must answer 200, not 500. Deliberately
 * NOT @Transactional so the request's own read-only transaction is the only one in play and the data is committed.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:nlreadonly;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NlQueryReadOnlyTransactionTest {

    @Autowired MockMvc mvc;
    @Autowired EvidenceIngestionService ingestion;

    @Test
    void nlEvidenceLookupReturns200WhenTheQueryHasToReindexUnderTheReadOnlyTransaction() throws Exception {
        ingestion.ingest(new IngestRequest("net-banking", "OS-SSH-ROOT-LOGIN", "C-SITE", "AGENT_OS_LINUX",
                "a", "t", "text/plain", null, "sshd_config sets PermitRootLogin no - ssh root login is disabled",
                Instant.now(), "agent", Map.of(), Map.of()));

        // first query: cold index -> ensureIndexed() reindexes inside the read-only transaction
        mvc.perform(post("/api/v1/insight/nl-query").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"what evidence do we have for SSH root login?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matchedQuery").value("evidence-lookup"));

        // evidence lands after the index was built -> the next query is stale and reindexes again
        ingestion.ingest(new IngestRequest("net-banking", "MW-HSTS", "DPSC", "SHAREPOINT",
                "b", "t", "text/plain", null, "HSTS header enabled on nginx", Instant.now(), "agent", Map.of(), Map.of()));
        mvc.perform(post("/api/v1/insight/nl-query").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"what evidence do we have for HSTS on nginx?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matchedQuery").value("evidence-lookup"));
    }
}
