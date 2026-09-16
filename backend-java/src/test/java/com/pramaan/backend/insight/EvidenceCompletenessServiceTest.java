package com.pramaan.backend.insight;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

import com.pramaan.backend.evidence.EvidenceDtos.IntegrityStatus;
import com.pramaan.backend.evidence.EvidenceDtos.IngestRequest;
import com.pramaan.backend.evidence.EvidenceDtos.IngestResult;
import com.pramaan.backend.evidence.EvidenceIngestionService;
import com.pramaan.backend.evidence.EvidenceQueryService;
import com.pramaan.backend.insight.InsightDtos.ControlCompletenessRow;
import com.pramaan.backend.insight.InsightDtos.EvidenceCompletenessItem;
import com.pramaan.backend.insight.InsightDtos.EvidenceCompletenessReport;
import com.pramaan.backend.insight.InsightDtos.FrameworkCompletenessRow;
import com.pramaan.backend.storage.ObjectStore;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class EvidenceCompletenessServiceTest {

    @Autowired EvidenceIngestionService ingestion;
    @Autowired EvidenceQueryService queries;
    @Autowired EvidenceCompletenessService scoring;
    @SpyBean ObjectStore objectStore;

    private static final String SUBSTANTIVE_CONTENT =
            "{\"control\":\"OS-SSH-ROOT-LOGIN\",\"findings\":[{\"checkId\":\"OS-LNX-02\",\"status\":\"PASS\"}]}";

    private IngestResult ingest(String app, String control, String framework, String sourceSystem,
                                String body, Instant collectedAt, String collectedBy,
                                Map<String, String> tags) {
        return ingestion.ingest(new IngestRequest(app, control, framework, sourceSystem,
                app + "/" + control, control, "application/json", null, body, collectedAt,
                collectedBy, Map.of(), tags));
    }

    private EvidenceCompletenessItem itemFor(String evidenceId, String applicationSlug) {
        EvidenceCompletenessReport r = scoring.forScope(applicationSlug, null);
        return r.items().stream().filter(i -> i.evidenceId().equals(evidenceId)).findFirst()
                .orElseThrow(() -> new AssertionError("no item for " + evidenceId));
    }

    @Test
    void fullyCompleteItemLandsInTheCompleteBand() {
        IngestResult r = ingest("net-banking", "OS-SSH-ROOT-LOGIN", "C-SITE", "AGENT_OS_LINUX",
                SUBSTANTIVE_CONTENT, Instant.now().minus(3, ChronoUnit.DAYS), "agent-scanner",
                Map.of("technology", "linux"));

        EvidenceCompletenessItem item = itemFor(r.evidenceId(), "net-banking");

        assertThat(item.band()).isEqualTo("COMPLETE");
        assertThat(item.completenessPct()).isGreaterThanOrEqualTo(90);
        assertThat(item.factors()).allMatch(f -> f.status().equals("ok"));
    }

    @Test
    void missingOptionalMetadataDropsToPartial() {
        // no collectedBy, and a source system that doesn't hint at any technology -> two lesser-severity misses
        IngestResult r = ingest("net-banking", "OS-SSH-ROOT-LOGIN", "C-SITE", "MOCK_JIRA",
                SUBSTANTIVE_CONTENT, Instant.now().minus(3, ChronoUnit.DAYS), null, Map.of());

        EvidenceCompletenessItem item = itemFor(r.evidenceId(), "net-banking");

        assertThat(item.band()).isEqualTo("PARTIAL");
        assertThat(item.completenessPct()).isBetween(60, 89);
        assertThat(item.factors()).anyMatch(f -> f.factor().equals("Collected by") && f.status().equals("fail"));
        assertThat(item.factors()).anyMatch(f -> f.factor().equals("Technology") && f.status().equals("fail"));
    }

    @Test
    void staleEvidenceLosesOnlyTheFreshnessFactor() {
        IngestResult r = ingest("net-banking", "OS-SSH-ROOT-LOGIN", "C-SITE", "AGENT_OS_LINUX",
                SUBSTANTIVE_CONTENT, Instant.now().minus(120, ChronoUnit.DAYS), "agent-scanner",
                Map.of("technology", "linux"));

        EvidenceCompletenessItem item = itemFor(r.evidenceId(), "net-banking");

        assertThat(item.factors()).anyMatch(f -> f.factor().equals("Freshness") && f.status().equals("fail")
                && f.detail().contains("exceeds"));
        assertThat(item.band()).isEqualTo("PARTIAL");
    }

    @Test
    void integrityFailureCascadesToContentAndLandsIncomplete() {
        IngestResult r = ingest("net-banking", "OS-SSH-ROOT-LOGIN", "C-SITE", "AGENT_OS_LINUX",
                SUBSTANTIVE_CONTENT, Instant.now().minus(3, ChronoUnit.DAYS), "agent-scanner",
                Map.of("technology", "linux"));
        String objectKey = queries.versions(UUID.fromString(r.evidenceId())).get(0).objectKey();
        doReturn(Optional.of("tampered-content".getBytes(StandardCharsets.UTF_8)))
                .when(objectStore).get(objectKey);

        EvidenceCompletenessItem item = itemFor(r.evidenceId(), "net-banking");

        assertThat(item.integrityStatus()).isEqualTo(IntegrityStatus.TAMPERED);
        assertThat(item.factors()).anyMatch(f -> f.factor().equals("Integrity") && f.status().equals("fail"));
        assertThat(item.factors()).anyMatch(f -> f.factor().equals("Evidence content") && f.status().equals("fail"));
        assertThat(item.band()).isEqualTo("INCOMPLETE");
        assertThat(item.completenessPct()).isLessThan(60);
    }

    @Test
    void unmappedControlLosesTheControlMappingFactorOnly() {
        IngestResult r = ingest("net-banking", "NOT-A-REAL-CONTROL", "C-SITE", "AGENT_OS_LINUX",
                SUBSTANTIVE_CONTENT, Instant.now().minus(3, ChronoUnit.DAYS), "agent-scanner",
                Map.of("technology", "linux"));

        EvidenceCompletenessItem item = itemFor(r.evidenceId(), "net-banking");

        assertThat(item.factors()).anyMatch(f -> f.factor().equals("Control mapping") && f.status().equals("fail"));
        assertThat(item.band()).isEqualTo("PARTIAL");
    }

    @Test
    void summaryCountsMatchTheRepositoryListingOneToOne() {
        ingest("net-banking", "OS-SSH-ROOT-LOGIN", "C-SITE", "AGENT_OS_LINUX",
                SUBSTANTIVE_CONTENT, Instant.now().minus(3, ChronoUnit.DAYS), "agent-scanner",
                Map.of("technology", "linux"));
        ingest("net-banking", "OS-AUDIT-LOGGING", "PCI_DSS", "AGENT_OS_LINUX",
                SUBSTANTIVE_CONTENT, Instant.now().minus(3, ChronoUnit.DAYS), "agent-scanner",
                Map.of("technology", "linux"));

        EvidenceCompletenessReport report = scoring.forScope("net-banking", null);
        long repositoryCount = queries.recordsMatching(
                new EvidenceQueryService.EvidenceFilter("net-banking", null, null, null, null, null, null, 0, 100_000))
                .size();

        assertThat(report.totalItems()).isEqualTo((int) repositoryCount);
        assertThat(report.items()).hasSize((int) repositoryCount);
        assertThat(report.completeCount() + report.partialCount() + report.incompleteCount())
                .isEqualTo(report.totalItems());
    }

    // ---- framework/control rollup ----------------------------------------

    @Test
    void frameworkRollupCountsEvaluatedControlsAndAveragesOnlyThoseEvaluated() {
        // C-SITE's catalog has 23 controls (phase2/control-catalog.json: 7 hand-curated +
        // 16 imported from docs/ECS_Control_Library.xlsx); we only give evidence to 2.
        ingest("fw-rollup-app", "OS-SSH-ROOT-LOGIN", "C-SITE", "AGENT_OS_LINUX",
                SUBSTANTIVE_CONTENT, Instant.now().minus(3, ChronoUnit.DAYS), "agent-scanner",
                Map.of("technology", "linux"));
        ingest("fw-rollup-app", "TLS-CERT-EXPIRY", "C-SITE", "MOCK_JIRA",
                SUBSTANTIVE_CONTENT, Instant.now().minus(3, ChronoUnit.DAYS), null, Map.of());

        List<FrameworkCompletenessRow> rows = scoring.frameworkRollup("fw-rollup-app");
        FrameworkCompletenessRow cSite = rows.stream().filter(r -> r.framework().equals("C-SITE")).findFirst()
                .orElseThrow(() -> new AssertionError("no C-SITE row"));

        assertThat(cSite.totalControls()).isEqualTo(23);
        assertThat(cSite.controlsEvaluated()).isEqualTo(2);
        assertThat(cSite.controlsNotEvaluated()).isEqualTo(21);

        double controlA = itemFor(
                scoring.forScope("fw-rollup-app", "C-SITE").items().stream()
                        .filter(i -> i.controlId().equals("OS-SSH-ROOT-LOGIN")).findFirst().orElseThrow()
                        .evidenceId(),
                "fw-rollup-app").completenessPct();
        double controlB = itemFor(
                scoring.forScope("fw-rollup-app", "C-SITE").items().stream()
                        .filter(i -> i.controlId().equals("TLS-CERT-EXPIRY")).findFirst().orElseThrow()
                        .evidenceId(),
                "fw-rollup-app").completenessPct();
        double expectedAvg = Math.round((controlA + controlB) / 2 * 10.0) / 10.0;

        assertThat(cSite.avgCompletenessPct()).isEqualTo(expectedAvg);
    }

    @Test
    void frameworkWithNoEvidenceAtAllHasNullAverageAndZeroEvaluated() {
        List<FrameworkCompletenessRow> rows = scoring.frameworkRollup("fw-rollup-empty-app");
        FrameworkCompletenessRow cSite = rows.stream().filter(r -> r.framework().equals("C-SITE")).findFirst()
                .orElseThrow(() -> new AssertionError("no C-SITE row"));

        assertThat(cSite.controlsEvaluated()).isZero();
        assertThat(cSite.controlsNotEvaluated()).isEqualTo(cSite.totalControls());
        assertThat(cSite.avgCompletenessPct()).isNull();
    }

    @Test
    void controlRollupAveragesAllEvidenceItemsMappedToThatControl() {
        // Two separate evidence records (different source systems) for the same control:
        // one fully complete, one partial (missing collectedBy + technology).
        ingest("ctrl-rollup-app", "OS-SSH-ROOT-LOGIN", "C-SITE", "AGENT_OS_LINUX",
                SUBSTANTIVE_CONTENT, Instant.now().minus(3, ChronoUnit.DAYS), "agent-scanner",
                Map.of("technology", "linux"));
        ingest("ctrl-rollup-app", "OS-SSH-ROOT-LOGIN", "C-SITE", "MOCK_JIRA",
                SUBSTANTIVE_CONTENT, Instant.now().minus(3, ChronoUnit.DAYS), null, Map.of());

        EvidenceCompletenessReport report = scoring.forScope("ctrl-rollup-app", "C-SITE");
        List<Integer> pcts = report.items().stream()
                .filter(i -> i.controlId().equals("OS-SSH-ROOT-LOGIN"))
                .map(EvidenceCompletenessItem::completenessPct)
                .toList();
        assertThat(pcts).hasSize(2);
        double expectedAvg = Math.round(pcts.stream().mapToInt(Integer::intValue).average().orElseThrow() * 10.0) / 10.0;

        List<ControlCompletenessRow> controls = scoring.controlRollup("ctrl-rollup-app", "C-SITE");
        ControlCompletenessRow row = controls.stream().filter(c -> c.controlId().equals("OS-SSH-ROOT-LOGIN"))
                .findFirst().orElseThrow(() -> new AssertionError("no OS-SSH-ROOT-LOGIN row"));

        assertThat(row.evaluated()).isTrue();
        assertThat(row.evidenceCount()).isEqualTo(2);
        assertThat(row.completenessPct()).isEqualTo(expectedAvg);
    }

    @Test
    void controlWithNoEvidenceIsNotEvaluatedAndShowsNoPercentage() {
        // Give evidence to one C-SITE control only; the rest of the catalog's C-SITE
        // controls must come back not-evaluated with a null (not 0%) completeness.
        ingest("ctrl-rollup-empty-app", "OS-SSH-ROOT-LOGIN", "C-SITE", "AGENT_OS_LINUX",
                SUBSTANTIVE_CONTENT, Instant.now().minus(3, ChronoUnit.DAYS), "agent-scanner",
                Map.of("technology", "linux"));

        List<ControlCompletenessRow> controls = scoring.controlRollup("ctrl-rollup-empty-app", "C-SITE");
        ControlCompletenessRow untouched = controls.stream().filter(c -> c.controlId().equals("TLS-CERT-EXPIRY"))
                .findFirst().orElseThrow(() -> new AssertionError("no TLS-CERT-EXPIRY row"));

        assertThat(untouched.evaluated()).isFalse();
        assertThat(untouched.completenessPct()).isNull();
        assertThat(untouched.evidenceCount()).isZero();
    }

    @Test
    void evidenceForControlDrillDownReturnsOnlyThatControlsItems() {
        ingest("drilldown-app", "OS-SSH-ROOT-LOGIN", "C-SITE", "AGENT_OS_LINUX",
                SUBSTANTIVE_CONTENT, Instant.now().minus(3, ChronoUnit.DAYS), "agent-scanner",
                Map.of("technology", "linux"));
        ingest("drilldown-app", "TLS-CERT-EXPIRY", "C-SITE", "AGENT_OS_LINUX",
                SUBSTANTIVE_CONTENT, Instant.now().minus(3, ChronoUnit.DAYS), "agent-scanner",
                Map.of("technology", "linux"));

        List<EvidenceCompletenessItem> items =
                scoring.evidenceForControl("drilldown-app", "C-SITE", "OS-SSH-ROOT-LOGIN");

        assertThat(items).hasSize(1);
        assertThat(items.get(0).controlId()).isEqualTo("OS-SSH-ROOT-LOGIN");
    }
}
