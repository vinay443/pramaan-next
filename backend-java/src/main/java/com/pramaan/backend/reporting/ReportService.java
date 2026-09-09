package com.pramaan.backend.reporting;

import com.pramaan.backend.common.ApiException;
import com.pramaan.backend.evidence.EvidenceDtos.EvidenceView;
import com.pramaan.backend.evidence.EvidenceQueryService;
import com.pramaan.backend.evidence.EvidenceQueryService.EvidenceFilter;
import com.pramaan.backend.insight.AuditPrepService;
import com.pramaan.backend.insight.ComparisonService;
import com.pramaan.backend.insight.EnterpriseDashboardService;
import com.pramaan.backend.insight.InsightDtos.AppPosture;
import com.pramaan.backend.insight.InsightDtos.GapRow;
import com.pramaan.backend.insight.InsightDtos.PrepFinding;
import com.pramaan.backend.insight.InsightDtos.RegionPosture;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * UC17 — automated regulatory reporting. A report is a <b>view</b> over data the
 * insight / evidence services already produce, rendered as JSON or CSV. No report
 * builds its own dataset.
 */
@Service
@Transactional(readOnly = true)
public class ReportService {

    public record ReportInfo(String name, String title, List<String> formats, List<String> params) {}

    private static final List<ReportInfo> CATALOG = List.of(
            new ReportInfo("compliance-summary", "Portfolio & enterprise compliance summary",
                    List.of("json", "csv"), List.of()),
            new ReportInfo("evidence-register", "Evidence register (all records)",
                    List.of("json", "csv"), List.of("applicationSlug", "framework")),
            new ReportInfo("gap-report", "Cross-application control gap report",
                    List.of("json", "csv"), List.of("framework")),
            new ReportInfo("audit-readiness", "AI-assisted audit-readiness checklist",
                    List.of("json", "csv"), List.of("applicationSlug", "framework")),
            new ReportInfo("pan-india", "National / pan-India compliance report",
                    List.of("json", "csv"), List.of()));

    private final EvidenceQueryService evidence;
    private final ComparisonService comparison;
    private final EnterpriseDashboardService enterprise;
    private final AuditPrepService auditPrep;
    private final Clock clock;

    public ReportService(EvidenceQueryService evidence, ComparisonService comparison,
                         EnterpriseDashboardService enterprise, AuditPrepService auditPrep, Clock clock) {
        this.evidence = evidence;
        this.comparison = comparison;
        this.enterprise = enterprise;
        this.auditPrep = auditPrep;
        this.clock = clock;
    }

    public List<ReportInfo> catalog() {
        return CATALOG;
    }

    /** The structured (JSON) form of a report. */
    public Object json(String name, String applicationSlug, String framework) {
        return switch (require(name)) {
            case "compliance-summary" -> enterprise.enterprise();
            case "evidence-register" -> register(applicationSlug, framework);
            case "gap-report" -> comparison.compare(null, framework);
            case "audit-readiness" -> auditPrep.prepare(applicationSlug, framework);
            case "pan-india" -> enterprise.national();
            default -> throw ApiException.notFound("unknown report: " + name);
        };
    }

    /** The tabular (CSV) form. First line is the header. */
    public String csv(String name, String applicationSlug, String framework) {
        return switch (require(name)) {
            case "evidence-register" -> csvEvidence(register(applicationSlug, framework));
            case "gap-report" -> csvGaps(comparison.compare(null, framework).gaps());
            case "audit-readiness" -> csvFindings(auditPrep.prepare(applicationSlug, framework).findings());
            case "compliance-summary" -> csvSummary();
            case "pan-india" -> csvRegions(enterprise.national().regions());
            default -> throw ApiException.notFound("unknown report: " + name);
        };
    }

    private List<EvidenceView> register(String applicationSlug, String framework) {
        return evidence.search(new EvidenceFilter(applicationSlug, framework, null, null, null, null, null,
                0, 100_000)).items();
    }

    private String csvEvidence(List<EvidenceView> rows) {
        StringBuilder sb = new StringBuilder(
                "evidenceId,applicationSlug,framework,controlId,sourceSystem,currentVersion,lifecycleState,sha256,collectedAt\n");
        for (EvidenceView e : rows) {
            sb.append(csv(e.evidenceId(), e.applicationSlug(), e.framework(), e.controlId(),
                    e.sourceSystem(), String.valueOf(e.currentVersion()), e.lifecycleState(),
                    e.latest() == null ? "" : e.latest().sha256(),
                    e.latest() == null ? "" : String.valueOf(e.latest().collectedAt())));
        }
        return sb.toString();
    }

    private String csvGaps(List<GapRow> gaps) {
        StringBuilder sb = new StringBuilder("applicationSlug,framework,controlId,status\n");
        gaps.forEach(g -> sb.append(csv(g.applicationSlug(), g.framework(), g.controlId(), g.status())));
        return sb.toString();
    }

    private String csvFindings(List<PrepFinding> findings) {
        StringBuilder sb = new StringBuilder("severity,category,applicationSlug,framework,controlId,detail,action\n");
        findings.forEach(f -> sb.append(csv(f.severity(), f.category(), f.applicationSlug(), f.framework(),
                f.controlId(), f.detail(), f.action())));
        return sb.toString();
    }

    private String csvSummary() {
        var e = enterprise.enterprise();
        StringBuilder sb = new StringBuilder("applicationSlug,name,criticality,compliancePct,completenessPct,nonCompliant,missingEvidence\n");
        for (AppPosture p : e.portfolio().byApplication()) {
            sb.append(csv(p.applicationSlug(), p.name(), p.criticality(),
                    String.valueOf(p.compliancePct()), String.valueOf(p.completenessPct()),
                    String.valueOf(p.nonCompliant()), String.valueOf(p.missingEvidence())));
        }
        return sb.toString();
    }

    private String csvRegions(List<RegionPosture> regions) {
        StringBuilder sb = new StringBuilder("region,applications,expected,compliant,compliancePct,completenessPct,rag\n");
        regions.forEach(r -> sb.append(csv(r.region(), String.join(" ", r.applications()),
                String.valueOf(r.expected()), String.valueOf(r.compliant()),
                String.valueOf(r.compliancePct()), String.valueOf(r.completenessPct()), r.rag())));
        return sb.toString();
    }

    private String require(String name) {
        if (CATALOG.stream().noneMatch(r -> r.name().equalsIgnoreCase(name))) {
            throw ApiException.notFound("unknown report: " + name
                    + " (available: " + CATALOG.stream().map(ReportInfo::name).toList() + ")");
        }
        return name.toLowerCase();
    }

    private static String csv(String... cells) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            String c = cells[i] == null ? "" : cells[i];
            if (c.contains(",") || c.contains("\"") || c.contains("\n")) {
                c = '"' + c.replace("\"", "\"\"") + '"';
            }
            sb.append(c);
        }
        return sb.append('\n').toString();
    }

    /** Suggested download filename for a report. */
    public String filename(String name, String format) {
        return "%s-%s.%s".formatted(name.toLowerCase(),
                clock.instant().toString().substring(0, 10), format.toLowerCase());
    }

    // kept for symmetry / future use
    Map<String, Object> meta(String name) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("report", name);
        m.put("generatedAt", clock.instant().toString());
        return m;
    }
}
