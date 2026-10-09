package com.pramaan.backend.insight;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pramaan.backend.common.ApiException;
import com.pramaan.backend.evidence.EvidenceDtos.IngestRequest;
import com.pramaan.backend.evidence.EvidenceDtos.IngestResult;
import com.pramaan.backend.evidence.EvidenceIngestionService;
import com.pramaan.backend.evidence.EvidenceQueryService;
import com.pramaan.backend.evidence.domain.EvidenceRecord;
import com.pramaan.backend.evidence.domain.EvidenceTag;
import com.pramaan.backend.evidence.repo.EvidenceRecordRepository;
import com.pramaan.backend.insight.InsightDtos.EvidenceSummary;
import com.pramaan.backend.insight.InsightDtos.SummaryFact;
import com.pramaan.backend.insight.summary.ContentKind;
import com.pramaan.backend.insight.summary.SummaryEnums.ControlSource;
import com.pramaan.backend.insight.summary.SummaryEnums.Freshness;
import com.pramaan.backend.insight.summary.SummaryEnums.Integrity;
import com.pramaan.backend.insight.summary.SummaryEnums.Severity;
import com.pramaan.backend.insight.summary.SummaryEnums.Verdict;
import com.pramaan.backend.rules.CheckStatus;
import com.pramaan.backend.rules.repo.CheckResultRepository;
import com.pramaan.backend.storage.ObjectStore;
import com.pramaan.backend.insight.summary.TestFiles;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Base64;
import java.util.Random;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Import(EvidenceSummaryServiceTest.ClockConfig.class)
class EvidenceSummaryServiceTest {

    /** Settable clock so age/freshness can be moved without touching stored evidence. */
    static final class MutableClock extends Clock {
        private volatile Instant now = Instant.now();

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    @TestConfiguration
    static class ClockConfig {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock();
        }
    }

    @Autowired EvidenceIngestionService ingestion;
    @Autowired EvidenceQueryService queries;
    @Autowired EvidenceSummaryService service;
    @Autowired EvidenceRecordRepository records;
    @Autowired CheckResultRepository checkResults;
    @Autowired ObjectMapper mapper;
    @Autowired MutableClock clock;
    @SpyBean ObjectStore objectStore;

    private Instant collected;

    @BeforeEach
    void setUp() {
        collected = clock.instant().minus(3, ChronoUnit.DAYS);
    }

    private IngestResult ingest(String control, String framework, String source, String contentType, String body) {
        return ingest("payments", control, framework, source, "obj-" + control, contentType, body, Map.of(), collected);
    }

    private IngestResult ingest(String app, String control, String framework, String source, String objectId,
                                String contentType, String body, Map<String, String> metadata, Instant at) {
        return ingestion.ingest(new IngestRequest(app, control, framework, source, objectId, null, contentType,
                null, body, at, "agent", metadata, Map.of()));
    }

    private EvidenceSummary summarize(IngestResult r) {
        return service.summarize(UUID.fromString(r.evidenceId()));
    }

    private static final String PASSING_BUNDLE =
            "{\"collector\":\"database\",\"status\": \"pass\",\"checks\":[{\"checkId\":\"DB-1\",\"status\":\"PASS\","
                    + "\"observed\":\"on\",\"expected\":\"on\",\"title\":\"TLS\"}]}";

    // ---- verdicts -----------------------------------------------------------------------

    @Test
    void passingBundleMeetsTheAutomatedChecks() {
        EvidenceSummary s = summarize(ingest("PCI-C6", "PCI_DSS", "AGENT_DB", "application/json", PASSING_BUNDLE));

        assertThat(s.verdict()).isEqualTo(Verdict.PASS);
        assertThat(s.method()).isEqualTo("DETERMINISTIC_RULES");
        assertThat(s.model()).isEqualTo("deterministic-rules/v1");
        assertThat(s.modelGenerated()).isFalse();
        assertThat(s.contentKind()).isEqualTo(ContentKind.CHECK_BUNDLE);
        assertThat(s.headline()).isEqualTo("Secure Systems & Software (PCI-C6, PCI DSS) — meets the automated checks. "
                + "1 of 1 check passed.");
        assertThat(s.control().source()).isEqualTo(ControlSource.LIBRARY);
        assertThat(s.control().description())
                .isEqualTo("Securely develop and maintain systems/software and remediate security vulnerabilities.");
        assertThat(s.checks()).hasSize(1);
        assertThat(s.checks().get(0).checkId()).isEqualTo("RULE-EVIDENCE-COLLECTED");
        assertThat(s.checks().get(0).status()).isEqualTo(CheckStatus.PASS);
        assertThat(s.version()).isEqualTo(1);
        assertThat(s.sha256()).hasSize(64);
        assertThat(s.summary()).startsWith(s.headline()).endsWith(
                "integrity intact; current.").contains("from AGENT_DB for payments (v1)");
        assertThat(s.groundedOn()).allMatch(g -> g.matches(".+: .+ \\[.+\\]"));
        assertThat(s.facts()).extracting(SummaryFact::pointer).allMatch(p -> !p.isBlank());
        assertThat(s.health().integrity()).isEqualTo(Integrity.INTACT);
        assertThat(s.health().freshness()).isEqualTo(Freshness.CURRENT);
        assertThat(s.health().ageDays()).isEqualTo(3);
        assertThat(s.health().versionCount()).isEqualTo(1);
        assertThat(s.health().lifecycleState()).isEqualTo("DRAFT");
        assertThat(s.health().missingTags()).isEmpty();
    }

    @Test
    void unrecognisedContentPassesWithWarnings() {
        EvidenceSummary s = summarize(ingest("PCI-C6", "PCI_DSS", "AGENT_DB", "application/json",
                "{\"note\":\"nothing a rule recognises\"}"));

        assertThat(s.verdict()).isEqualTo(Verdict.WARNING);
        assertThat(s.contentKind()).isEqualTo(ContentKind.STRUCTURED_DATA);
        assertThat(s.headline()).startsWith("Secure Systems & Software (PCI-C6, PCI DSS) — passes with warnings. JSON data of ");
    }

    @Test
    void aFailingCheckFailsTheEvidenceAndNamesTheCheck() {
        EvidenceSummary s = summarize(ingest("OS-SSH-ROOT-LOGIN", "C-SITE", "AGENT_OS", "application/json",
                "{\"collector\":\"os\",\"findings\":[{\"checkId\":\"OS-LNX-02\",\"status\":\"FAIL\",\"observed\":\"yes\","
                        + "\"expected\":\"no\",\"detail\":\"PermitRootLogin is enabled\"}]}"));

        assertThat(s.verdict()).isEqualTo(Verdict.FAIL);
        assertThat(s.headline()).isEqualTo("SSH root login disabled (OS-SSH-ROOT-LOGIN, C-SITE) — fails an automated check. "
                + "0 of 1 check passed, 1 failed. OS-LNX-02: observed yes, expected no — PermitRootLogin is enabled.");
        assertThat(s.findings().get(0).severity()).isEqualTo(Severity.FAIL);
        assertThat(s.findings().get(0).pointer()).isEqualTo("/findings/0");
    }

    @Test
    void notApplicableChecksGiveANotApplicableVerdict() {
        EvidenceSummary s = summarize(ingest("PCI-C6", "PCI_DSS", "AGENT_DB", "application/json",
                "{\"status\": \"not_applicable\"}"));
        assertThat(s.verdict()).isEqualTo(Verdict.NOT_APPLICABLE);
        assertThat(s.headline()).contains("— not applicable to this target.");
    }

    @Test
    void noCheckRowsMeansNotAssessedAndTheGetNeverWritesThem() {
        IngestResult r = ingest("PCI-C6", "PCI_DSS", "AGENT_DB", "application/json", PASSING_BUNDLE);
        UUID id = UUID.fromString(r.evidenceId());
        checkResults.deleteAll(checkResults.findByEvidenceRecordId(id));
        checkResults.flush();

        EvidenceSummary s = service.summarize(id);

        assertThat(s.verdict()).isEqualTo(Verdict.NOT_ASSESSED);
        assertThat(s.checks()).isEmpty();
        assertThat(s.headline()).contains("— collected, not assessed (no automated rule).");
        assertThat(s.findings()).anyMatch(f -> f.severity() == Severity.INFO
                && f.message().equals("No automated rule applies to this evidence."));
        assertThat(checkResults.findByEvidenceRecordId(id)).isEmpty();
    }

    @Test
    void checkResultsOlderThanTheCollectionAreDeclaredAsALimit() {
        IngestResult r = ingest("payments", "PCI-C6", "PCI_DSS", "AGENT_DB", "future", "application/json",
                PASSING_BUNDLE, Map.of(), clock.instant().plus(1, ChronoUnit.DAYS));
        assertThat(summarize(r).limits()).contains("Check results were evaluated on an older version.");
    }

    // ---- content handling ---------------------------------------------------------------

    @Test
    void secretsNeverAppearAnywhereInTheResponse() throws Exception {
        String bundle = "{\"collector\":\"os\",\"password\":\"hunter2\",\"token\":\"tok-9\","
                + "\"cert\":\"-----BEGIN PRIVATE KEY-----\\nMIIxxSECRETBODY\\n-----END PRIVATE KEY-----\","
                + "\"findings\":[{\"checkId\":\"S-1\",\"status\":\"FAIL\",\"observed\":\"password=hunter2 in config\","
                + "\"expected\":\"none\",\"detail\":\"apiKey: abc123 exposed\"}]}";
        String generic = "{\"db_password\":\"hunter2\",\"client_secret\":\"abc123\",\"name\":\"ok\","
                + "\"key\":\"-----BEGIN RSA PRIVATE KEY-----\\nMIIxxSECRETBODY\\n-----END RSA PRIVATE KEY-----\"}";

        for (String body : List.of(bundle, generic)) {
            EvidenceSummary s = summarize(ingest("PCI-C6", "PCI_DSS", "AGENT_" + body.hashCode(), "application/json", body));
            String wire = mapper.writeValueAsString(s);
            assertThat(wire).doesNotContain("hunter2", "abc123", "tok-9", "SECRETBODY");
        }
        EvidenceSummary s = summarize(ingest("PCI-C6", "PCI_DSS", "AGENT_BUNDLE", "application/json", bundle));
        assertThat(s.headline()).contains("password=[REDACTED] in config").contains("apiKey: [REDACTED] exposed");
    }

    @Test
    void tamperedObjectIsReportedAsMismatchAndStillDescribed() {
        IngestResult r = ingest("PCI-C6", "PCI_DSS", "AGENT_DB", "application/json", PASSING_BUNDLE);
        String key = queries.versions(UUID.fromString(r.evidenceId())).get(0).objectKey();
        doReturn(Optional.of("tampered-content".getBytes(StandardCharsets.UTF_8))).when(objectStore).get(key);

        EvidenceSummary s = summarize(r);

        assertThat(s.health().integrity()).isEqualTo(Integrity.MISMATCH);
        assertThat(s.headline()).contains("The stored content does not match the recorded SHA-256.");
        assertThat(s.findings().get(0).severity()).isEqualTo(Severity.FAIL);
        assertThat(s.findings().get(0).message()).contains("does not match the recorded SHA-256");
        assertThat(s.contentKind()).isEqualTo(ContentKind.TEXT); // describes the bytes as stored now
        assertThat(s.summary()).contains("integrity NOT intact");
    }

    @Test
    void missingObjectIsUnavailableNotAServerError() {
        IngestResult r = ingest("PCI-C6", "PCI_DSS", "AGENT_DB", "application/json", PASSING_BUNDLE);
        String key = queries.versions(UUID.fromString(r.evidenceId())).get(0).objectKey();
        doReturn(Optional.empty()).when(objectStore).get(key);

        EvidenceSummary s = summarize(r);

        assertThat(s.contentKind()).isEqualTo(ContentKind.UNAVAILABLE);
        assertThat(s.health().integrity()).isEqualTo(Integrity.UNVERIFIABLE);
        assertThat(s.limits()).isNotEmpty();
        assertThat(s.headline()).contains("The stored content is unavailable");
        assertThat(s.facts()).isNotEmpty().allMatch(f -> f.pointer().equals("record"));
        assertThat(s.summary()).contains("integrity unverifiable");
    }

    @Test
    void invalidJsonFallsBackToTextWithAWarning() {
        EvidenceSummary s = summarize(ingest("PCI-C6", "PCI_DSS", "AGENT_DB", "application/json",
                "{\"collector\": \"os\", \"findings\": ["));

        assertThat(s.contentKind()).isEqualTo(ContentKind.TEXT);
        assertThat(s.findings()).anyMatch(f -> f.severity() == Severity.WARNING
                && f.message().contains("not valid JSON"));
    }

    @Test
    void declaredAndDetectedTypeDisagreementIsAnInfoFinding() {
        EvidenceSummary s = summarize(ingest("PCI-C6", "PCI_DSS", "AGENT_DB", "application/pdf",
                "this is plainly text, not a pdf"));
        assertThat(s.findings()).anyMatch(f -> f.severity() == Severity.INFO
                && f.message().contains("does not match the detected content"));
    }

    @Test
    void readsAtMostOneMebibyteAndSaysSo() {
        String big = "abcdefghij".repeat(314_573); // ~3 MiB of text
        EvidenceSummary s = summarize(ingest("PCI-C6", "PCI_DSS", "AGENT_BIG", "text/plain", big));

        assertThat(s.contentKind()).isEqualTo(ContentKind.TEXT);
        assertThat(s.limits()).contains("Only the first 1.0 MiB of 3.0 MiB was read.");
    }

    @Test
    void factsAreCappedAtSixtyAndValuesAtTwoHundredCharacters() {
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < 200; i++) {
            sb.append(i == 0 ? "" : ",").append("\"f").append(String.format("%03d", i)).append("\":\"")
                    .append(i == 0 ? "y".repeat(500) : "v").append('"');
        }
        EvidenceSummary s = summarize(ingest("PCI-C6", "PCI_DSS", "AGENT_MANY", "application/json", sb.append("}").toString()));

        assertThat(s.facts()).hasSizeLessThanOrEqualTo(60);
        assertThat(s.limits()).anyMatch(l -> l.startsWith("Showing 50 of 200 facts"));
        assertThat(s.facts()).allMatch(f -> f.value().length() <= 200);
        assertThat(s.facts()).anyMatch(f -> f.value().length() == 200 && f.value().endsWith("…"));
        assertThat(s.groundedOn()).hasSize(s.facts().size());
    }

    @Test
    void jsonDeeperThanSixLevelsIsNotInspected() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 9; i++) sb.append("{\"l\":");
        sb.append("{\"status\":\"deep\"}").append("}".repeat(9));
        EvidenceSummary s = summarize(ingest("PCI-C6", "PCI_DSS", "AGENT_DEEP", "application/json", sb.toString()));

        assertThat(s.limits()).contains("JSON nested deeper than 6 levels was not inspected.");
        assertThat(s.findings()).noneMatch(f -> f.message().contains("deep"));
    }

    // ---- versions, controls, metadata ---------------------------------------------------------

    @Test
    void versionParameterSelectsThatVersionAndAnUnknownVersionIs404() {
        IngestResult v1 = ingest("payments", "PCI-C6", "PCI_DSS", "AGENT_DB", "same", "application/json",
                "{\"note\":\"first\"}", Map.of(), collected);
        IngestResult v2 = ingest("payments", "PCI-C6", "PCI_DSS", "AGENT_DB", "same", "application/json",
                PASSING_BUNDLE, Map.of(), collected);
        UUID id = UUID.fromString(v2.evidenceId());
        assertThat(v1.evidenceId()).isEqualTo(v2.evidenceId());

        EvidenceSummary current = service.summarize(id);
        EvidenceSummary first = service.summarize(id, 1);

        assertThat(current.version()).isEqualTo(2);
        assertThat(current.sha256()).isEqualTo(v2.sha256());
        assertThat(current.contentKind()).isEqualTo(ContentKind.CHECK_BUNDLE);
        assertThat(current.verdict()).isEqualTo(Verdict.PASS);
        assertThat(first.version()).isEqualTo(1);
        assertThat(first.sha256()).isEqualTo(v1.sha256());
        assertThat(first.contentKind()).isEqualTo(ContentKind.STRUCTURED_DATA);
        assertThat(first.verdict()).isEqualTo(Verdict.NOT_ASSESSED);
        assertThat(first.limits()).contains("Check results were evaluated on a different version; none exist for version 1.");
        assertThat(first.health().versionCount()).isEqualTo(2);

        assertThatThrownBy(() -> service.summarize(id, 99))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.status()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> service.summarize(UUID.randomUUID()))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.status()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void legacyControlFallsBackToItsTitleAndUnknownControlToItsId() {
        EvidenceSummary legacy = summarize(ingest("OS-SSH-ROOT-LOGIN", "C-SITE", "AGENT_OS", "application/json", PASSING_BUNDLE));
        assertThat(legacy.control().source()).isEqualTo(ControlSource.LEGACY);
        assertThat(legacy.control().name()).isEqualTo("SSH root login disabled");
        assertThat(legacy.control().description()).isEqualTo("SSH root login disabled");
        assertThat(legacy.control().alsoSatisfies()).extracting("framework").contains("PCI_DSS", "ISO27001")
                .doesNotContain("C-SITE");

        EvidenceSummary unknown = summarize(ingest("ZZ-UNKNOWN-1", "MY_FW", "AGENT_OS", "application/json", PASSING_BUNDLE));
        assertThat(unknown.control().source()).isEqualTo(ControlSource.UNKNOWN);
        assertThat(unknown.control().name()).isEqualTo("ZZ-UNKNOWN-1");
        assertThat(unknown.control().description()).isNull();
        assertThat(unknown.headline()).startsWith("ZZ-UNKNOWN-1 (ZZ-UNKNOWN-1, MY FW) — ");
    }

    @Test
    void simulatedFlagFollowsTheEvidenceMetadata() {
        IngestResult sim = ingest("payments", "PCI-C6", "PCI_DSS", "AGENT_DB", "sim", "application/json", PASSING_BUNDLE,
                Map.of("simulated", "true"), collected);
        IngestResult real = ingest("payments", "PCI-C6", "PCI_DSS", "AGENT_DB", "real", "application/json", PASSING_BUNDLE,
                Map.of(), collected);
        assertThat(summarize(sim).simulated()).isTrue();
        assertThat(summarize(real).simulated()).isFalse();
    }

    @Test
    void missingExpectedTagsAreListed() {
        IngestResult r = ingest("PCI-C6", "PCI_DSS", "AGENT_DB", "application/json", PASSING_BUNDLE);
        EvidenceRecord rec = records.findById(UUID.fromString(r.evidenceId())).orElseThrow();
        List<EvidenceTag> kept = new ArrayList<>();
        rec.getTags().stream().filter(t -> !t.getTagKey().equals("technology") && !t.getTagKey().equals("version"))
                .forEach(t -> kept.add(new EvidenceTag(UUID.randomUUID(), t.getTagKey(), t.getTagValue())));
        rec.replaceTags(kept);
        records.saveAndFlush(rec);

        assertThat(summarize(r).health().missingTags()).containsExactly("technology", "version");
    }

    // ---- determinism and caching --------------------------------------------------------------

    @Test
    void sameEvidenceSameChecksSameClockGiveIdenticalJsonExceptGeneratedAt() {
        IngestResult r = ingest("PCI-C6", "PCI_DSS", "AGENT_DB", "application/json", PASSING_BUNDLE);
        UUID id = UUID.fromString(r.evidenceId());

        ObjectNode a = mapper.valueToTree(service.summarize(id));
        clock.advance(Duration.ofSeconds(5));
        ObjectNode b = mapper.valueToTree(service.summarize(id));

        assertThat(a.get("generatedAt")).isNotEqualTo(b.get("generatedAt"));
        a.remove("generatedAt");
        b.remove("generatedAt");
        assertThat(b.toString()).isEqualTo(a.toString());
    }

    @Test
    void cacheReusesTheContentPartButHealthIsRecomputedEveryCall() {
        IngestResult r = ingest("PCI-C6", "PCI_DSS", "AGENT_DB", "application/json", PASSING_BUNDLE);
        UUID id = UUID.fromString(r.evidenceId());

        long hitsBefore = service.cacheHits();
        EvidenceSummary first = service.summarize(id);
        assertThat(service.cacheHits()).isEqualTo(hitsBefore);

        clock.advance(Duration.ofDays(100));
        EvidenceSummary second = service.summarize(id);

        assertThat(service.cacheHits()).isEqualTo(hitsBefore + 1);
        assertThat(second.facts()).isEqualTo(first.facts());
        assertThat(second.findings()).isEqualTo(first.findings());
        assertThat(second.contentKind()).isEqualTo(first.contentKind());
        assertThat(first.health().freshness()).isEqualTo(Freshness.CURRENT);
        assertThat(second.health().freshness()).isEqualTo(Freshness.STALE);
        assertThat(second.health().ageDays()).isEqualTo(103);
        assertThat(second.summary()).endsWith("integrity intact; stale.");
    }

    // ---- file evidence (PDF / DOCX) --------------------------------------------------------------

    private IngestResult ingestFile(String control, String source, String contentType, byte[] bytes, String objectId) {
        return ingestion.ingest(new IngestRequest("payments", control, "PCI_DSS", source, objectId, null, contentType,
                Base64.getEncoder().encodeToString(bytes), null, collected, "uploader",
                Map.of("upload.filename", "evidence-file"), Map.of()));
    }

    private static byte[] policyDocx(String reviewDate) {
        return TestFiles.docx(TestFiles.coreXml("Secure Development Policy", "Priya Nair", null, null, "2026-09-01T09:00:00Z"),
                null, TestFiles.documentXml("H1:Secure Development Policy", "Approved by: Priya Nair",
                        "Next review: " + reviewDate, "Systems and software must be maintained securely."));
    }

    private static final String DOCX_TYPE = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    @Test
    void docxEvidenceIsSummarisedFromItsContentAndNeverClaimedToProveTheControl() {
        EvidenceSummary s = summarize(ingestFile("PCI-C6", "UPLOAD", DOCX_TYPE, policyDocx("2000-01-01"), "docx-1"));

        assertThat(s.contentKind()).isEqualTo(ContentKind.DOCUMENT);
        assertThat(s.headline()).startsWith("Secure Systems & Software (PCI-C6, PCI DSS) — ").contains("DOCX of ")
                .contains("title \"Secure Development Policy\"; last modified 2026-09-01; approved by Priya Nair.");
        assertThat(s.findings()).anyMatch(f -> f.severity() == Severity.WARNING
                && f.message().equals("Document states it was due for review on 2000-01-01."));
        assertThat(s.facts()).anyMatch(f -> f.label().equals("approved by") && f.value().equals("Priya Nair"));
        assertThat(s.facts()).anyMatch(f -> f.label().startsWith("Keyword overlap") && f.value().startsWith("Mentions: "));
        assertThat(s.health().integrity()).isEqualTo(Integrity.INTACT);
        assertThat(s.facts().size()).isLessThanOrEqualTo(60);
    }

    @Test
    void pdfEvidenceGivesPagesAndMetadata() {
        byte[] pdf = TestFiles.pdf("Access Review Report", "Meera Iyer", "2026-09-01", "Access Review Report\nOwner: Meera Iyer");
        EvidenceSummary s = summarize(ingestFile("PCI-C6", "UPLOAD", "application/pdf", pdf, "pdf-1"));

        assertThat(s.contentKind()).isEqualTo(ContentKind.DOCUMENT);
        assertThat(s.facts()).anyMatch(f -> f.label().equals("pages") && f.value().equals("1"));
        assertThat(s.headline()).contains("PDF of ").contains("1 page; title \"Access Review Report\"");
    }

    @Test
    void aDeclaredTypeThatContradictsTheBytesIsAnInfoFinding() {
        EvidenceSummary s = summarize(ingestFile("PCI-C6", "UPLOAD", "application/pdf", policyDocx("2099-01-01"), "mismatch-1"));
        assertThat(s.contentKind()).isEqualTo(ContentKind.DOCUMENT);
        assertThat(s.findings()).anyMatch(f -> f.severity() == Severity.INFO
                && f.message().contains("does not match the detected content (Word (DOCX) document)"));
    }

    @Test
    void officeAndPdfFilesAreReadBeyondOneMebibyte() {
        byte[] noise = new byte[2 * 1024 * 1024];
        new Random(1).nextBytes(noise);
        java.util.Map<String, byte[]> parts = new java.util.LinkedHashMap<>();
        parts.put("word/document.xml", TestFiles.documentXml("H1:Large Policy", "Owner: Meera Iyer").getBytes(StandardCharsets.UTF_8));
        parts.put("word/media/image1.bin", noise);
        parts.put("docProps/core.xml", TestFiles.coreXml("Large Policy", null, null, null, null).getBytes(StandardCharsets.UTF_8));
        EvidenceSummary s = summarize(ingestFile("PCI-C6", "UPLOAD", DOCX_TYPE, TestFiles.zip(parts), "big-1"));

        assertThat(s.contentKind()).isEqualTo(ContentKind.DOCUMENT);
        assertThat(s.limits()).noneMatch(l -> l.startsWith("Only the first"));
        assertThat(s.facts()).anyMatch(f -> f.label().equals("owner") && f.value().equals("Meera Iyer"));
    }

    @Test
    void theReviewDateWarningFollowsTheInjectedClockEvenWhenTheSummaryWasCached() {
        String review = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC).plusDays(10).toString();
        UUID id = UUID.fromString(ingestFile("PCI-C6", "UPLOAD", DOCX_TYPE, policyDocx(review), "clock-1").evidenceId());

        assertThat(service.summarize(id).findings()).noneMatch(f -> f.message().startsWith("Document states it was due"));

        clock.advance(Duration.ofDays(20));
        assertThat(service.summarize(id).findings()).anyMatch(f -> f.message().equals(
                "Document states it was due for review on " + review + "."));
    }
}
