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
    @Autowired com.pramaan.backend.application.ApplicationService applications;
    @Autowired EvidenceIngestionService ingestion;

    @org.junit.jupiter.api.BeforeEach
    void seedApplication() {
        applications.upsert(new com.pramaan.backend.application.ApplicationDtos.UpsertRequest("net-banking", "Net Banking", null, null, null, null));
    }

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
    void parallelDifferentContentUploadsOfOneExistingFileGetDistinctContiguousVersionsAndNeverFail() throws Exception {
        final int writers = 8;
        JsonNode seed = uploadAs("Seeder", "versioned.txt", "version one");
        assertThat(seed.get("files").get(0).get("status").asText()).isEqualTo("CREATED");
        String evidenceId = seed.get("files").get(0).get("evidenceId").asText();

        List<Callable<JsonNode>> calls = new ArrayList<>();
        for (int u = 0; u < writers; u++) {
            int n = u;
            calls.add(() -> uploadAs("Writer" + n, "versioned.txt", "different content from writer " + n));
        }
        List<JsonNode> results = simultaneously(calls);

        assertThat(results).allSatisfy(r -> {
            assertThat(r.get("failed").asInt()).isZero();
            assertThat(r.get("files").get(0).get("status").asText()).isEqualTo("NEW_VERSION");
        });
        List<Integer> reported = results.stream().map(r -> r.get("files").get(0).get("version").asInt()).sorted().toList();
        assertThat(reported).containsExactly(2, 3, 4, 5, 6, 7, 8, 9);

        JsonNode versions = json.readTree(mvc.perform(get("/api/v1/evidence/" + evidenceId + "/versions"))
                .andReturn().getResponse().getContentAsString());
        List<Integer> stored = new ArrayList<>();
        versions.forEach(v -> stored.add(v.get("version").asInt()));
        assertThat(stored.stream().sorted().toList()).containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9);
    }

    /** UC03-FT015: 12 parallel uploads, same application/control/environment/day -> 12 records, 12 distinct names. */
    @Test
    void twelveParallelUploadsForOneControlAndEnvironmentGetDistinctNamesAndLoseNoRecords() throws Exception {
        final int n = 12;
        List<Callable<JsonNode>> calls = new ArrayList<>();
        for (int u = 0; u < n; u++) {
            int k = u;
            calls.add(() -> {
                MockMultipartFile f = new MockMultipartFile("files", "ft015-" + k + ".txt", "text/plain",
                        ("ft015 content " + k).getBytes(StandardCharsets.UTF_8));
                return json.readTree(mvc.perform(multipart("/api/v1/evidence/bulk/upload").file(f)
                                .header("X-Pramaan-Role", "APP").header("X-Pramaan-User", "P" + k)
                                .param("applicationSlug", "net-banking").param("framework", "PCI_DSS")
                                .param("controlId", "MW-TLS-VERSION").param("environment", "UAT"))
                        .andReturn().getResponse().getContentAsString());
            });
        }
        List<JsonNode> results = simultaneously(calls);

        assertThat(results).allSatisfy(r -> {
            assertThat(r.get("failed").asInt()).isZero();
            assertThat(r.get("files").get(0).get("status").asText()).isEqualTo("CREATED");
        });
        Set<String> ids = new HashSet<>();
        Set<String> names = new HashSet<>();
        for (JsonNode r : results) {
            String id = r.get("files").get(0).get("evidenceId").asText();
            ids.add(id);
            names.add(json.readTree(mvc.perform(get("/api/v1/evidence/" + id)).andReturn().getResponse()
                    .getContentAsString()).get("title").asText());
        }
        assertThat(ids).as("no lost records").hasSize(n);
        assertThat(names).as("distinct generated names").hasSize(n).allSatisfy(name -> assertThat(name)
                .contains("_MW-TLS-VERSION_").contains("_UAT_"));
    }


    /** Uploads n distinct files at once to {@code app}; returns {results, elapsedMillis}. */
    private record Burst(List<JsonNode> results, long millis, List<String> contents) {}

    private Burst burst(String app, int n, String tag) throws Exception {
        List<String> contents = new ArrayList<>();
        List<Callable<JsonNode>> calls = new ArrayList<>();
        for (int u = 0; u < n; u++) {
            int k = u;
            String content = tag + " content " + k;
            contents.add(content);
            calls.add(() -> {
                MockMultipartFile f = new MockMultipartFile("files", tag + "-" + k + ".txt", "text/plain",
                        content.getBytes(StandardCharsets.UTF_8));
                return json.readTree(mvc.perform(multipart("/api/v1/evidence/bulk/upload").file(f)
                                .header("X-Pramaan-Role", "APP").header("X-Pramaan-User", "P" + k)
                                .param("applicationSlug", app).param("framework", "PCI_DSS")
                                .param("controlId", "MW-TLS-VERSION").param("environment", "UAT"))
                        .andReturn().getResponse().getContentAsString());
            });
        }
        long t0 = System.nanoTime();
        List<JsonNode> results = simultaneously(calls);
        return new Burst(results, (System.nanoTime() - t0) / 1_000_000, contents);
    }

    private void assertUniqueNamesAndIntactHashes(Burst b, int expected) throws Exception {
        Set<String> names = new HashSet<>();
        for (int i = 0; i < b.results().size(); i++) {
            JsonNode r = b.results().get(i);
            JsonNode file = r.get("files").get(0);
            assertThat(r.get("failed").asInt()).as(r.toString()).isZero();
            assertThat(file.get("status").asText()).isEqualTo("CREATED"); // never INTEGRITY_MISMATCH / FAILED
            String id = file.get("evidenceId").asText();
            assertThat(file.get("sha256").asText())
                    .isEqualTo(com.pramaan.backend.util.Hashing.sha256Hex(b.contents().get(i).getBytes(StandardCharsets.UTF_8)));
            assertThat(json.readTree(mvc.perform(get("/api/v1/evidence/" + id + "/verify")).andReturn().getResponse()
                    .getContentAsString()).get("intact").asBoolean()).as("stored object intact").isTrue();
            names.add(json.readTree(mvc.perform(get("/api/v1/evidence/" + id)).andReturn().getResponse()
                    .getContentAsString()).get("title").asText());
        }
        assertThat(names).as("distinct generated names").hasSize(expected);
    }

    private void seedLargeApplication(String app, int records) {
        applications.upsert(new com.pramaan.backend.application.ApplicationDtos.UpsertRequest(app, app, null, null, null, null));
        for (int i = 0; i < records; i++) {
            ingestion.ingest(new EvidenceDtos.IngestRequest(app, "SEED-CTRL-" + (i % 50), "PCI_DSS", "BULK_UPLOAD",
                    "seed-" + i, "seed-" + i + ".txt", "text/plain", null, "seed body " + i,
                    java.time.Instant.parse("2026-03-05T10:00:00Z"), "seeder", java.util.Map.of(), java.util.Map.of()));
        }
    }

    /** Measured: 12 parallel uploads into an application that already holds 400 records. */
    @Test
    void twelveParallelUploadsIntoALargeApplicationAreTimedAndStayUnique() throws Exception {
        seedLargeApplication("perf-12", 400);
        Burst b = burst("perf-12", 12, "p12");
        System.out.println("PARALLEL-TIMING 12 uploads into an application with 400 records: " + b.millis() + " ms");
        assertUniqueNamesAndIntactHashes(b, 12);
    }

    /** FT015: 24 parallel uploads for one control/environment/day -> 24 unique names, no hash mismatch. */
    @Test
    void twentyFourParallelUploadsGetUniqueNamesAndNoHashMismatch() throws Exception {
        seedLargeApplication("perf-24", 100);
        Burst b = burst("perf-24", 24, "p24");
        System.out.println("PARALLEL-TIMING 24 uploads into an application with 100 records: " + b.millis() + " ms");
        assertUniqueNamesAndIntactHashes(b, 24);
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
