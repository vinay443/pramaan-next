package com.pramaan.backend.reporting;

import com.pramaan.backend.application.ApplicationDtos.ApplicationView;
import com.pramaan.backend.application.ApplicationService;
import com.pramaan.backend.evidence.EvidenceQueryService;
import com.pramaan.backend.evidence.EvidenceQueryService.EvidenceFilter;
import com.pramaan.backend.evidence.domain.EvidenceRecord;
import com.pramaan.backend.insight.CompletenessService;
import com.pramaan.backend.insight.ControlCatalog;
import com.pramaan.backend.insight.EvidenceSummaryService;
import com.pramaan.backend.insight.InsightDtos.CompletenessReport;
import com.pramaan.backend.insight.InsightDtos.ControlCoverage;
import com.pramaan.backend.insight.InsightDtos.EvidenceSummary;
import com.pramaan.backend.insight.summary.SummaryEnums.Freshness;
import com.pramaan.backend.insight.summary.SummaryEnums.Integrity;
import com.pramaan.backend.insight.summary.SummaryEnums.Verdict;
import com.pramaan.backend.insight.InsightDtos.Coverage;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The "evidence-summary" report — audit documentation. A view, not a dataset of its own: the expected
 * controls and their coverage come from {@link CompletenessService} (so controls with no evidence show
 * up as MISSING), and every evidence entry comes from {@link EvidenceSummaryService}, the same code path
 * as {@code GET /insight/evidence/{id}/summary}, so the two can never disagree.
 */
@Component
@Transactional(readOnly = true)
public class EvidenceSummaryReport {

    /** Most evidence summaries one report renders; beyond this the report is flagged truncated. */
    public static final int DEFAULT_MAX_EVIDENCE = 2000;

    public record Scope(String applicationSlug, String framework) {}

    public record Totals(int controlsExpected, int controlsWithEvidence, int controlsMissing, int evidenceRecords,
                         Map<String, Integer> byVerdict, int stale, int integrityMismatch) {}

    public record EvidenceRow(String evidenceId, int version, String headline, Verdict verdict, Instant collectedAt,
                              Integrity integrity, Freshness freshness) {}

    public record ControlRow(String application, String framework, String controlId, String controlName,
                             String description, Coverage status, Verdict verdict, int evidenceCount,
                             Instant latestCollectedAt, List<EvidenceRow> evidence) {}

    public record Report(Instant generatedAt, Scope scope, Totals totals, boolean truncated, List<String> limits,
                         List<ControlRow> controls) {}

    private static final List<Verdict> WORST_FIRST = List.of(
            Verdict.FAIL, Verdict.WARNING, Verdict.PASS, Verdict.NOT_APPLICABLE, Verdict.NOT_ASSESSED);

    private final CompletenessService completeness;
    private final EvidenceQueryService evidence;
    private final EvidenceSummaryService summaries;
    private final ControlCatalog catalog;
    private final ApplicationService applications;
    private final Clock clock;
    private final int maxEvidence;

    @Autowired
    public EvidenceSummaryReport(CompletenessService completeness, EvidenceQueryService evidence,
                                 EvidenceSummaryService summaries, ControlCatalog catalog,
                                 ApplicationService applications, Clock clock,
                                 @Value("${pramaan.reports.evidence-summary.max-evidence:" + DEFAULT_MAX_EVIDENCE + "}")
                                 int maxEvidence) {
        this.completeness = completeness;
        this.evidence = evidence;
        this.summaries = summaries;
        this.catalog = catalog;
        this.applications = applications;
        this.clock = clock;
        this.maxEvidence = maxEvidence;
    }

    public Report build(String applicationSlug, String framework) {
        String app = blankToNull(applicationSlug);
        String fw = blankToNull(framework);
        List<String> slugs = app != null ? List.of(app)
                : applications.list().stream().map(ApplicationView::slug).sorted().toList();

        List<ControlRow> controls = new ArrayList<>();
        List<String> limits = new ArrayList<>();
        int summarised = 0;
        boolean truncated = false;

        for (String slug : slugs) {
            CompletenessReport cr = completeness.forApplication(slug, fw); // 404 for an unknown application
            Map<String, List<EvidenceRecord>> held = evidence.recordsMatching(
                            new EvidenceFilter(slug, fw, null, null, null, null, null, 0, 100_000)).stream()
                    .collect(Collectors.groupingBy(r -> key(r.getFramework(), r.getControlId())));

            for (ControlCoverage c : cr.controls()) {
                List<EvidenceRecord> records = held.getOrDefault(key(c.framework(), c.controlId()), List.of()).stream()
                        .sorted(Comparator.comparing(r -> r.getId().toString())).toList();
                List<EvidenceRow> rows = new ArrayList<>();
                for (EvidenceRecord r : records) {
                    if (summarised >= maxEvidence) {
                        truncated = true;
                        break;
                    }
                    rows.add(row(summaries.summarize(r.getId())));
                    summarised++;
                }
                Instant latest = records.stream().map(EvidenceRecord::getLatestCollectedAt)
                        .filter(java.util.Objects::nonNull).max(Comparator.naturalOrder()).orElse(null);
                Verdict verdict = c.coverage() == Coverage.MISSING || rows.isEmpty() ? null : worst(rows);
                ControlCatalog.Expectation e = catalog.find(c.framework(), c.controlId()).orElse(null);
                controls.add(new ControlRow(slug, c.framework(), c.controlId(), c.title(),
                        e == null ? null : e.description(), c.coverage(), verdict, records.size(), latest,
                        List.copyOf(rows)));
            }
        }
        if (truncated) {
            limits.add("Report truncated: only the first " + maxEvidence
                    + " evidence records were summarised; later controls list no evidence entries.");
        }
        return new Report(clock.instant(), new Scope(app, fw), totals(controls), truncated, List.copyOf(limits),
                List.copyOf(controls));
    }

    private static EvidenceRow row(EvidenceSummary s) {
        return new EvidenceRow(s.evidenceId(), s.version(), s.headline(), s.verdict(), s.health().collectedAt(),
                s.health().integrity(), s.health().freshness());
    }

    /** Worst verdict over a control's evidence: FAIL > WARNING > PASS > NOT_APPLICABLE > NOT_ASSESSED. */
    static Verdict worst(List<EvidenceRow> rows) {
        for (Verdict v : WORST_FIRST) {
            if (rows.stream().anyMatch(r -> r.verdict() == v)) {
                return v;
            }
        }
        return null;
    }

    private static Totals totals(List<ControlRow> controls) {
        Map<String, Integer> byVerdict = new LinkedHashMap<>();
        for (Verdict v : new Verdict[]{Verdict.PASS, Verdict.WARNING, Verdict.FAIL, Verdict.NOT_APPLICABLE,
                Verdict.NOT_ASSESSED}) {
            byVerdict.put(v.name(), 0);
        }
        int missing = 0, records = 0, stale = 0, mismatch = 0;
        for (ControlRow c : controls) {
            if (c.status() == Coverage.MISSING) {
                missing++;
            }
            for (EvidenceRow e : c.evidence()) {
                records++;
                byVerdict.merge(e.verdict().name(), 1, Integer::sum);
                if (e.freshness() == Freshness.STALE) stale++;
                if (e.integrity() == Integrity.MISMATCH) mismatch++;
            }
        }
        return new Totals(controls.size(), controls.size() - missing, missing, records, byVerdict, stale, mismatch);
    }

    // ---- CSV ---------------------------------------------------------------------------------

    public String csv(Report report) {
        StringBuilder sb = new StringBuilder("application,framework,controlId,controlName,status,verdict,"
                + "evidenceId,version,collectedAt,integrity,freshness,headline\n");
        for (ControlRow c : report.controls()) {
            String verdict = c.verdict() == null ? "" : c.verdict().name();
            if (c.evidence().isEmpty()) {
                sb.append(line(c.application(), c.framework(), c.controlId(), c.controlName(), c.status().name(),
                        verdict, "", "", "", "", "", ""));
            }
            for (EvidenceRow e : c.evidence()) {
                sb.append(line(c.application(), c.framework(), c.controlId(), c.controlName(), c.status().name(),
                        verdict, e.evidenceId(), String.valueOf(e.version()),
                        e.collectedAt() == null ? "" : e.collectedAt().toString(), e.integrity().name(),
                        e.freshness().name(), e.headline()));
            }
        }
        return sb.toString();
    }

    private static String line(String... cells) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(cell(cells[i]));
        }
        return sb.append('\n').toString();
    }

    /**
     * One CSV cell: RFC 4180 quoting (commas, quotes, CR/LF) and spreadsheet formula-injection guard —
     * a cell starting with = + - @ (or a tab/CR) gets a leading single quote so it is read as text.
     */
    static String cell(String raw) {
        String c = raw == null ? "" : raw;
        if (!c.isEmpty() && "=+-@\t\r".indexOf(c.charAt(0)) >= 0) {
            c = "'" + c;
        }
        if (c.contains(",") || c.contains("\"") || c.contains("\n") || c.contains("\r")) {
            c = '"' + c.replace("\"", "\"\"") + '"';
        }
        return c;
    }

    private static String key(String framework, String controlId) {
        return (framework == null ? "" : framework.toUpperCase(Locale.ROOT)) + "|"
                + (controlId == null ? "" : controlId.toUpperCase(Locale.ROOT));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
