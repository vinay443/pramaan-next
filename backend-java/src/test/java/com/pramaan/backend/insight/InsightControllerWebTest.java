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
                .andExpect(jsonPath("$.simulated").value(true))
                .andExpect(jsonPath("$.supported").value(true))
                .andExpect(jsonPath("$.modelGenerated").value(false));

        // UC-P2-4: an unrecognised question is reported as unsupported, with the
        // supported set returned, instead of falling through to a generic query.
        mvc.perform(post("/api/v1/insight/nl-query").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"what is the capital of France?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.supported").value(false))
                .andExpect(jsonPath("$.matchedQuery").value("unsupported"))
                .andExpect(jsonPath("$.supportedQuestionTypes").isNotEmpty());

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
                // UC-P2-3: the mock is a prompt digest, and the payload must say so,
                // and must name the record it summarised.
                .andExpect(jsonPath("$.modelGenerated").value(false))
                .andExpect(jsonPath("$.evidenceId").value(r.evidenceId()))
                .andExpect(jsonPath("$.controlId").value("TLS-CERT-EXPIRY"));

        mvc.perform(get("/api/v1/insight/reuse/{id}", r.evidenceId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vectorStore").value("memory"));
    }

    @Test
    void reuseByControlListsFrameworksAndHeldEvidenceAndCatalogue() throws Exception {
        var r = ingestion.ingest(new IngestRequest("net-banking", "OS-SSH-ROOT-LOGIN", "C-SITE",
                "AGENT_OS_LINUX", "a", "t", "application/json", null,
                "{\"findings\":[{\"status\":\"PASS\"}]}", Instant.now(), "agent", Map.of(), Map.of()));

        // control picker catalogue (UC03 control -> frameworks)
        mvc.perform(get("/api/v1/insight/reuse/controls"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.controlId == 'OS-SSH-ROOT-LOGIN')].frameworks[0]").value("C-SITE"));

        mvc.perform(get("/api/v1/insight/reuse/by-control").param("controlId", "os-ssh-root-login"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.controlId").value("OS-SSH-ROOT-LOGIN"))
                .andExpect(jsonPath("$.frameworks").value(org.hamcrest.Matchers.hasItems("C-SITE", "PCI_DSS")))
                .andExpect(jsonPath("$.evidence[0].applicationSlug").value("net-banking"))
                .andExpect(jsonPath("$.evidence[0].evidenceId").value(r.evidenceId()))
                .andExpect(jsonPath("$.evidence[0].mappedFrameworks").value(
                        org.hamcrest.Matchers.hasItem("C-SITE")));

        mvc.perform(get("/api/v1/insight/reuse/by-control").param("controlId", "NOPE-404"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.frameworks").isEmpty())
                .andExpect(jsonPath("$.evidence").isEmpty());
    }

    @Test
    void addFrameworkMappingIsValidatedAndIdempotent() throws Exception {
        var r = ingestion.ingest(new IngestRequest("payments", "TLS-CERT-EXPIRY", "C-SITE", "AGENT_TLS",
                "p", "t", "application/json", null, "{\"findings\":[{\"status\":\"PASS\"}]}",
                Instant.now(), "agent", Map.of(), Map.of()));

        // a framework the control does not map to is rejected
        mvc.perform(post("/api/v1/evidence/{id}/frameworks", r.evidenceId())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"framework\":\"HIPAA\"}"))
                .andExpect(status().isBadRequest());

        // a mapped framework is accepted and reflected in the frameworks tag
        mvc.perform(post("/api/v1/evidence/{id}/frameworks", r.evidenceId())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"framework\":\"iso27001\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evidenceId").value(r.evidenceId()))
                .andExpect(jsonPath("$.tags.frameworks").value(org.hamcrest.Matchers.containsString("ISO27001")));

        // idempotent — re-adding the same framework still succeeds
        mvc.perform(post("/api/v1/evidence/{id}/frameworks", r.evidenceId())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"framework\":\"ISO27001\"}"))
                .andExpect(status().isOk());
    }
}
