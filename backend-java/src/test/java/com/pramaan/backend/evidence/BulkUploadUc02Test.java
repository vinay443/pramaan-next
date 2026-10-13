package com.pramaan.backend.evidence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pramaan.backend.ai.EmbeddingStore;
import com.pramaan.backend.storage.ObjectStore;
import com.pramaan.backend.util.Hashing;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * UC02 - bulk evidence upload (FT001-FT016, code-level). Real controller, services, H2 database, in-memory object
 * store and embedding store; the storage outage is simulated by a spy on the {@link ObjectStore} interface.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:uc02;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "pramaan.bulk-upload.max-file-bytes=2048",
        "pramaan.bulk-upload.max-zip-entries=5",
        "pramaan.bulk-upload.simulate-failure-marker=simfail"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class BulkUploadUc02Test {

    private static final String ROLE = "X-Pramaan-Role";
    private static final String USER = "X-Pramaan-User";
    private static final String UPLOAD = "/api/v1/evidence/bulk/upload";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired EmbeddingStore embeddings;
    @Autowired com.pramaan.backend.application.ApplicationService applications;
    @SpyBean ObjectStore objectStore;

    private static MockMultipartFile file(String name, String content) {
        return new MockMultipartFile("files", name, "text/plain", content.getBytes(StandardCharsets.UTF_8));
    }

    private static MockMultipartFile bytes(String name, byte[] content) {
        return new MockMultipartFile("files", name, "application/octet-stream", content);
    }

    private MockHttpServletRequestBuilder req(String role, MockMultipartFile... files) {
        MockMultipartHttpServletRequestBuilder b = multipart(UPLOAD);
        for (MockMultipartFile f : files) b.file(f);
        if (role != null) b.header(ROLE, role);
        return b.header(USER, "App Owner")
                .param("applicationSlug", "net-banking").param("framework", "PCI_DSS")
                .param("controlId", "MW-TLS-VERSION");
    }

    /** Like req() but with its own application/framework/control (param() would append a second value). */
    private MockHttpServletRequestBuilder reqAs(String app, String framework, String control, MockMultipartFile... files) {
        MockMultipartHttpServletRequestBuilder b = multipart(UPLOAD);
        for (MockMultipartFile f : files) b.file(f);
        return b.header(ROLE, "APP").header(USER, "App Owner").param("applicationSlug", app)
                .param("framework", framework).param("controlId", control);
    }

    private JsonNode upload(MockHttpServletRequestBuilder b) throws Exception {
        return json.readTree(mvc.perform(b).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private JsonNode getJson(String url, String role) throws Exception {
        return json.readTree(mvc.perform(get(url).header(ROLE, role)).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString());
    }

    private JsonNode evidence(String id) throws Exception {
        return getJson("/api/v1/evidence/" + id, "APP");
    }

    // ---- FT001 / FT002 / FT003 ---------------------------------------------------------------------------------

    @Test
    void ft001_multipleValidFilesAreAcceptedWithCommonMetadata() throws Exception {
        JsonNode r = upload(req("APP", file("a.txt", "alpha"), file("b.csv", "x,y\n1,2"), file("c.json", "{\"k\":1}"))
                .param("environment", "prod").param("technology", "nginx"));
        assertThat(UUID.fromString(r.get("batchId").asText())).isNotNull();
        assertThat(r.get("status").asText()).isEqualTo("COMPLETED");
        assertThat(r.get("received").asInt()).isEqualTo(3);
        assertThat(r.get("created").asInt()).isEqualTo(3);
        assertThat(r.get("files")).allSatisfy(f -> assertThat(f.get("status").asText()).isEqualTo("CREATED"));
    }

    @Test
    void ft002_missingOrUnsafeMandatoryMetadataBlocksTheSubmissionWithAClearMessage() throws Exception {
        mvc.perform(multipart(UPLOAD).file(file("a.txt", "alpha")).header(ROLE, "APP")
                        .param("applicationSlug", "net-banking").param("framework", "PCI_DSS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("controlId is required"));
        mvc.perform(req("APP", file("a.txt", "alpha")).param("framework", "<b>x</b>"))
                .andExpect(status().isBadRequest());
        mvc.perform(multipart(UPLOAD).header(ROLE, "APP").param("applicationSlug", "net-banking")
                        .param("framework", "PCI_DSS").param("controlId", "MW-TLS-VERSION"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("select at least one file (or a single .zip archive)"));
        mvc.perform(get("/api/v1/evidence").param("applicationSlug", "net-banking"))
                .andExpect(jsonPath("$.totalItems").value(0));
        assertThat(getJson("/api/v1/security/events?type=INPUT_REJECTED", "ADMIN").size()).isGreaterThanOrEqualTo(2);
    }

    @org.junit.jupiter.api.BeforeEach
    void seedApplication() {
        applications.upsert(new com.pramaan.backend.application.ApplicationDtos.UpsertRequest(
                "net-banking", "Net Banking", null, null, null, null));
    }

    private void assertRejectedBeforeAnyFileIsProcessed(MockHttpServletRequestBuilder b, String message) throws Exception {
        mvc.perform(b).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(message));
        mvc.perform(get("/api/v1/evidence").param("applicationSlug", "net-banking"))
                .andExpect(jsonPath("$.totalItems").value(0));
        mvc.perform(get("/api/v1/scheduler/runs").header(ROLE, "ADMIN").param("trigger", "BULK"))
                .andExpect(jsonPath("$.totalItems").value(0));
    }

    @Test
    void ft002_unknownApplicationIsRejectedAndNeverAutoCreated() throws Exception {
        mvc.perform(reqAs("no-such-app", "PCI_DSS", "MW-TLS-VERSION", file("a.txt", "alpha")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("applicationSlug 'no-such-app' is not a known application - "
                        + "choose an existing application (bulk upload never creates one)"));
        assertThat(applications.exists("no-such-app")).isFalse();
        mvc.perform(get("/api/v1/applications")).andExpect(jsonPath("$[?(@.slug=='no-such-app')]").isEmpty());
        mvc.perform(get("/api/v1/evidence").param("applicationSlug", "no-such-app"))
                .andExpect(jsonPath("$.totalItems").value(0));
    }

    @Test
    void ft002_unknownFrameworkControlAndEnvironmentAreRejectedBeforeAnyFileIsProcessed() throws Exception {
        assertRejectedBeforeAnyFileIsProcessed(reqAs("net-banking", "NOT_A_FW", "MW-TLS-VERSION", file("a.txt", "alpha")),
                "framework 'NOT_A_FW' is not a known framework; choose one of [C-SITE, DB_BASELINING, DPSC, "
                        + "INTERNAL_AUDIT, IS, ISG, ISO27001, ITDRM, ITPP, NGINX_BASELINING, OS_BASELINING, PCI_DSS, VAPT]");
        assertRejectedBeforeAnyFileIsProcessed(reqAs("net-banking", "PCI_DSS", "ZZZ-999", file("a.txt", "alpha")),
                "controlId 'ZZZ-999' is not a valid control for framework PCI_DSS in the control catalog");
        // a real control, but not one of this framework's
        assertRejectedBeforeAnyFileIsProcessed(reqAs("net-banking", "PCI_DSS", "NGBL-C8", file("a.txt", "alpha")),
                "controlId 'NGBL-C8' is not a valid control for framework PCI_DSS in the control catalog");
        assertRejectedBeforeAnyFileIsProcessed(req("APP", file("a.txt", "alpha")).param("environment", "MARS"),
                "environment 'MARS' is not valid; choose one of [PROD, UAT, DR, DEV]");
        assertThat(getJson("/api/v1/security/events?type=INPUT_REJECTED", "ADMIN").size()).isGreaterThanOrEqualTo(5);
    }

    @Test
    void ft004_newVersionWithBlankTechnologyKeepsTheExistingTechnology() throws Exception {
        JsonNode v1 = upload(req("APP", file("tech.txt", "first")).param("technology", "nginx"));
        String id = v1.get("files").get(0).get("evidenceId").asText();
        assertThat(evidence(id).get("tags").get("technology").asText()).isEqualTo("nginx");

        JsonNode v2 = upload(req("APP", file("tech.txt", "second, different")));
        assertThat(v2.get("files").get(0).get("status").asText()).isEqualTo("NEW_VERSION");
        assertThat(evidence(id).get("tags").get("technology").asText()).isEqualTo("nginx");

        // an explicit technology on a later version still wins
        upload(req("APP", file("tech.txt", "third, different again")).param("technology", "tomcat"));
        assertThat(evidence(id).get("tags").get("technology").asText()).isEqualTo("tomcat");
    }

    @Test
    void ft005_checksumsAreMatchedByFilePositionSoTheSameNameTwiceInOneBatchIsNotAFalseMismatch() throws Exception {
        String first = "first bytes";
        String second = "second, different bytes";
        String byIndex = "{\"#0\":\"" + Hashing.sha256Hex(first.getBytes(StandardCharsets.UTF_8))
                + "\",\"#1\":\"" + Hashing.sha256Hex(second.getBytes(StandardCharsets.UTF_8)) + "\"}";
        JsonNode r = upload(req("APP", file("same.txt", first), file("same.txt", second)).param("checksums", byIndex));
        assertThat(r.get("failed").asInt()).isZero();
        assertThat(r.get("files").get(0).get("status").asText()).isEqualTo("CREATED");
        assertThat(r.get("files").get(1).get("status").asText()).isEqualTo("NEW_VERSION");

        // an older client's name-keyed map is ambiguous when a name repeats: it is ignored, not applied to both
        String byName = "{\"same.txt\":\"" + Hashing.sha256Hex(first.getBytes(StandardCharsets.UTF_8)) + "\"}";
        JsonNode legacy = upload(req("APP", file("dup.txt", first), file("dup.txt", second)).param("checksums", byName));
        assertThat(legacy.get("files").toString()).doesNotContain("INTEGRITY_MISMATCH");

        // a wrong hash at a position still rejects exactly that file
        String wrong = "{\"#0\":\"" + Hashing.sha256Hex(first.getBytes(StandardCharsets.UTF_8)) + "\",\"#1\":\"" + "0".repeat(64) + "\"}";
        JsonNode bad = upload(req("APP", file("other.txt", first), file("other.txt", second)).param("checksums", wrong));
        assertThat(bad.get("files").get(0).get("status").asText()).isEqualTo("CREATED");
        assertThat(bad.get("files").get(1).get("code").asText()).isEqualTo("INTEGRITY_MISMATCH");
    }

    @Test
    void aNonZipNamedZipIsReportedAsNotAValidArchive() throws Exception {
        JsonNode r = upload(req("APP", bytes("fake.zip", "this is not a zip".getBytes(StandardCharsets.UTF_8))));
        assertThat(r.get("files").get(0).get("code").asText()).isEqualTo("BAD_ARCHIVE");
        assertThat(r.get("files").get(0).get("message").asText()).isEqualTo("not a valid zip archive");
    }

    @Test
    void aZipTogetherWithOtherFilesIsRejectedWithAClearMessage() throws Exception {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(buf)) {
            z.putNextEntry(new ZipEntry("in.txt"));
            z.write("x".getBytes(StandardCharsets.UTF_8));
            z.closeEntry();
        }
        assertRejectedBeforeAnyFileIsProcessed(req("APP", bytes("a.zip", buf.toByteArray()), file("b.txt", "beta")),
                "upload either individual files or a single .zip archive, not a .zip together with other files");
    }

    @Test
    void duplicateUploadFromAnotherEnvironmentKeepsTheRecordsOriginalEnvironment() throws Exception {
        JsonNode first = upload(req("APP", file("env.txt", "same bytes")).param("environment", "PROD"));
        String id = first.get("files").get(0).get("evidenceId").asText();
        assertThat(evidence(id).get("tags").get("environment").asText()).isEqualTo("PROD");

        JsonNode again = upload(req("APP", file("env.txt", "same bytes")).param("environment", "UAT"));
        assertThat(again.get("files").get(0).get("status").asText()).isEqualTo("DUPLICATE");
        assertThat(again.get("files").get(0).get("evidenceId").asText()).isEqualTo(id);
        assertThat(again.get("batchId").asText()).isNotEqualTo(first.get("batchId").asText());

        JsonNode tags = evidence(id).get("tags");
        assertThat(tags.get("environment").asText()).isEqualTo("PROD");
        assertThat(tags.get("batchId").asText()).isEqualTo(first.get("batchId").asText());
    }

    @Test
    void ft002_pathSeparatorsAndDotDotInIdentifiersAreRejectedAndRecorded() throws Exception {
        String[][] bad = {{"controlId", "../../etc/passwd"}, {"controlId", "a/b"}, {"controlId", "a\\b"},
                {"controlId", "a..b"}, {"applicationSlug", "net/banking"}, {"framework", "..\\PCI"}};
        for (String[] p : bad) {
            mvc.perform(req("APP", file("a.txt", "alpha")).param(p[0], p[1]))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(
                            p[0] + " must not contain path separators or \"..\" (/ \\ ..)"));
        }
        mvc.perform(get("/api/v1/evidence").param("applicationSlug", "net-banking"))
                .andExpect(jsonPath("$.totalItems").value(0));
        assertThat(getJson("/api/v1/security/events?type=INPUT_REJECTED", "ADMIN").size())
                .isGreaterThanOrEqualTo(bad.length);
    }

    @Test
    void ft003_mixedValidAndInvalidFilesAreReportedIndividuallyAndTheBatchIsNotLost() throws Exception {
        JsonNode r = upload(req("APP", file("good.txt", "good"), file("malware.exe", "MZ-ish"),
                file("empty.txt", ""), file("also-good.csv", "a,b")));
        assertThat(r.get("status").asText()).isEqualTo("PARTIAL");
        assertThat(r.get("received").asInt()).isEqualTo(4);
        assertThat(r.get("created").asInt()).isEqualTo(2);
        assertThat(r.get("failed").asInt()).isEqualTo(2);
        assertThat(r.get("files").get(1).get("status").asText()).isEqualTo("REJECTED");
        assertThat(r.get("files").get(1).get("code").asText()).isEqualTo("TYPE_NOT_ALLOWED");
        assertThat(r.get("files").get(2).get("code").asText()).isEqualTo("EMPTY_FILE");
        assertThat(r.get("errors").get(0).get("message").asText()).startsWith("malware.exe:");
        mvc.perform(get("/api/v1/evidence").param("applicationSlug", "net-banking"))
                .andExpect(jsonPath("$.totalItems").value(2));
    }

    // ---- FT004 / FT005 / FT006 -------------------------------------------------------------------------------

    @Test
    void ft004_perFileMetadataIsPersistedOnTheRecordVersionAndAuditTrail() throws Exception {
        JsonNode r = upload(req("APP", file("tls.txt", "ssl_protocols TLSv1.3;"))
                .param("environment", "uat").param("technology", "nginx"));
        String batch = r.get("batchId").asText();
        JsonNode ev = evidence(r.get("files").get(0).get("evidenceId").asText());
        assertThat(ev.get("sourceObjectId").asText()).isEqualTo("tls.txt");
        assertThat(ev.get("sourceSystem").asText()).isEqualTo("BULK_UPLOAD");
        assertThat(ev.get("applicationSlug").asText()).isEqualTo("net-banking");
        assertThat(ev.get("framework").asText()).isEqualTo("PCI_DSS");
        assertThat(ev.get("controlId").asText()).isEqualTo("MW-TLS-VERSION");
        JsonNode tags = ev.get("tags");
        assertThat(tags.get("environment").asText()).isEqualTo("UAT");
        assertThat(tags.get("uploadedBy").asText()).isEqualTo("App Owner");
        assertThat(tags.get("batchId").asText()).isEqualTo(batch);
        assertThat(tags.get("technology").asText()).isEqualTo("nginx");
        assertThat(tags.get("collectionMethod").asText()).isEqualTo("bulk");
        JsonNode latest = ev.get("latest");
        assertThat(latest.get("collectedBy").asText()).isEqualTo("App Owner");
        assertThat(latest.get("ingestionRunId").asText()).isEqualTo(batch);
        assertThat(latest.get("collectedAt").asText()).isNotBlank();
        assertThat(ev.get("createdAt").asText()).isNotBlank();
        JsonNode history = getJson("/api/v1/evidence/" + ev.get("evidenceId").asText() + "/lifecycle", "APP").get("history");
        assertThat(history.get(0).get("action").asText()).isEqualTo("INGESTED");
        assertThat(history.get(0).get("actor").asText()).isEqualTo("App Owner");
        assertThat(history.get(0).get("occurredAt").asText()).isNotBlank();
    }

    @Test
    void ft005_duplicatesFollowTheVersionRulesAndNeverSilentlyMultiply() throws Exception {
        JsonNode first = upload(req("APP", file("policy.txt", "v1 text")));
        String id = first.get("files").get(0).get("evidenceId").asText();

        JsonNode again = upload(req("APP", file("policy.txt", "v1 text")));
        assertThat(again.get("files").get(0).get("status").asText()).isEqualTo("DUPLICATE");
        assertThat(again.get("files").get(0).get("evidenceId").asText()).isEqualTo(id);
        assertThat(again.get("status").asText()).isEqualTo("COMPLETED");
        assertThat(again.get("duplicates").asInt()).isEqualTo(1);
        assertThat(again.get("created").asInt()).isZero();
        // a duplicate neither adds a version nor re-homes the record to the newer batch
        JsonNode ev = evidence(id);
        assertThat(ev.get("currentVersion").asInt()).isEqualTo(1);
        assertThat(ev.get("tags").get("batchId").asText()).isEqualTo(first.get("batchId").asText());
        JsonNode history = getJson("/api/v1/evidence/" + id + "/lifecycle", "APP").get("history");
        assertThat(history.toString()).contains("DUPLICATE_SKIPPED");

        JsonNode changed = upload(req("APP", file("policy.txt", "v2 text, changed")));
        assertThat(changed.get("files").get(0).get("status").asText()).isEqualTo("NEW_VERSION");
        assertThat(changed.get("files").get(0).get("version").asInt()).isEqualTo(2);

        // different context (control) is a different record, not a duplicate
        JsonNode otherCtx = upload(reqAs("net-banking", "PCI_DSS", "OS-SSH-ROOT-LOGIN", file("policy.txt", "v1 text")));
        assertThat(otherCtx.get("files").get(0).get("status").asText()).isEqualTo("CREATED");
    }

    @Test
    void ft006_sha256IsGeneratedStoredAndVerifiableAgainstTheObjectStore() throws Exception {
        String content = "integrity please";
        JsonNode r = upload(req("APP", file("hash.txt", content)));
        String sha = Hashing.sha256Hex(content.getBytes(StandardCharsets.UTF_8));
        String id = r.get("files").get(0).get("evidenceId").asText();
        assertThat(r.get("files").get(0).get("sha256").asText()).isEqualTo(sha);
        assertThat(evidence(id).get("latest").get("sha256").asText()).isEqualTo(sha);
        mvc.perform(get("/api/v1/evidence/{id}/verify", id)).andExpect(jsonPath("$.intact").value(true));
        // client-supplied hash that matches is accepted
        JsonNode ok = upload(req("APP", file("hash2.txt", content))
                .param("checksums", "{\"hash2.txt\":\"" + sha + "\"}"));
        assertThat(ok.get("files").get(0).get("status").asText()).isEqualTo("CREATED");
    }

    // ---- FT007 / FT008 -----------------------------------------------------------------------------------------

    @Test
    void ft007_batchStatusAndCountsMatchThePerFileOutcomesAndSurviveAReload() throws Exception {
        JsonNode r = upload(req("APP", file("one.txt", "1"), file("two.exe", "x"), file("three.txt", "3")));
        String batch = r.get("batchId").asText();
        JsonNode run = getJson("/api/v1/scheduler/runs/" + batch, "AUDITOR");
        assertThat(run.get("trigger").asText()).isEqualTo("BULK");
        assertThat(run.get("status").asText()).isEqualTo("PARTIAL");
        assertThat(run.get("received").asInt()).isEqualTo(3);
        assertThat(run.get("ingested").asInt()).isEqualTo(2);
        assertThat(run.get("failed").asInt()).isEqualTo(1);
        assertThat(run.get("perSource").has("one.txt")).isTrue();
        assertThat(run.get("perSource").get("two.exe").asText()).contains("errorCode=TYPE_NOT_ALLOWED");
        assertThat(run.get("triggeredBy").asText()).isEqualTo("App Owner");
        assertThat(run.get("startedAt").asText()).isNotBlank();
        assertThat(run.get("finishedAt").asText()).isNotBlank();

        JsonNode list = getJson("/api/v1/scheduler/runs?trigger=BULK", "APP");
        assertThat(list.get("items").toString()).contains(batch);
        mvc.perform(get("/api/v1/scheduler/runs?trigger=bogus").header(ROLE, "APP")).andExpect(status().isBadRequest());
    }

    @Test
    void ft008_batchMetadataFindsEachSuccessfulItemAndOpensIt() throws Exception {
        JsonNode r = upload(req("APP", file("s1.txt", "s1"), file("bad.exe", "x"), file("s2.txt", "s2")));
        String batch = r.get("batchId").asText();
        JsonNode found = json.readTree(mvc.perform(get("/api/v1/evidence").param("tag", "batchId:" + batch))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(found.get("totalItems").asInt()).isEqualTo(2);
        for (JsonNode item : found.get("items")) {
            mvc.perform(get("/api/v1/evidence/{id}", item.get("evidenceId").asText())).andExpect(status().isOk());
        }
    }

    // ---- FT009 / FT010 / FT011 ---------------------------------------------------------------------------------

    @Test
    void ft009_onlyAuthorizedRolesMayUpload() throws Exception {
        for (String ok : new String[] {"APP", "ADMIN"}) {
            mvc.perform(req(ok, file(ok + ".txt", ok))).andExpect(status().isOk());
        }
        for (String denied : new String[] {"AUDITOR", "CIO", "FH", "VH"}) {
            mvc.perform(req(denied, file(denied + ".txt", denied))).andExpect(status().isForbidden());
        }
        mvc.perform(req(null, file("x.txt", "x"))).andExpect(status().isForbidden());
        mvc.perform(req("NOBODY", file("x.txt", "x"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/evidence").param("applicationSlug", "net-banking"))
                .andExpect(jsonPath("$.totalItems").value(2));
    }

    @Test
    void ft010_everyActionIsAuditedWithUserAndTimestampAndReviewable() throws Exception {
        mvc.perform(req("AUDITOR", file("nope.txt", "x"))).andExpect(status().isForbidden());
        upload(req("APP", file("bad.exe", "x")));
        JsonNode events = getJson("/api/v1/security/events", "AUDITOR");
        String all = events.toString();
        assertThat(all).contains("ACCESS_DENIED").contains("UPLOAD_REJECTED").contains("bad.exe");
        JsonNode first = events.get(0);
        assertThat(first.get("occurredAt").asText()).isNotBlank();
        assertThat(first.get("actor").asText()).isEqualTo("App Owner");
        mvc.perform(get("/api/v1/security/events").header(ROLE, "APP")).andExpect(status().isForbidden());
    }

    @Test
    void ft011_batchCompletionAndFailureNotifyTheRecipients() throws Exception {
        upload(req("APP", file("ok.txt", "ok")));                       // COMPLETED
        upload(req("APP", file("ok2.txt", "ok"), file("bad.exe", "x"))); // PARTIAL
        upload(req("APP", file("only-bad.exe", "x")));                  // FAILED
        JsonNode list = getJson("/api/v1/notifications", "APP");
        String types = list.get("items").toString();
        assertThat(types).contains("RUN_COMPLETED").contains("RUN_PARTIAL").contains("RUN_FAILED").contains("bulk upload");
        assertThat(getJson("/api/v1/notifications", "ADMIN").get("items").size()).isGreaterThanOrEqualTo(3);
        assertThat(getJson("/api/v1/notifications", "AUDITOR").get("items")).isEmpty();
    }

    // ---- FT012 -------------------------------------------------------------------------------------------------

    @Test
    void ft012_uploadFlowsThroughRepositoryObjectStoreMetadataAndVectorIndexWithoutLoss() throws Exception {
        long before = embeddings.count();
        JsonNode r = upload(req("APP", file("flow.txt", "ssl_protocols TLSv1.3; ciphers strong")));
        String id = r.get("files").get(0).get("evidenceId").asText();
        JsonNode ev = evidence(id);
        String objectKey = getJson("/api/v1/evidence/" + id + "/versions", "APP").get(0).get("objectKey").asText();
        assertThat(objectStore.exists(objectKey)).isTrue();
        assertThat(new String(objectStore.get(objectKey).orElseThrow(), StandardCharsets.UTF_8))
                .isEqualTo("ssl_protocols TLSv1.3; ciphers strong");
        assertThat(ev.get("tags").get("batchId").asText()).isEqualTo(r.get("batchId").asText());
        assertThat(embeddings.count()).isGreaterThan(before);
        mvc.perform(get("/api/v1/evidence/{id}/verify", id)).andExpect(jsonPath("$.intact").value(true));
    }

    // ---- FT013 -------------------------------------------------------------------------------------------------

    @Test
    void ft013_aTemporaryStorageFailureIsRecoveredByRetryingOnlyTheFailedFile() throws Exception {
        doThrow(new IllegalStateException("MinIO unavailable")).doCallRealMethod()
                .when(objectStore).put(anyString(), any(), any());
        JsonNode first = upload(req("APP", file("a-first.txt", "A"), file("b-second.txt", "B")));
        assertThat(first.get("status").asText()).isEqualTo("PARTIAL");
        assertThat(first.get("files").get(0).get("status").asText()).isEqualTo("FAILED");
        assertThat(first.get("files").get(0).get("retryable").asBoolean()).isTrue();
        assertThat(first.get("files").get(1).get("status").asText()).isEqualTo("CREATED");

        JsonNode retry = upload(req("APP", file("a-first.txt", "A")).param("retryOfBatchId", first.get("batchId").asText()));
        assertThat(retry.get("status").asText()).isEqualTo("COMPLETED");
        assertThat(retry.get("files").get(0).get("status").asText()).isEqualTo("CREATED");
        JsonNode run = getJson("/api/v1/scheduler/runs/" + retry.get("batchId").asText(), "APP");
        assertThat(run.get("retryOfRunId").asText()).isEqualTo(first.get("batchId").asText());

        mvc.perform(get("/api/v1/evidence").param("applicationSlug", "net-banking"))
                .andExpect(jsonPath("$.totalItems").value(2));
        // the scheduler's own retry refuses a bulk batch instead of mis-running it as a collection
        mvc.perform(post("/api/v1/scheduler/runs/{id}/retry", first.get("batchId").asText()).header(ROLE, "APP"))
                .andExpect(status().isBadRequest());
        mvc.perform(req("APP", file("x.txt", "x")).param("retryOfBatchId", UUID.randomUUID().toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    void ft013_simulatedOutageMarkerFailsAFileOnceAndTheRetrySucceeds() throws Exception {
        JsonNode first = upload(req("APP", file("simfail-report.txt", "R")));
        assertThat(first.get("status").asText()).isEqualTo("FAILED");
        JsonNode retry = upload(req("APP", file("simfail-report.txt", "R")).param("retryOfBatchId", first.get("batchId").asText()));
        assertThat(retry.get("status").asText()).isEqualTo("COMPLETED");
    }

    // ---- FT014 -------------------------------------------------------------------------------------------------

    @Test
    void ft014_unsafeFilesTamperedHashesAndRoleBypassArePreventedAndRecorded() throws Exception {
        byte[] exe = new byte[] {'M', 'Z', 0, 0, 1, 2, 3};
        String eicar = "X5O!P%@AP[4\\PZX54(P^)7CC)7}$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*";
        JsonNode r = upload(req("APP",
                file("run.exe", "x"),                              // disallowed type
                bytes("disguised.txt", exe),                       // executable content
                file("eicar.txt", eicar),                          // malware signature
                file("fake.pdf", "this is not a pdf"),             // content / extension mismatch
                file("big.txt", "x".repeat(3000)),                 // oversized (limit 2048 in this test)
                file("tampered.txt", "real bytes"),                // wrong client hash
                file("..\\..\\evil.txt", "traversal"))             // path traversal in the name
                .param("checksums", "{\"tampered.txt\":\"" + "0".repeat(64) + "\"}"));
        assertThat(r.get("status").asText()).isEqualTo("FAILED");
        assertThat(r.get("created").asInt()).isZero();
        String[] codes = {"TYPE_NOT_ALLOWED", "EXECUTABLE_CONTENT", "MALWARE_SIGNATURE", "CONTENT_MISMATCH", "TOO_LARGE",
                "INTEGRITY_MISMATCH", "INVALID_NAME"};
        for (int i = 0; i < codes.length; i++) {
            assertThat(r.get("files").get(i).get("code").asText()).as(codes[i]).isEqualTo(codes[i]);
            assertThat(r.get("files").get(i).get("status").asText()).isEqualTo("REJECTED");
        }
        mvc.perform(get("/api/v1/evidence")).andExpect(jsonPath("$.totalItems").value(0));
        assertThat(getJson("/api/v1/security/events?type=UPLOAD_REJECTED&limit=50", "ADMIN").size()).isGreaterThanOrEqualTo(7);

        // role bypass: a reader role forging the upload is blocked and recorded
        mvc.perform(req("CIO", file("x.txt", "x"))).andExpect(status().isForbidden());
        assertThat(getJson("/api/v1/security/events?type=ACCESS_DENIED", "ADMIN").toString()).contains("bulk evidence upload");
    }

    @Test
    void ft014_archivesAreBoundedAndEntriesAreValidatedIndividually() throws Exception {
        ByteArrayOutputStream many = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(many)) {
            for (int i = 0; i < 8; i++) {
                z.putNextEntry(new ZipEntry("f" + i + ".txt"));
                z.write(("entry " + i).getBytes());
                z.closeEntry();
            }
        }
        JsonNode r = upload(req("APP", bytes("many.zip", many.toByteArray())));
        assertThat(r.get("created").asInt()).isEqualTo(5);                 // first 5 entries processed
        assertThat(r.get("files").toString()).contains("ARCHIVE_LIMIT");   // the rest is reported, not silently dropped

        ByteArrayOutputStream mixed = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(mixed)) {
            for (String n : new String[] {"ok.txt", "../escape.txt", "tool.exe"}) {
                z.putNextEntry(new ZipEntry(n));
                z.write("data".getBytes());
                z.closeEntry();
            }
        }
        JsonNode m = upload(req("APP", bytes("mixed.zip", mixed.toByteArray())));
        assertThat(m.get("created").asInt()).isEqualTo(1);
        assertThat(m.get("failed").asInt()).isEqualTo(2);
        assertThat(m.get("files").toString()).contains("INVALID_NAME").contains("TYPE_NOT_ALLOWED");
    }

    // ---- FT016 -------------------------------------------------------------------------------------------------

    @Test
    void ft016_retentionPolicyArchivesBulkEvidenceWithoutDeletingIt() throws Exception {
        JsonNode r = upload(req("APP", file("keep.txt", "keep me")));
        String id = r.get("files").get(0).get("evidenceId").asText();
        mvc.perform(put("/api/v1/retention/policy").header(ROLE, "ADMIN").contentType(MediaType.APPLICATION_JSON)
                .content("{\"retentionDays\":0}")).andExpect(status().isOk());
        mvc.perform(post("/api/v1/retention/archive").header(ROLE, "APP").contentType(MediaType.APPLICATION_JSON)
                .content("{\"mode\":\"EXECUTE\"}")).andExpect(status().isForbidden());
        MvcResult run = mvc.perform(post("/api/v1/retention/archive").header(ROLE, "ADMIN")
                .contentType(MediaType.APPLICATION_JSON).content("{\"mode\":\"EXECUTE\",\"application\":\"net-banking\"}"))
                .andExpect(status().isOk()).andReturn();
        assertThat(json.readTree(run.getResponse().getContentAsString()).get("archived").asInt()).isEqualTo(1);
        JsonNode ev = evidence(id);
        assertThat(ev.get("lifecycleState").asText()).isEqualTo("ARCHIVED");
        mvc.perform(get("/api/v1/evidence/{id}/verify", id)).andExpect(jsonPath("$.intact").value(true));
        mvc.perform(get("/api/v1/evidence/{id}/content", id)).andExpect(status().isOk());
    }
}
