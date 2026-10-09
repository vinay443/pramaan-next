package com.pramaan.backend.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pramaan.backend.application.ApplicationService;
import com.pramaan.backend.common.ApiException;
import com.pramaan.backend.evidence.EvidenceDtos.IngestRequest;
import com.pramaan.backend.evidence.EvidenceDtos.IngestResult;
import com.pramaan.backend.evidence.EvidenceIngestionService;
import com.pramaan.backend.evidence.EvidenceQueryService;
import com.pramaan.backend.insight.CompletenessService;
import com.pramaan.backend.insight.ControlCatalog;
import com.pramaan.backend.insight.EvidenceSummaryService;
import com.pramaan.backend.insight.InsightDtos.Coverage;
import com.pramaan.backend.insight.InsightDtos.EvidenceSummary;
import com.pramaan.backend.insight.summary.SummaryEnums.Verdict;
import com.pramaan.backend.reporting.EvidenceSummaryReport.ControlRow;
import com.pramaan.backend.reporting.EvidenceSummaryReport.Report;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class EvidenceSummaryReportTest {

    @Autowired EvidenceIngestionService ingestion;
    @Autowired EvidenceSummaryReport report;
    @Autowired EvidenceSummaryService summaries;
    @Autowired ControlCatalog catalog;
    @Autowired CompletenessService completeness;
    @Autowired EvidenceQueryService evidenceQueries;
    @Autowired ApplicationService applications;
    @Autowired Clock clock;

    private static final String PASS = "{\"collector\":\"db\",\"status\":\"pass\",\"checks\":[{\"checkId\":\"DB-1\",\"status\":\"PASS\"}]}";
    private static final String FAIL = "{\"collector\":\"os\",\"findings\":[{\"checkId\":\"OS-1\",\"status\":\"FAIL\","
            + "\"observed\":\"yes\",\"expected\":\"no\",\"detail\":\"bad\"}]}";
    private static final String WARN = "{\"note\":\"nothing a rule recognises\"}";

    private IngestResult ingest(String app, String framework, String control, String objectId, String body) {
        return ingestion.ingest(new IngestRequest(app, control, framework, "AGENT_TEST", objectId, null,
                "application/json", null, body, Instant.now().minus(2, ChronoUnit.DAYS), "agent", Map.of(), Map.of()));
    }

    private ControlRow control(Report r, String app, String controlId) {
        return r.controls().stream().filter(c -> c.application().equals(app) && c.controlId().equals(controlId))
                .findFirst().orElseThrow();
    }

    @Test
    void totalsAddUpAndControlsWithoutEvidenceAreListedAsMissing() {
        ingest("payments", "PCI_DSS", "PCI-C6", "a", PASS);
        ingest("payments", "PCI_DSS", "PCI-C1", "b", FAIL);

        Report r = report.build("payments", "PCI_DSS");

        int expected = catalog.forApplication("payments", "PCI_DSS").size();
        assertThat(r.scope()).isEqualTo(new EvidenceSummaryReport.Scope("payments", "PCI_DSS"));
        assertThat(r.totals().controlsExpected()).isEqualTo(expected).isEqualTo(r.controls().size());
        assertThat(r.totals().controlsWithEvidence()).isEqualTo(2);
        assertThat(r.totals().controlsMissing()).isEqualTo(expected - 2);
        assertThat(r.totals().evidenceRecords()).isEqualTo(2);
        assertThat(r.totals().byVerdict()).containsExactly(
                Map.entry("PASS", 1), Map.entry("WARNING", 0), Map.entry("FAIL", 1),
                Map.entry("NOT_APPLICABLE", 0), Map.entry("NOT_ASSESSED", 0));
        assertThat(r.totals().byVerdict().values().stream().mapToInt(Integer::intValue).sum())
                .isEqualTo(r.totals().evidenceRecords());
        assertThat(r.totals().stale()).isZero();
        assertThat(r.totals().integrityMismatch()).isZero();
        assertThat(r.truncated()).isFalse();

        ControlRow missing = control(r, "payments", "PCI-C2");
        assertThat(missing.status()).isEqualTo(Coverage.MISSING);
        assertThat(missing.verdict()).isNull();
        assertThat(missing.evidence()).isEmpty();
        assertThat(missing.evidenceCount()).isZero();
        assertThat(missing.controlName()).isEqualTo("Secure System Configuration");
        assertThat(missing.description()).startsWith("Apply secure configurations");
    }

    @Test
    void aControlTakesTheWorstVerdictOfItsEvidenceAndEvidenceIsOrderedById() {
        ingest("payments", "PCI_DSS", "PCI-C3", "p", PASS);
        ingest("payments", "PCI_DSS", "PCI-C3", "f", FAIL);
        ingest("payments", "PCI_DSS", "PCI-C4", "p", PASS);
        ingest("payments", "PCI_DSS", "PCI-C4", "w", WARN);
        ingest("payments", "PCI_DSS", "PCI-C5", "p", PASS);

        Report r = report.build("payments", "PCI_DSS");

        ControlRow c3 = control(r, "payments", "PCI-C3");
        assertThat(c3.verdict()).isEqualTo(Verdict.FAIL);
        assertThat(c3.evidenceCount()).isEqualTo(2);
        assertThat(c3.evidence()).extracting(e -> e.evidenceId()).isSorted();
        assertThat(control(r, "payments", "PCI-C4").verdict()).isEqualTo(Verdict.WARNING);
        assertThat(control(r, "payments", "PCI-C5").verdict()).isEqualTo(Verdict.PASS);
        assertThat(c3.status()).isEqualTo(Coverage.COVERED);
        assertThat(c3.latestCollectedAt()).isNotNull();
    }

    @Test
    void filtersNarrowByApplicationAndFrameworkAndOrderingIsDeterministic() {
        ingest("payments", "PCI_DSS", "PCI-C6", "a", PASS);
        ingest("net-banking", "DPSC", "DPSC-C14", "a", PASS);

        Report all = report.build(null, null);
        assertThat(all.scope()).isEqualTo(new EvidenceSummaryReport.Scope(null, null));
        assertThat(all.controls()).extracting(ControlRow::application).contains("payments", "net-banking");
        List<ControlRow> sorted = new ArrayList<>(all.controls());
        sorted.sort(Comparator.comparing(ControlRow::application).thenComparing(ControlRow::framework)
                .thenComparing(ControlRow::controlId));
        assertThat(all.controls()).isEqualTo(sorted);
        assertThat(report.build(null, null)).usingRecursiveComparison().ignoringFields("generatedAt").isEqualTo(all);

        Report onlyPayments = report.build("payments", null);
        assertThat(onlyPayments.controls()).extracting(ControlRow::application).containsOnly("payments");
        assertThat(onlyPayments.totals().controlsExpected()).isEqualTo(catalog.forApplication("payments", null).size());

        Report dpscAll = report.build(null, "DPSC");
        assertThat(dpscAll.controls()).extracting(ControlRow::framework).containsOnly("DPSC");
        assertThat(dpscAll.totals().evidenceRecords()).isEqualTo(1);
        assertThat(control(dpscAll, "net-banking", "DPSC-C14").verdict()).isEqualTo(Verdict.PASS);
    }

    @Test
    void anUnknownApplicationIsA404() {
        assertThatThrownBy(() -> report.build("no-such-app", null))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.status().value()).isEqualTo(404));
    }

    @Test
    void reportRowsAgreeWithThePerEvidenceSummary() {
        IngestResult a = ingest("payments", "PCI_DSS", "PCI-C6", "a", PASS);
        IngestResult b = ingest("payments", "PCI_DSS", "PCI-C1", "b", FAIL);
        Report r = report.build("payments", "PCI_DSS");

        for (IngestResult ing : List.of(a, b)) {
            EvidenceSummary s = summaries.summarize(UUID.fromString(ing.evidenceId()));
            var row = r.controls().stream().flatMap(c -> c.evidence().stream())
                    .filter(e -> e.evidenceId().equals(ing.evidenceId())).findFirst().orElseThrow();
            assertThat(row.verdict()).isEqualTo(s.verdict());
            assertThat(row.headline()).isEqualTo(s.headline());
            assertThat(row.version()).isEqualTo(s.version());
            assertThat(row.integrity()).isEqualTo(s.health().integrity());
            assertThat(row.freshness()).isEqualTo(s.health().freshness());
        }
    }

    @Test
    void theReportIsCappedAndSaysSo() {
        ingest("payments", "PCI_DSS", "PCI-C3", "a", PASS);
        ingest("payments", "PCI_DSS", "PCI-C4", "b", PASS);
        ingest("payments", "PCI_DSS", "PCI-C5", "c", PASS);
        EvidenceSummaryReport capped = new EvidenceSummaryReport(completeness, evidenceQueries, summaries, catalog,
                applications, clock, 2);

        Report r = capped.build("payments", "PCI_DSS");

        assertThat(r.truncated()).isTrue();
        assertThat(r.totals().evidenceRecords()).isEqualTo(2);
        assertThat(r.limits()).singleElement().asString().contains("truncated").contains("first 2 evidence records");
        assertThat(control(r, "payments", "PCI-C5").evidence()).isEmpty();
        assertThat(control(r, "payments", "PCI-C5").evidenceCount()).isEqualTo(1);
        assertThat(control(r, "payments", "PCI-C5").verdict()).isNull();
    }

    // ---- CSV -------------------------------------------------------------------------------------

    @Test
    void csvHasOneRowPerEvidenceAndOnePerMissingControlAndRoundTripsThroughAParser() {
        // a plain-text file whose headline contains commas and quotes
        IngestResult text = ingestion.ingest(new IngestRequest("payments", "PCI-C6", "PCI_DSS", "UPLOAD", "t", null,
                "text/plain", null, "Policy, \"draft\" v2\nsecond line", Instant.now(), "agent", Map.of(), Map.of()));
        ingest("payments", "PCI_DSS", "PCI-C1", "b", FAIL);

        Report r = report.build("payments", "PCI_DSS");
        List<List<String>> rows = parseCsv(report.csv(r));

        assertThat(rows.get(0)).containsExactly("application", "framework", "controlId", "controlName", "status",
                "verdict", "evidenceId", "version", "collectedAt", "integrity", "freshness", "headline");
        int evidenceRows = (int) rows.stream().skip(1).filter(x -> !x.get(6).isEmpty()).count();
        int missingRows = (int) rows.stream().skip(1).filter(x -> x.get(6).isEmpty()).count();
        assertThat(evidenceRows).isEqualTo(r.totals().evidenceRecords());
        assertThat(missingRows).isEqualTo(r.totals().controlsMissing());
        assertThat(rows.stream().skip(1).allMatch(x -> x.size() == 12)).isTrue();

        List<String> textRow = rows.stream().filter(x -> x.get(6).equals(text.evidenceId())).findFirst().orElseThrow();
        assertThat(textRow.get(11)).isEqualTo(summaries.summarize(UUID.fromString(text.evidenceId())).headline())
                .contains(",").contains("\"draft\"");
        List<String> missing = rows.stream().filter(x -> x.get(2).equals("PCI-C2")).findFirst().orElseThrow();
        assertThat(missing.subList(4, 12)).containsExactly("MISSING", "", "", "", "", "", "", "");
    }

    @Test
    void cellsAreQuotedAndFormulaInjectionIsNeutralised() {
        assertThat(EvidenceSummaryReport.cell("plain")).isEqualTo("plain");
        assertThat(EvidenceSummaryReport.cell(null)).isEmpty();
        assertThat(EvidenceSummaryReport.cell("a,b")).isEqualTo("\"a,b\"");
        assertThat(EvidenceSummaryReport.cell("say \"hi\"")).isEqualTo("\"say \"\"hi\"\"\"");
        assertThat(EvidenceSummaryReport.cell("line1\nline2")).isEqualTo("\"line1\nline2\"");
        assertThat(EvidenceSummaryReport.cell("line1\r\nline2")).isEqualTo("\"line1\r\nline2\"");
        for (String evil : new String[]{"=1+1", "+cmd", "-2+3", "@SUM(A1)", "\tTAB", "\rCR"}) {
            assertThat(EvidenceSummaryReport.cell(evil)).as(evil).startsWith(evil.startsWith("\r") ? "\"'" : "'");
        }
        assertThat(EvidenceSummaryReport.cell("=HYPERLINK(\"http://x\",\"y\")"))
                .isEqualTo("\"'=HYPERLINK(\"\"http://x\"\",\"\"y\"\")\"");
        assertThat(EvidenceSummaryReport.cell("2026-09-30")).isEqualTo("2026-09-30");
    }

    /** Minimal RFC 4180 parser (quoted fields, doubled quotes, embedded newlines). */
    static List<List<String>> parseCsv(String csv) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < csv.length(); i++) {
            char c = csv.charAt(i);
            if (quoted) {
                if (c == '"' && i + 1 < csv.length() && csv.charAt(i + 1) == '"') {
                    cur.append('"');
                    i++;
                } else if (c == '"') {
                    quoted = false;
                } else {
                    cur.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == ',') {
                row.add(cur.toString());
                cur.setLength(0);
            } else if (c == '\n') {
                row.add(cur.toString());
                cur.setLength(0);
                rows.add(row);
                row = new ArrayList<>();
            } else {
                cur.append(c);
            }
        }
        return rows;
    }
}
