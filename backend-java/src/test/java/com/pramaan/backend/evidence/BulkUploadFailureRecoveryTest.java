package com.pramaan.backend.evidence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pramaan.backend.rules.RuleEvaluationService;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * UC03-FT012 - a failure in the middle of one file's ingest (after the object was written, while the database
 * transaction is still open). Not @Transactional on purpose: the ingest transaction must really roll back.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:uc03recovery;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BulkUploadFailureRecoveryTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired com.pramaan.backend.application.ApplicationService applications;
    @SpyBean RuleEvaluationService ruleEvaluation;

    private JsonNode upload(MockMultipartFile... files) throws Exception {
        var b = multipart("/api/v1/evidence/bulk/upload");
        for (MockMultipartFile f : files) b.file(f);
        return json.readTree(mvc.perform(b.header("X-Pramaan-Role", "APP").header("X-Pramaan-User", "App Owner")
                        .param("applicationSlug", "net-banking").param("framework", "PCI_DSS")
                        .param("controlId", "MW-TLS-VERSION").param("environment", "UAT"))
                .andReturn().getResponse().getContentAsString());
    }

    private static MockMultipartFile file(String name, String content) {
        return new MockMultipartFile("files", name, "text/plain", content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void anExceptionMidIngestLeavesNoPartialRecordEndsTheBatchPartialAndAResubmitIsIdempotent() throws Exception {
        applications.upsert(new com.pramaan.backend.application.ApplicationDtos.UpsertRequest(
                "net-banking", "Net Banking", null, null, null, null));
        // the first file's ingest fails after its record was saved and its object written; the second is fine
        doThrow(new IllegalStateException("db connection lost")).doCallRealMethod().when(ruleEvaluation).evaluate(any());

        JsonNode first = upload(file("a-fails.txt", "content A"), file("b-ok.txt", "content B"));
        assertThat(first.get("status").asText()).isEqualTo("PARTIAL");                       // terminal, not RUNNING
        JsonNode a = first.get("files").get(0);
        assertThat(a.get("status").asText()).isEqualTo("FAILED");
        assertThat(a.get("code").asText()).isEqualTo("PROCESSING_ERROR");
        assertThat(a.get("message").asText()).doesNotContain("connection lost");               // no internals leaked
        assertThat(first.get("files").get(1).get("status").asText()).isEqualTo("CREATED");
        assertThat(first.get("failed").asInt()).isEqualTo(1);

        // the failed file left no record behind (transaction rolled back); only the good one exists
        JsonNode list = json.readTree(mvc.perform(get("/api/v1/evidence").param("applicationSlug", "net-banking"))
                .andReturn().getResponse().getContentAsString());
        assertThat(list.get("totalItems").asInt()).isEqualTo(1);
        assertThat(list.get("items").get(0).get("evidenceKey").asText()).contains("b-ok.txt");

        // re-submitting both files: the failed one is created once, the stored one is a duplicate, nothing doubles
        JsonNode again = upload(file("a-fails.txt", "content A"), file("b-ok.txt", "content B"));
        assertThat(again.get("status").asText()).isEqualTo("COMPLETED");
        assertThat(again.get("files").get(0).get("status").asText()).isEqualTo("CREATED");
        assertThat(again.get("files").get(1).get("status").asText()).isEqualTo("DUPLICATE");
        JsonNode third = upload(file("a-fails.txt", "content A"), file("b-ok.txt", "content B"));
        assertThat(third.get("files").get(0).get("status").asText()).isEqualTo("DUPLICATE");
        assertThat(third.get("files").get(1).get("status").asText()).isEqualTo("DUPLICATE");

        JsonNode finalList = json.readTree(mvc.perform(get("/api/v1/evidence").param("applicationSlug", "net-banking"))
                .andReturn().getResponse().getContentAsString());
        assertThat(finalList.get("totalItems").asInt()).isEqualTo(2);
        finalList.get("items").forEach(i -> assertThat(i.get("currentVersion").asInt()).isEqualTo(1));
    }
}
