package com.pramaan.backend.insight;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class InsightControllerWebTest {

    @Autowired MockMvc mvc;
    @Autowired EvidenceIngestionService ingestion;

    @Test
    void completenessComplianceAndNlQueryEndpoints() throws Exception {
        ingestion.ingest(new IngestRequest("net-banking", "OS-SSH-ROOT-LOGIN", "C-SITE", "AGENT_OS_LINUX",
                "a", "t", "application/json", null, "{\"findings\":[{\"status\":\"PASS\"}]}",
                Instant.now(), "agent", Map.of(), Map.of()));

        mvc.perform(get("/api/v1/insight/completeness").param("applicationSlug", "net-banking"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expected").value(18))
                .andExpect(jsonPath("$.covered").value(1));

        mvc.perform(get("/api/v1/insight/compliance").param("applicationSlug", "net-banking"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expected").value(18));

        mvc.perform(post("/api/v1/insight/nl-query").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"which controls are missing for net-banking?\",\"applicationSlug\":\"net-banking\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matchedQuery").value("completeness"))
                .andExpect(jsonPath("$.simulated").value(true));

        mvc.perform(post("/api/v1/insight/embeddings/reindex")).andExpect(status().isOk());
        mvc.perform(post("/api/v1/insight/nl-query").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"what evidence do we have for SSH root login?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matchedQuery").value("evidence-lookup"))
                .andExpect(jsonPath("$.answer.grounded").value(true))
                .andExpect(jsonPath("$.answer.evidenceCitations").isNotEmpty())
                .andExpect(jsonPath("$.answer.vectorStore").value("memory"));
    }

    @Test
    void leadershipDashboardEndpointAggregatesAcrossApplications() throws Exception {
        ingestion.ingest(new IngestRequest("net-banking", "OS-SSH-ROOT-LOGIN", "C-SITE", "AGENT_OS_LINUX",
                "a", "t", "application/json", null, "{\"findings\":[{\"status\":\"PASS\"}]}",
                Instant.now(), "agent", Map.of(), Map.of()));

        mvc.perform(get("/api/v1/insight/leadership"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applications").value(1))
                .andExpect(jsonPath("$.expected").value(18))
                .andExpect(jsonPath("$.byApplication[0].applicationSlug").value("net-banking"))
                .andExpect(jsonPath("$.checkVerdicts.PASS").exists());
    }

    @Test
    void reuseAndSummaryEndpoints() throws Exception {
        var r = ingestion.ingest(new IngestRequest("payments", "TLS-CERT-EXPIRY", "C-SITE", "AGENT_TLS",
                "p", "t", "application/json", null, "{\"control\":\"TLS-CERT-EXPIRY\",\"findings\":[{\"status\":\"PASS\"}]}",
                Instant.now(), "agent", Map.of(), Map.of()));

        mvc.perform(post("/api/v1/insight/embeddings/reindex"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.indexed").value(1));

        mvc.perform(get("/api/v1/insight/evidence/{id}/summary", r.evidenceId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.simulated").value(true))
                .andExpect(jsonPath("$.controlId").value("TLS-CERT-EXPIRY"));

        mvc.perform(get("/api/v1/insight/reuse/{id}", r.evidenceId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vectorStore").value("memory"));
    }
}
