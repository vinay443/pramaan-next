package com.pramaan.backend;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.pramaan.backend.scheduler.SchedulerRunExecutor;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Master workbook validation — one REST-level walk of the full chain:
 * onboard -> collect/upload -> tag -> persist/hash -> query -> completeness ->
 * reuse -> dashboard -> AI -> lifecycle -> enterprise/reporting.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class EndToEndFlowTest {

    @Autowired MockMvc mvc;
    @Autowired SchedulerRunExecutor executor;

    @Test
    void fullChain() throws Exception {
        // 1. ONBOARD (config-driven, idempotent)
        mvc.perform(post("/api/v1/onboarding/apply"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applied").value(3));
        mvc.perform(get("/api/v1/applications"))
                .andExpect(jsonPath("$[?(@.slug=='net-banking')].businessUnit").value("Retail Banking"));

        // 2. COLLECT — scheduler pulls SharePoint / ServiceNow / a simulated tech source
        String runJson = mvc.perform(post("/api/v1/scheduler/runs").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"applications\":[\"net-banking\"],\"sources\":[\"SHAREPOINT\",\"SERVICENOW\",\"SIM_NGINX\"]}"))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        executor.execute(UUID.fromString(JsonPath.read(runJson, "$.runId")));
        mvc.perform(get("/api/v1/scheduler/runs/{id}", JsonPath.read(runJson, "$.runId").toString()))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.ingested", org.hamcrest.Matchers.greaterThan(0)));

        // 3. UPLOAD — bulk file upload for the same app/control
        MockMultipartFile f = new MockMultipartFile("files", "hsts.conf", "text/plain",
                "add_header Strict-Transport-Security max-age=63072000; HSTS enabled on nginx".getBytes());
        String upJson = mvc.perform(multipart("/api/v1/evidence/bulk/upload").file(f)
                        .param("applicationSlug", "net-banking").param("framework", "NGINX_BASELINING")
                        .param("controlId", "NGBL-C8").param("technology", "nginx")
                        .param("collectedBy", "app-owner"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(1))
                .andReturn().getResponse().getContentAsString();
        String evidenceId = JsonPath.read(upJson, "$.results[0].evidenceId");

        // 4. TAG — canonical Use Case 3 tag set applied on the one ingestion path
        mvc.perform(get("/api/v1/evidence/{id}", evidenceId))
                .andExpect(jsonPath("$.tags.application").value("net-banking"))
                .andExpect(jsonPath("$.tags.control").value("NGBL-C8"))
                .andExpect(jsonPath("$.tags.framework").value("NGINX_BASELINING"))
                .andExpect(jsonPath("$.tags.technology").value("nginx"))
                .andExpect(jsonPath("$.tags.collectionMethod").value("bulk"))
                .andExpect(jsonPath("$.lifecycleState").value("DRAFT"));
        mvc.perform(get("/api/v1/evidence").param("applicationSlug", "net-banking")
                        .param("technology", "nginx").param("collectionMethod", "bulk"))
                .andExpect(jsonPath("$.totalItems", org.hamcrest.Matchers.greaterThan(0)));

        // 5. PERSIST / HASH — SHA-256 recomputed from the object store
        mvc.perform(get("/api/v1/evidence/{id}/verify", evidenceId))
                .andExpect(jsonPath("$.intact").value(true));
        mvc.perform(get("/api/v1/evidence/dashboard"))
                .andExpect(jsonPath("$.integrity.mismatch").value(0))
                .andExpect(jsonPath("$.integrity.intact", org.hamcrest.Matchers.greaterThan(0)))
                .andExpect(jsonPath("$.records", org.hamcrest.Matchers.greaterThan(0)));

        // 6. QUERY — deterministic named query over persisted evidence
        mvc.perform(get("/api/v1/evidence/query/source-breakdown").param("applicationSlug", "net-banking"))
                .andExpect(jsonPath("$.counts.SHAREPOINT").exists());

        // 7. COMPLETENESS — against the expected-control catalogue
        mvc.perform(get("/api/v1/insight/completeness").param("applicationSlug", "net-banking"))
                .andExpect(jsonPath("$.covered", org.hamcrest.Matchers.greaterThan(0)));

        // 8. REUSE — embedding index + SHA-256 exact-duplicate detection
        mvc.perform(post("/api/v1/insight/embeddings/reindex"))
                .andExpect(jsonPath("$.indexed", org.hamcrest.Matchers.greaterThan(0)));
        mvc.perform(get("/api/v1/insight/reuse/{id}", evidenceId))
                .andExpect(jsonPath("$.vectorStore").value("memory"))
                .andExpect(jsonPath("$.querySha256").isNotEmpty());

        // 9. DASHBOARD — leadership rollup reflects verdicts WITHOUT a manual re-evaluate
        //    (rule evaluation is auto-triggered on every ingest / scheduler run / upload)
        mvc.perform(get("/api/v1/insight/leadership"))
                .andExpect(jsonPath("$.applications").value(3))
                .andExpect(jsonPath("$.checkVerdicts.PASS").exists())
                .andExpect(jsonPath("$.compliant", org.hamcrest.Matchers.greaterThan(0)));

        // 10. AI — grounded summary + RAG NL query (deterministic MOCK_AI)
        mvc.perform(get("/api/v1/insight/evidence/{id}/summary", evidenceId))
                .andExpect(jsonPath("$.simulated").value(true))
                .andExpect(jsonPath("$.groundedOn", org.hamcrest.Matchers.not(org.hamcrest.Matchers.empty())));
        mvc.perform(post("/api/v1/insight/nl-query").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"what evidence do we have for HSTS?\"}"))
                .andExpect(jsonPath("$.matchedQuery").value("evidence-lookup"))
                .andExpect(jsonPath("$.simulated").value(true));

        // 11. LIFECYCLE — review workflow + audit trail (RBAC via X-User-Role)
        mvc.perform(post("/api/v1/evidence/{id}/lifecycle", evidenceId).contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Role", "APP_OWNER")
                        .content("{\"action\":\"SUBMIT\",\"actor\":\"owner\"}"))
                .andExpect(jsonPath("$.state").value("SUBMITTED"));
        mvc.perform(post("/api/v1/evidence/{id}/lifecycle", evidenceId).contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Role", "AUDITOR")
                        .content("{\"action\":\"APPROVE\",\"actor\":\"auditor\"}"))
                .andExpect(jsonPath("$.state").value("APPROVED"))
                .andExpect(jsonPath("$.history[0].action").value("INGESTED"));

        // 12. ENTERPRISE / REPORTING — compositions of the above
        mvc.perform(get("/api/v1/insight/comparison")
                        .param("applications", "net-banking").param("applications", "payments"))
                .andExpect(jsonPath("$.applications.length()").value(2));
        mvc.perform(get("/api/v1/insight/enterprise"))
                .andExpect(jsonPath("$.byBusinessUnit", org.hamcrest.Matchers.not(org.hamcrest.Matchers.empty())));
        mvc.perform(get("/api/v1/insight/national"))
                .andExpect(jsonPath("$.regions[?(@.region=='North')]").exists());
        mvc.perform(get("/api/v1/insight/audit-prep").param("applicationSlug", "net-banking"))
                .andExpect(jsonPath("$.simulated").value(true))
                .andExpect(jsonPath("$.readinessScore").isNumber());
        mvc.perform(post("/api/v1/insight/trend/snapshot")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/insight/trend"))
                .andExpect(jsonPath("$.points", org.hamcrest.Matchers.not(org.hamcrest.Matchers.empty())));
        mvc.perform(get("/api/v1/reports/evidence-register").param("applicationSlug", "net-banking"))
                .andExpect(jsonPath("$[0].lifecycleState").exists());
        mvc.perform(get("/api/v1/reports/gap-report").param("format", "csv"))
                .andExpect(status().isOk());
    }
}
