package com.pramaan.backend.evidence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * UC02-FT015 - simultaneous uploads. Real threads against a private database (not @Transactional, so every thread
 * sees committed data): batch ids never collide, the duplicate check is race-free (exactly one record and one
 * stored version per file however many users upload it at once) and no upload fails because of the race.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:uc02concurrent;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BulkUploadConcurrencyTest {

    private static final int USERS = 4;

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private JsonNode uploadAs(String user, String name, String content) throws Exception {
        MockMultipartFile f = new MockMultipartFile("files", name, "text/plain", content.getBytes(StandardCharsets.UTF_8));
        String body = mvc.perform(multipart("/api/v1/evidence/bulk/upload").file(f).header("X-Pramaan-Role", "APP")
                        .header("X-Pramaan-User", user).param("applicationSlug", "net-banking")
                        .param("framework", "PCI_DSS").param("controlId", "MW-TLS-VERSION"))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body);
    }

    private <T> List<T> simultaneously(List<Callable<T>> calls) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(calls.size());
        CountDownLatch go = new CountDownLatch(1);
        List<Future<T>> futures = new ArrayList<>();
        for (Callable<T> c : calls) {
            futures.add(pool.submit(() -> {
                go.await();
                return c.call();
            }));
        }
        go.countDown();
        List<T> out = new ArrayList<>();
        for (Future<T> f : futures) out.add(f.get());
        pool.shutdown();
        return out;
    }

    @Test
    void usersUploadingTheSameNewFileAtOnceCreateExactlyOneRecordAndNeverFail() throws Exception {
        Set<String> batchIds = new HashSet<>();
        for (int round = 1; round <= 8; round++) {
            String name = "shared-" + round + ".txt";
            List<Callable<JsonNode>> calls = new ArrayList<>();
            for (int u = 0; u < USERS; u++) {
                String user = "User" + u;
                calls.add(() -> uploadAs(user, name, "identical content for round"));
            }
            List<JsonNode> results = simultaneously(calls);

            long created = results.stream().filter(r -> r.get("files").get(0).get("status").asText().equals("CREATED")).count();
            long dup = results.stream().filter(r -> r.get("files").get(0).get("status").asText().equals("DUPLICATE")).count();
            assertThat(results).as("round %d: no failures", round)
                    .allSatisfy(r -> assertThat(r.get("failed").asInt()).isZero());
            assertThat(created).as("round %d created", round).isEqualTo(1);
            assertThat(dup).as("round %d duplicates", round).isEqualTo(USERS - 1);
            results.forEach(r -> assertThat(batchIds.add(r.get("batchId").asText())).as("unique batch id").isTrue());

            String evidenceId = results.get(0).get("files").get(0).get("evidenceId").asText();
            JsonNode versions = json.readTree(mvc.perform(get("/api/v1/evidence/" + evidenceId + "/versions"))
                    .andReturn().getResponse().getContentAsString());
            assertThat(versions).as("round %d versions", round).hasSize(1);
        }
        assertThat(batchIds).hasSize(8 * USERS);
    }

    @Test
    void usersUploadingDifferentFilesAtOnceEachGetTheirOwnBatchWithNoCrossTalk() throws Exception {
        List<Callable<JsonNode>> calls = new ArrayList<>();
        for (int u = 0; u < USERS; u++) {
            int n = u;
            calls.add(() -> uploadAs("Solo" + n, "solo-" + n + ".txt", "content " + n));
        }
        List<JsonNode> results = simultaneously(calls);
        Set<String> ids = new HashSet<>();
        for (int u = 0; u < USERS; u++) {
            JsonNode r = results.get(u);
            assertThat(r.get("status").asText()).isEqualTo("COMPLETED");
            assertThat(r.get("uploadedBy").asText()).isEqualTo("Solo" + u);
            assertThat(r.get("files").get(0).get("filename").asText()).isEqualTo("solo-" + u + ".txt");
            assertThat(ids.add(r.get("batchId").asText())).isTrue();
        }
    }
}
