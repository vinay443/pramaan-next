package com.pramaan.backend.insight;

import static org.assertj.core.api.Assertions.assertThat;

import com.pramaan.backend.evidence.EvidenceDtos.IngestRequest;
import com.pramaan.backend.evidence.EvidenceDtos.IngestResult;
import com.pramaan.backend.evidence.EvidenceIngestionService;
import com.pramaan.backend.insight.InsightDtos.CompletenessReport;
import com.pramaan.backend.insight.InsightDtos.ComplianceReport;
import com.pramaan.backend.insight.InsightDtos.Coverage;
import com.pramaan.backend.insight.InsightDtos.EvidenceSummary;
import com.pramaan.backend.insight.InsightDtos.LeadershipDashboard;
import com.pramaan.backend.insight.InsightDtos.NlQueryResult;
import com.pramaan.backend.insight.InsightDtos.ReuseResult;
import com.pramaan.backend.rules.RuleEvaluationService;
import com.pramaan.backend.rules.CheckDtos.EvaluateRequest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class InsightServicesTest {

    @Autowired EvidenceIngestionService ingestion;
    @Autowired RuleEvaluationService rules;
    @Autowired CompletenessService completeness;
    @Autowired ComplianceService compliance;
    @Autowired EvidenceReuseService reuse;
    @Autowired LeadershipService leadership;
    @Autowired EvidenceEmbeddingIndexer embeddingIndexer;
    @Autowired EvidenceSummaryService summaries;
    @Autowired NlQueryService nlQuery;

    private final Instant recent = Instant.now().minus(3, ChronoUnit.DAYS);

    private IngestResult ingest(String app, String control, String framework, String body) {
        return ingestion.ingest(new IngestRequest(app, control, framework, "AGENT_OS_LINUX",
                app + "/" + control, control, "application/json", null, body, recent, "agent",
                Map.of(), Map.of("team", app)));
    }

    @BeforeEach
    void seed() {
        // net-banking: 3 of 18 expected controls have current evidence
        ingest("net-banking", "OS-SSH-ROOT-LOGIN", "C-SITE",
                "{\"control\":\"OS-SSH-ROOT-LOGIN\",\"findings\":[{\"checkId\":\"OS-LNX-02\",\"status\":\"PASS\"}]}");
        ingest("net-banking", "OS-AUDIT-LOGGING", "PCI_DSS",
                "{\"control\":\"OS-AUDIT-LOGGING\",\"findings\":[{\"checkId\":\"OS-LNX-04\",\"status\":\"PASS\"}]}");
        ingest("net-banking", "MW-HSTS", "DPSC",
                "{\"control\":\"MW-HSTS\",\"findings\":[{\"checkId\":\"MW-03\",\"status\":\"FAIL\"}]}");
        // payments: same OS-SSH control, similar content -> reuse candidate
        ingest("payments", "OS-SSH-ROOT-LOGIN", "C-SITE",
                "{\"control\":\"OS-SSH-ROOT-LOGIN\",\"findings\":[{\"checkId\":\"OS-LNX-02\",\"status\":\"PASS\"}]}");
    }

    @Test
    void completenessCountsCoveredStaleAndMissing() {
        CompletenessReport r = completeness.forApplication("net-banking", null);
        assertThat(r.expected()).isEqualTo(18);
        assertThat(r.covered()).isEqualTo(3);
        assertThat(r.missing()).isEqualTo(15);
        assertThat(r.completenessPct()).isGreaterThan(0.0).isLessThan(100.0);
        assertThat(r.controls()).anyMatch(c -> c.controlId().equals("OS-SSH-ROOT-LOGIN")
                && c.coverage() == Coverage.COVERED);
        assertThat(r.controls()).anyMatch(c -> c.controlId().equals("TLS-CERT-EXPIRY")
                && c.coverage() == Coverage.MISSING);
    }

    @Test
    void complianceRollsUpDeterministicVerdicts() {
        rules.evaluate(new EvaluateRequest(null, null, null, null));
        ComplianceReport r = compliance.forApplication("net-banking", null);
        assertThat(r.expected()).isEqualTo(18);
        // OS-SSH + OS-AUDIT -> COMPLIANT ; MW-HSTS -> NON_COMPLIANT ; rest -> MISSING_EVIDENCE
        assertThat(r.compliant()).isEqualTo(2);
        assertThat(r.controls()).anyMatch(c -> c.controlId().equals("MW-HSTS")
                && c.status() == InsightDtos.ControlStatus.NON_COMPLIANT);
        assertThat(r.controls()).anyMatch(c -> c.controlId().equals("DB-TLS-IN-TRANSIT")
                && c.status() == InsightDtos.ControlStatus.MISSING_EVIDENCE);
        assertThat(r.byFramework()).isNotEmpty();
    }

    @Test
    void reuseFindsCrossApplicationEvidenceForTheSameControl() {
        UUID nbSsh = UUID.fromString(ingestExisting("net-banking", "OS-SSH-ROOT-LOGIN"));
        ReuseResult res = reuse.similarTo(nbSsh, 5, 0.1);
        assertThat(res.indexed()).isGreaterThanOrEqualTo(4);
        assertThat(res.matches()).isNotEmpty();
        assertThat(res.matches().get(0).evidenceId()).isNotEqualTo(nbSsh.toString());
        assertThat(res.matches()).anyMatch(m -> m.applicationSlug().equals("payments")
                && m.controlId().equals("OS-SSH-ROOT-LOGIN") && m.sameControl() && m.crossApplication());
    }

    @Test
    void reuseDetectsExactSha256DuplicatesAcrossApplications() {
        // net-banking and payments were seeded with byte-identical OS-SSH evidence
        UUID nbSsh = UUID.fromString(ingest("net-banking", "OS-SSH-ROOT-LOGIN", "C-SITE",
                "{\"control\":\"OS-SSH-ROOT-LOGIN\",\"findings\":[{\"checkId\":\"OS-LNX-02\",\"status\":\"PASS\"}]}")
                .evidenceId());
        ReuseResult res = reuse.similarTo(nbSsh, 5, 0.1);
        assertThat(res.querySha256()).isNotBlank();
        assertThat(res.exactDuplicates()).anyMatch(d -> d.applicationSlug().equals("payments")
                && d.exactDuplicate() && d.score() == 1.0);
    }

    @Test
    void leadershipDashboardAggregatesActualResultsAcrossApplications() {
        rules.evaluate(new EvaluateRequest(null, null, null, null));
        LeadershipDashboard d = leadership.dashboard();
        assertThat(d.applications()).isGreaterThanOrEqualTo(2);
        assertThat(d.expected()).isGreaterThan(0);
        assertThat(d.byApplication()).anyMatch(a -> a.applicationSlug().equals("net-banking")
                && a.compliant() >= 2);
        assertThat(d.checkVerdicts().values().stream().mapToInt(Integer::intValue).sum())
                .isGreaterThan(0);
        assertThat(d.byFramework()).isNotEmpty();
    }

    @Test
    void freeTextReuseSearchMatchesAParaphraseOfStoredEvidence() {
        // A record shaped like a real control evidence payload: JSON with field
        // names plus a prose description.
        ingest("payments", "DPSC-GOV-01", "DPSC",
                "{\"sourceSystem\":\"Vault\",\"application\":\"payments\",\"control\":\"DPSC-GOV-01\","
                        + "\"title\":\"Encryption at rest for the cardholder database\","
                        + "\"description\":\"All cardholder data stored in the payments database is "
                        + "encrypted at rest using AES-256 managed keys rotated every 90 days\","
                        + "\"status\":\"PASS\"}");
        // the in-memory embedding store is a singleton across tests; force a rebuild
        // so this record is indexed regardless of prior tests' state.
        embeddingIndexer.reindex();

        ReuseResult res = reuse.similarToText(
                "cardholder data in the payments database is encrypted at rest with AES-256 keys rotated regularly",
                5, 0.3);

        assertThat(res.queryText()).isNotNull();
        assertThat(res.matches()).isNotEmpty();
        assertThat(res.matches()).anyMatch(m -> m.controlId().equals("DPSC-GOV-01"));
    }

    @Test
    void aiSummaryIsGroundedAndDeterministic() {
        UUID id = UUID.fromString(ingestExisting("net-banking", "OS-AUDIT-LOGGING"));
        EvidenceSummary s1 = summaries.summarize(id);
        EvidenceSummary s2 = summaries.summarize(id);
        assertThat(s1.simulated()).isTrue();
        assertThat(s1.summary()).isEqualTo(s2.summary()).contains("[mock-ai]");
        assertThat(s1.groundedOn()).anyMatch(g -> g.contains("control: OS-AUDIT-LOGGING"));
    }

    @Test
    void nlQueryRoutesToCompletenessDeterministically() {
        NlQueryResult r = nlQuery.answer("which controls are missing for net-banking?", "net-banking");
        assertThat(r.matchedQuery()).isEqualTo("completeness");
        assertThat(r.answer()).containsKey("completenessPct");
        assertThat(r.simulated()).isTrue();
        assertThat(r.narrative()).contains("[mock-ai]");
    }

    @Test
    void nlQueryEvidenceLookupUsesRagAndCitesRetrievedEvidence() {
        embeddingIndexer.reindex();
        NlQueryResult r = nlQuery.answer("what evidence do we have for SSH root login on a linux host?", null);
        assertThat(r.matchedQuery()).isEqualTo("evidence-lookup");
        assertThat((List<?>) r.answer().get("evidenceCitations")).isNotEmpty();
        assertThat(r.answer().get("grounded")).isEqualTo(true);
        assertThat(r.answer().get("vectorStore")).isEqualTo("memory");
        assertThat(r.simulated()).isTrue();
        assertThat(r.narrative()).contains("[mock-ai]");
    }

    @Test
    void nlQueryEvidenceLookupRefusesWhenNoEvidenceMatches() {
        NlQueryResult r = nlQuery.answer("what evidence do we have for backup and restore?", "mobile-banking");
        assertThat(r.matchedQuery()).isEqualTo("evidence-lookup");
        assertThat(r.answer().get("grounded")).isEqualTo(false);
        assertThat(r.answer().get("answerText")).isEqualTo("No evidence found in the ECS repository.");
        assertThat(r.narrative()).contains("No evidence found in the ECS repository.");
    }

    @Test
    void nlQueryRoutesStaleAndSourceQuestions() {
        assertThat(nlQuery.answer("what evidence is stale?", null).matchedQuery()).isEqualTo("stale-evidence");
        assertThat(nlQuery.answer("where does our evidence come from?", null).matchedQuery())
                .isEqualTo("source-breakdown");
    }

    /** Helper: re-ingest identical content returns the existing evidence id (dedup). */
    private String ingestExisting(String app, String control) {
        String framework = control.startsWith("OS-AUDIT") ? "PCI_DSS" : "C-SITE";
        String body = "{\"control\":\"" + control + "\",\"findings\":[{\"checkId\":\"X\",\"status\":\"PASS\"}]}";
        return ingest(app, control, framework, body).evidenceId();
    }
}
