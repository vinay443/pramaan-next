package com.pramaan.backend.reporting;

import com.pramaan.backend.common.ApiException;
import com.pramaan.backend.evidence.EvidenceDtos.EvidenceView;
import com.pramaan.backend.evidence.EvidenceQueryService;
import com.pramaan.backend.evidence.EvidenceQueryService.EvidenceFilter;
import com.pramaan.backend.insight.AuditPrepService;
import com.pramaan.backend.insight.ComparisonService;
import com.pramaan.backend.insight.ComplianceService;
import com.pramaan.backend.insight.EnterpriseDashboardService;
import com.pramaan.backend.insight.InsightDtos.AppPosture;
import com.pramaan.backend.insight.InsightDtos.ComplianceReport;
import com.pramaan.backend.insight.InsightDtos.ControlStatus;
import com.pramaan.backend.insight.InsightDtos.FrameworkPosture;
import com.pramaan.backend.insight.InsightDtos.GapRow;
import com.pramaan.backend.insight.InsightDtos.LeadershipDashboard;
import com.pramaan.backend.insight.InsightDtos.PrepFinding;
import com.pramaan.backend.insight.InsightDtos.RegionPosture;
import com.pramaan.backend.util.Hashing;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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

    /**
     * UC17 — regulator-ready filing. Distinct from the other reports: instead of
     * exposing an internal DTO as-is, it renders a fixed cover-page + per-framework
     * schema (report id, regulator, reporting period, attestation) suitable for
     * submission, over the same underlying compliance data.
     */
    public record RegulatoryFiling(
            String reportId,
            String title,
            String regulator,
            String scope,
            String framework,
            Instant periodStart,
            Instant periodEnd,
            Instant generatedAt,
            String preparedBy,
            int applicationsInScope,
            int controlsExpected,
            int controlsCompliant,
            double compliancePct,
            int evidenceRecords,
            int openGaps,
            List<FrameworkFiling> frameworks,
            String attestation) {}

    public record FrameworkFiling(String framework, String regulator, int expected, int compliant,
                                  int nonCompliant, int missingEvidence, double compliancePct) {}

    private static final Map<String, String> REGULATOR_BY_FRAMEWORK = Map.of(
            "PCI_DSS", "PCI Security Standards Council",
            "ITPP", "Internal IT Policy & Procedures Board",
            "DPSC", "Data Protection Supervisory Council");
    private static final String DEFAULT_REGULATOR = "Compliance Authority";
    /** Regulator-ready filings cover a fixed trailing window, not the whole evidence history. */
    private static final int FILING_PERIOD_DAYS = 90;

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
                    List.of("json", "csv"), List.of()),
            new ReportInfo("regulatory-filing", "Regulator-ready compliance filing",
                    List.of("json", "csv"), List.of("applicationSlug", "framework")));

    private final EvidenceQueryService evidence;
    private final ComparisonService comparison;
    private final ComplianceService compliance;
    private final EnterpriseDashboardService enterprise;
    private final AuditPrepService auditPrep;
    private final Clock clock;

    public ReportService(EvidenceQueryService evidence, ComparisonService comparison,
                         ComplianceService compliance, EnterpriseDashboardService enterprise,
                         AuditPrepService auditPrep, Clock clock) {
        this.evidence = evidence;
        this.comparison = comparison;
        this.compliance = compliance;
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
            case "regulatory-filing" -> regulatoryFiling(applicationSlug, framework);
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
            case "regulatory-filing" -> csvRegulatoryFiling(regulatoryFiling(applicationSlug, framework));
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

    private RegulatoryFiling regulatoryFiling(String applicationSlug, String framework) {
        Instant now = clock.instant();
        Instant periodStart = now.minus(FILING_PERIOD_DAYS, ChronoUnit.DAYS);
        boolean scoped = applicationSlug != null && !applicationSlug.isBlank();

        List<FrameworkPosture> byFramework;
        int applicationsInScope;
        int expected;
        int compliant;
        int evidenceRecords;
        int openGaps;

        if (scoped) {
            ComplianceReport cr = compliance.forApplication(applicationSlug, framework);
            byFramework = cr.byFramework();
            applicationsInScope = 1;
            expected = cr.expected();
            compliant = cr.compliant();
            evidenceRecords = register(applicationSlug, framework).size();
            openGaps = (int) cr.controls().stream().filter(c -> c.status() != ControlStatus.COMPLIANT).count();
        } else {
            LeadershipDashboard portfolio = enterprise.enterprise().portfolio();
            byFramework = framework == null || framework.isBlank() ? portfolio.byFramework()
                    : portfolio.byFramework().stream()
                            .filter(f -> f.framework().equalsIgnoreCase(framework)).toList();
            applicationsInScope = portfolio.applications();
            expected = byFramework.stream().mapToInt(FrameworkPosture::expected).sum();
            compliant = byFramework.stream().mapToInt(FrameworkPosture::compliant).sum();
            evidenceRecords = (int) evidence.dashboard().records();
            openGaps = byFramework.stream().mapToInt(f -> f.nonCompliant() + f.missingEvidence()).sum();
        }

        double compliancePct = expected == 0 ? 0.0 : Math.round(1000.0 * compliant / expected) / 10.0;
        List<FrameworkFiling> filings = byFramework.stream()
                .map(f -> new FrameworkFiling(f.framework(), regulatorFor(f.framework()), f.expected(),
                        f.compliant(), f.nonCompliant(), f.missingEvidence(), f.compliancePct()))
                .toList();

        String scope = scoped ? applicationSlug : "PORTFOLIO";
        String scopedFramework = framework == null || framework.isBlank() ? "ALL" : framework.toUpperCase();
        String reportId = "REG-" + now.toString().substring(0, 10).replace("-", "") + "-"
                + Hashing.sha256Hex((scope + "|" + scopedFramework + "|" + now)
                        .getBytes(StandardCharsets.UTF_8)).substring(0, 8).toUpperCase();
        String regulator = filings.size() == 1 ? filings.get(0).regulator() : "Multiple Regulatory Bodies";
        String attestation = "This filing reflects deterministic evidence and control-verdict data held by "
                + "Pramaan Next as of " + now + ". Figures are computed, not model-generated.";

        return new RegulatoryFiling(reportId, "Regulatory Compliance Filing", regulator, scope, scopedFramework,
                periodStart, now, now, "Pramaan Next (automated)", applicationsInScope, expected, compliant,
                compliancePct, evidenceRecords, openGaps, filings, attestation);
    }

    private static String regulatorFor(String framework) {
        return REGULATOR_BY_FRAMEWORK.getOrDefault(framework == null ? "" : framework.toUpperCase(),
                DEFAULT_REGULATOR);
    }

    private String csvRegulatoryFiling(RegulatoryFiling r) {
        StringBuilder sb = new StringBuilder(
                "reportId,title,regulator,scope,framework,periodStart,periodEnd,generatedAt,"
                        + "applicationsInScope,controlsExpected,controlsCompliant,compliancePct,"
                        + "evidenceRecords,openGaps\n");
        sb.append(csv(r.reportId(), r.title(), r.regulator(), r.scope(), r.framework(),
                r.periodStart().toString(), r.periodEnd().toString(), r.generatedAt().toString(),
                String.valueOf(r.applicationsInScope()), String.valueOf(r.controlsExpected()),
                String.valueOf(r.controlsCompliant()), String.valueOf(r.compliancePct()),
                String.valueOf(r.evidenceRecords()), String.valueOf(r.openGaps())));
        sb.append('\n').append("framework,regulator,expected,compliant,nonCompliant,missingEvidence,compliancePct\n");
        r.frameworks().forEach(f -> sb.append(csv(f.framework(), f.regulator(), String.valueOf(f.expected()),
                String.valueOf(f.compliant()), String.valueOf(f.nonCompliant()),
                String.valueOf(f.missingEvidence()), String.valueOf(f.compliancePct()))));
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
