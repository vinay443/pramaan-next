package com.pramaan.backend.reporting;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pramaan.backend.evidence.EvidenceDtos.IngestRequest;
import com.pramaan.backend.evidence.EvidenceIngestionService;
import java.time.Instant;
import java.util.Map;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ReportControllerWebTest {

    @Autowired MockMvc mvc;
    @Autowired EvidenceIngestionService ingestion;

    @Test
    void reportCatalogueIsListed() throws Exception {
        mvc.perform(get("/api/v1/reports"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name=='evidence-register')].formats").exists())
                .andExpect(jsonPath("$[?(@.name=='pan-india')]").exists());
    }

    @Test
    void evidenceRegisterRendersAsJsonAndCsvFromPersistedEvidence() throws Exception {
        ingestion.ingest(new IngestRequest("payments", "ITPP-DOC-03", "ITPP", "SHAREPOINT",
                "p", "policy", "application/json", null, "{\"doc\":\"policy\"}", Instant.now(),
                "owner", Map.of(), Map.of()));

        mvc.perform(get("/api/v1/reports/evidence-register").param("applicationSlug", "payments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].controlId").value("ITPP-DOC-03"))
                .andExpect(jsonPath("$[0].lifecycleState").value("DRAFT"));

        mvc.perform(get("/api/v1/reports/evidence-register")
                        .param("applicationSlug", "payments").param("format", "csv"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(header().string("Content-Disposition", Matchers.containsString("evidence-register")))
                .andExpect(content().string(Matchers.containsString("ITPP-DOC-03")));
    }

    @Test
    void panIndiaAndComplianceSummaryReportsRender() throws Exception {
        mvc.perform(get("/api/v1/reports/pan-india"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.regions").isArray());

        mvc.perform(get("/api/v1/reports/compliance-summary").param("format", "csv"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("compliancePct")));
    }

    @Test
    void regulatoryFilingReportRendersAScopedAndPortfolioWideFiling() throws Exception {
        ingestion.ingest(new IngestRequest("payments", "ITPP-DOC-03", "ITPP", "SHAREPOINT",
                "p2", "policy", "application/json", null, "{\"doc\":\"policy\"}", Instant.now(),
                "owner", Map.of(), Map.of()));

        mvc.perform(get("/api/v1/reports/regulatory-filing").param("applicationSlug", "payments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reportId").value(Matchers.startsWith("REG-")))
                .andExpect(jsonPath("$.scope").value("payments"))
                .andExpect(jsonPath("$.applicationsInScope").value(1))
                .andExpect(jsonPath("$.attestation").isNotEmpty());

        mvc.perform(get("/api/v1/reports/regulatory-filing"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scope").value("PORTFOLIO"))
                .andExpect(jsonPath("$.framework").value("ALL"));

        mvc.perform(get("/api/v1/reports/regulatory-filing")
                        .param("applicationSlug", "payments").param("format", "csv"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(header().string("Content-Disposition", Matchers.containsString("regulatory-filing")))
                .andExpect(content().string(Matchers.containsString("reportId")));
    }

    @Test
    void unknownReportIs404() throws Exception {
        mvc.perform(get("/api/v1/reports/nope")).andExpect(status().isNotFound());
    }

    @Test
    void auditReadinessReportReflectsTheChecklist() throws Exception {
        ingestion.ingest(new IngestRequest("net-banking", "MW-HSTS", "DPSC", "SIM_NGINX",
                "nb", "hsts", "application/json", null,
                "{\"control\":\"MW-HSTS\",\"findings\":[{\"checkId\":\"MW-03\",\"status\":\"FAIL\"}]}",
                Instant.now(), "agent", Map.of(), Map.of()));
        mvc.perform(post("/api/v1/insight/trend/snapshot")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/reports/audit-readiness").param("applicationSlug", "net-banking"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.readinessScore").isNumber())
                .andExpect(jsonPath("$.simulated").value(true));
    }
}
