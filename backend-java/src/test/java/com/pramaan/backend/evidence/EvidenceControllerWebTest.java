package com.pramaan.backend.evidence;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
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
class EvidenceControllerWebTest {

    @Autowired MockMvc mvc;

    @Test
    void ingestThenQueryAndRunDeterministicQuery() throws Exception {
        String body = """
                {
                  "applicationSlug": "mobile-banking",
                  "controlId": "PCI-DSS-6.2",
                  "framework": "PCI_DSS",
                  "sourceSystem": "MOCK_GITHUB",
                  "sourceObjectId": "repo-1",
                  "contentText": "branch protection enabled",
                  "collectedAt": "2026-08-01T00:00:00Z",
                  "tags": { "team": "mobile" }
                }
                """;
        mvc.perform(post("/api/v1/evidence/ingest").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.outcome").value("CREATED"))
                .andExpect(jsonPath("$.version").value(1));

        mvc.perform(get("/api/v1/evidence").param("applicationSlug", "mobile-banking"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].tags.team").value("mobile"));

        mvc.perform(get("/api/v1/evidence/query/source-breakdown").param("applicationSlug", "mobile-banking"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.counts.MOCK_GITHUB").value(1));
    }

    @Test
    void evidenceLifecycleTransitionsAndAuditTrailOverRest() throws Exception {
        String body = """
                {"applicationSlug":"payments","controlId":"ITPP-DOC-03","framework":"ITPP",
                 "sourceSystem":"SHAREPOINT","contentText":"policy document v1"}
                """;
        String created = mvc.perform(post("/api/v1/evidence/ingest")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = com.jayway.jsonpath.JsonPath.read(created, "$.evidenceId");

        mvc.perform(get("/api/v1/evidence/{id}/lifecycle", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("DRAFT"))
                .andExpect(jsonPath("$.history[0].action").value("INGESTED"));

        mvc.perform(post("/api/v1/evidence/{id}/lifecycle", id).contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Role", "APP_OWNER")
                        .content("{\"action\":\"SUBMIT\",\"actor\":\"owner\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("SUBMITTED"));

        mvc.perform(post("/api/v1/evidence/{id}/lifecycle", id).contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Role", "AUDITOR")
                        .content("{\"action\":\"APPROVE\",\"actor\":\"auditor\",\"note\":\"ok\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("APPROVED"))
                .andExpect(jsonPath("$.reviewedBy").value("auditor"));

        mvc.perform(post("/api/v1/evidence/{id}/lifecycle", id).contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Role", "AUDITOR")
                        .content("{\"action\":\"APPROVE\",\"actor\":\"x\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void dashboardAggregatesPersistedEvidenceAndChecksIntegrity() throws Exception {
        String body = """
                {
                  "applicationSlug": "payments",
                  "controlId": "PCI-DSS-3.4",
                  "framework": "PCI_DSS",
                  "sourceSystem": "MOCK_JIRA",
                  "contentText": "disk encryption enabled",
                  "collectedAt": "2026-09-01T00:00:00Z"
                }
                """;
        mvc.perform(post("/api/v1/evidence/ingest").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/v1/evidence/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.records").value(1))
                .andExpect(jsonPath("$.versions").value(1))
                .andExpect(jsonPath("$.applications").value(1))
                .andExpect(jsonPath("$.integrity.checked").value(1))
                .andExpect(jsonPath("$.integrity.intact").value(1))
                .andExpect(jsonPath("$.integrity.mismatch").value(0))
                .andExpect(jsonPath("$.duplicateHashes").value(0))
                .andExpect(jsonPath("$.bySource[0].sourceSystem").value("MOCK_JIRA"));
    }

    @Test
    void bulkIngestReportsPerItemOutcomes() throws Exception {
        String body = """
                [
                  {"applicationSlug":"payments","controlId":"C-1","framework":"ITPP","sourceSystem":"MOCK_JIRA","contentText":"a"},
                  {"applicationSlug":"payments","controlId":"C-1","framework":"ITPP","sourceSystem":"MOCK_JIRA","contentText":"a"},
                  {"applicationSlug":"payments","controlId":"C-2","framework":"ITPP","sourceSystem":"MOCK_JIRA","contentText":"b"}
                ]
                """;
        mvc.perform(post("/api/v1/evidence/bulk").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.received").value(3))
                .andExpect(jsonPath("$.created").value(2))
                .andExpect(jsonPath("$.duplicates").value(1));
    }

    @Test
    void bulkIngestConvertsPerItemValidationFailuresToErrorsAndStillCreatesValidItems() throws Exception {
        // one valid item plus one item per required-but-blank field
        String body = """
                [
                  {"applicationSlug":"payments","controlId":"C-OK","framework":"ITPP","sourceSystem":"MOCK_JIRA","contentText":"ok"},
                  {"applicationSlug":"payments","controlId":"","framework":"ITPP","sourceSystem":"MOCK_JIRA","contentText":"x"},
                  {"applicationSlug":"","controlId":"C-2","framework":"ITPP","sourceSystem":"MOCK_JIRA","contentText":"x"},
                  {"applicationSlug":"payments","controlId":"C-3","framework":"ITPP","sourceSystem":"","contentText":"x"}
                ]
                """;
        mvc.perform(post("/api/v1/evidence/bulk").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.received").value(4))
                .andExpect(jsonPath("$.created").value(1))
                .andExpect(jsonPath("$.failed").value(3))
                .andExpect(jsonPath("$.errors[0].index").value(1))
                .andExpect(jsonPath("$.errors[0].message").value("controlId is required"))
                .andExpect(jsonPath("$.errors[1].index").value(2))
                .andExpect(jsonPath("$.errors[1].message").value("applicationSlug is required"))
                .andExpect(jsonPath("$.errors[2].index").value(3))
                .andExpect(jsonPath("$.errors[2].message").value("sourceSystem is required"));

        // the one valid item really was persisted despite the bad siblings
        mvc.perform(get("/api/v1/evidence")
                        .param("applicationSlug", "payments")
                        .param("controlId", "C-OK"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1));
    }

    @Test
    void bulkFileUploadCarriesTechnologyTagAndIsFacetFilterable() throws Exception {
        MockMultipartFile f1 = new MockMultipartFile("files", "nginx.conf", "text/plain",
                "server_tokens off;".getBytes());
        MockMultipartFile f2 = new MockMultipartFile("files", "tls.txt", "text/plain",
                "ssl_protocols TLSv1.2 TLSv1.3;".getBytes());

        mvc.perform(multipart("/api/v1/evidence/bulk/upload").file(f1).file(f2)
                        .param("applicationSlug", "net-banking")
                        .param("framework", "PCI_DSS")
                        .param("controlId", "MW-TLS-VERSION")
                        .param("technology", "nginx")
                        .param("collectedBy", "app-owner"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.received").value(2))
                .andExpect(jsonPath("$.created").value(2));

        // uploaded evidence carries the real technology tag + bulk collection method,
        // and is filterable by the same facets as scheduler-collected evidence
        mvc.perform(get("/api/v1/evidence")
                        .param("applicationSlug", "net-banking")
                        .param("technology", "nginx")
                        .param("collectionMethod", "bulk"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.items[0].tags.technology").value("nginx"))
                .andExpect(jsonPath("$.items[0].tags.collectionMethod").value("bulk"))
                .andExpect(jsonPath("$.items[0].framework").value("PCI_DSS"))
                .andExpect(jsonPath("$.items[0].controlId").value("MW-TLS-VERSION"));
    }

    @Test
    void bulkFileUploadWithoutTechnologyFallsBackGracefully() throws Exception {
        MockMultipartFile f = new MockMultipartFile("files", "note.txt", "text/plain",
                "manual evidence note".getBytes());
        mvc.perform(multipart("/api/v1/evidence/bulk/upload").file(f)
                        .param("applicationSlug", "payments")
                        .param("framework", "ITPP")
                        .param("controlId", "ITPP-DOC-03"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(1));

        mvc.perform(get("/api/v1/evidence")
                        .param("applicationSlug", "payments")
                        .param("collectionMethod", "bulk"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].tags.technology").value("unknown"));
    }

    @Test
    void bulkFileUploadExpandsZipEntriesIntoSeparateEvidenceItems() throws Exception {
        java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
        try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(buf)) {
            zos.putNextEntry(new java.util.zip.ZipEntry("configs/nginx.conf"));
            zos.write("server_tokens off;".getBytes());
            zos.closeEntry();
            zos.putNextEntry(new java.util.zip.ZipEntry("tls.txt"));
            zos.write("ssl_protocols TLSv1.2 TLSv1.3;".getBytes());
            zos.closeEntry();
        }
        MockMultipartFile zip = new MockMultipartFile("files", "evidence.zip", "application/zip", buf.toByteArray());

        mvc.perform(multipart("/api/v1/evidence/bulk/upload").file(zip)
                        .param("applicationSlug", "net-banking")
                        .param("framework", "PCI_DSS")
                        .param("controlId", "MW-TLS-VERSION"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.received").value(2))
                .andExpect(jsonPath("$.created").value(2));
    }

    @Test
    void bulkFileUploadReportsAPartialDuplicateDistinctlyFromFailuresAndSuccesses() throws Exception {
        MockMultipartFile original = new MockMultipartFile("files", "existing.txt", "text/plain",
                "unchanged policy text".getBytes());
        mvc.perform(multipart("/api/v1/evidence/bulk/upload").file(original)
                        .param("applicationSlug", "payments")
                        .param("framework", "ITPP")
                        .param("controlId", "ITPP-DOC-09"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(1));

        // Re-upload the same filename + byte-identical content alongside a genuinely new
        // file: the duplicate must be reported as DUPLICATE (not an error), the new file
        // must still be ingested, and neither should land in the `errors` list.
        MockMultipartFile duplicate = new MockMultipartFile("files", "existing.txt", "text/plain",
                "unchanged policy text".getBytes());
        MockMultipartFile fresh = new MockMultipartFile("files", "new-evidence.txt", "text/plain",
                "brand new content".getBytes());

        mvc.perform(multipart("/api/v1/evidence/bulk/upload").file(duplicate).file(fresh)
                        .param("applicationSlug", "payments")
                        .param("framework", "ITPP")
                        .param("controlId", "ITPP-DOC-09"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.received").value(2))
                .andExpect(jsonPath("$.created").value(1))
                .andExpect(jsonPath("$.duplicates").value(1))
                .andExpect(jsonPath("$.failed").value(0))
                .andExpect(jsonPath("$.errors").isEmpty())
                .andExpect(jsonPath("$.results[0].outcome").value("DUPLICATE"))
                .andExpect(jsonPath("$.results[0].sourceObjectId").value("existing.txt"))
                .andExpect(jsonPath("$.results[1].outcome").value("CREATED"))
                .andExpect(jsonPath("$.results[1].sourceObjectId").value("new-evidence.txt"));

        // the duplicate is reported, not silently dropped — it's still queryable and its
        // evidence record didn't gain a spurious new version from the duplicate re-upload
        mvc.perform(get("/api/v1/evidence")
                        .param("applicationSlug", "payments")
                        .param("controlId", "ITPP-DOC-09"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(2));
    }
}
