package com.pramaan.backend.insight;

import com.pramaan.backend.evidence.ControlFrameworkCatalog;
import com.pramaan.backend.insight.InsightDtos.AuditPrepReport;
import com.pramaan.backend.insight.InsightDtos.AuditScheduleReport;
import com.pramaan.backend.insight.InsightDtos.ComparisonReport;
import com.pramaan.backend.insight.InsightDtos.ControlReuseResult;
import com.pramaan.backend.insight.InsightDtos.ComplianceReport;
import com.pramaan.backend.insight.InsightDtos.CompletenessReport;
import com.pramaan.backend.insight.InsightDtos.ControlCompletenessRow;
import com.pramaan.backend.insight.InsightDtos.EnterpriseDashboard;
import com.pramaan.backend.insight.InsightDtos.EvidenceCompletenessItem;
import com.pramaan.backend.insight.InsightDtos.EvidenceCompletenessReport;
import com.pramaan.backend.insight.InsightDtos.EvidenceContext;
import com.pramaan.backend.insight.InsightDtos.EvidenceLifecycleSummary;
import com.pramaan.backend.insight.InsightDtos.FrameworkCompletenessRow;
import com.pramaan.backend.insight.InsightDtos.EvidenceSummary;
import com.pramaan.backend.insight.InsightDtos.LeadershipDashboard;
import com.pramaan.backend.insight.InsightDtos.NationalDashboard;
import com.pramaan.backend.insight.InsightDtos.NationalRollup;
import com.pramaan.backend.insight.InsightDtos.NlQueryRequest;
import com.pramaan.backend.insight.InsightDtos.NlQueryResult;
import com.pramaan.backend.insight.InsightDtos.ReuseResult;
import com.pramaan.backend.insight.InsightDtos.TrendPoint;
import com.pramaan.backend.insight.InsightDtos.TrendReport;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Phase 2 insight endpoints: completeness, reuse/similarity, AI summaries, NL query, compliance. */
@RestController
@RequestMapping("/api/v1/insight")
public class InsightController {

    private final CompletenessService completeness;
    private final EvidenceCompletenessService evidenceCompleteness;
    private final EvidenceReuseService reuse;
    private final EvidenceSummaryService summaries;
    private final NlQueryService nlQuery;
    private final ComplianceService compliance;
    private final LeadershipService leadership;
    private final ComparisonService comparison;
    private final EnterpriseDashboardService enterprise;
    private final AuditPrepService auditPrep;
    private final TrendService trend;
    private final EvidenceEmbeddingIndexer indexer;
    private final EvidenceContextService evidenceContext;
    private final EvidenceLifecycleSummaryService lifecycleSummary;
    private final AuditScheduleService auditSchedule;

    public InsightController(CompletenessService completeness, EvidenceCompletenessService evidenceCompleteness,
                             EvidenceReuseService reuse,
                             EvidenceSummaryService summaries, NlQueryService nlQuery,
                             ComplianceService compliance, LeadershipService leadership,
                             ComparisonService comparison, EnterpriseDashboardService enterprise,
                             AuditPrepService auditPrep, TrendService trend,
                             EvidenceEmbeddingIndexer indexer, EvidenceContextService evidenceContext,
                             EvidenceLifecycleSummaryService lifecycleSummary, AuditScheduleService auditSchedule) {
        this.completeness = completeness;
        this.evidenceCompleteness = evidenceCompleteness;
        this.reuse = reuse;
        this.summaries = summaries;
        this.nlQuery = nlQuery;
        this.compliance = compliance;
        this.leadership = leadership;
        this.comparison = comparison;
        this.enterprise = enterprise;
        this.auditPrep = auditPrep;
        this.trend = trend;
        this.indexer = indexer;
        this.evidenceContext = evidenceContext;
        this.lifecycleSummary = lifecycleSummary;
        this.auditSchedule = auditSchedule;
    }

    @GetMapping("/completeness")
    public CompletenessReport completeness(@RequestParam String applicationSlug,
                                           @RequestParam(required = false) String framework) {
        return completeness.forApplication(applicationSlug, framework);
    }

    /** Per-evidence-item completeness (audit-readiness at the item level) — see {@link EvidenceCompletenessService}. */
    @GetMapping("/evidence-completeness")
    public EvidenceCompletenessReport evidenceCompleteness(@RequestParam(required = false) String applicationSlug,
                                                            @RequestParam(required = false) String framework) {
        return evidenceCompleteness.forScope(applicationSlug, framework);
    }

    /** Evidence Completeness — Framework -> Control rollup: one row per framework. */
    @GetMapping("/evidence-completeness/frameworks")
    public List<FrameworkCompletenessRow> evidenceCompletenessFrameworks(
            @RequestParam(required = false) String applicationSlug) {
        return evidenceCompleteness.frameworkRollup(applicationSlug);
    }

    /** One row per control in the given framework. */
    @GetMapping("/evidence-completeness/frameworks/{framework}/controls")
    public List<ControlCompletenessRow> evidenceCompletenessControls(
            @PathVariable String framework,
            @RequestParam(required = false) String applicationSlug) {
        return evidenceCompleteness.controlRollup(applicationSlug, framework);
    }

    /** Per-evidence-item completeness records backing one control's score (drill-down). */
    @GetMapping("/evidence-completeness/frameworks/{framework}/controls/{controlId}/evidence")
    public List<EvidenceCompletenessItem> evidenceCompletenessControlEvidence(
            @PathVariable String framework, @PathVariable String controlId,
            @RequestParam(required = false) String applicationSlug) {
        return evidenceCompleteness.evidenceForControl(applicationSlug, framework, controlId);
    }

    @GetMapping("/compliance")
    public ComplianceReport compliance(@RequestParam String applicationSlug,
                                       @RequestParam(required = false) String framework) {
        return compliance.forApplication(applicationSlug, framework);
    }

    /** Leadership compliance dashboard — portfolio rollup of actual results across all applications. */
    @GetMapping("/leadership")
    public LeadershipDashboard leadership(@RequestParam(required = false) List<String> businessUnit) {
        return leadership.dashboard(businessUnit);
    }

    /** UC14 — cross-application compliance comparison. */
    @GetMapping("/comparison")
    public ComparisonReport comparison(@RequestParam(required = false) List<String> applications,
                                       @RequestParam(required = false) List<String> businessUnit,
                                       @RequestParam(required = false) String framework) {
        return comparison.compare(applications, businessUnit, framework);
    }

    /**
     * UC16 — enterprise compliance dashboard.
     *
     * @deprecated superseded by {@code GET /national/rollup}, which carries the same portfolio,
     *             business-unit / criticality cuts and top risks (this is a thin view over it).
     */
    @Deprecated
    @GetMapping("/enterprise")
    public EnterpriseDashboard enterprise(@RequestParam(required = false) List<String> businessUnit,
                                          HttpServletResponse response) {
        markDeprecated(response);
        return enterprise.enterprise(businessUnit);
    }

    /**
     * UC20 — national / pan-India compliance dashboard (flat per-region table).
     *
     * @deprecated superseded by {@code GET /national/rollup}, which has the same regions plus the
     *             region x framework breakdown, gap-to-average ranking and enterprise cuts.
     */
    @Deprecated
    @GetMapping("/national")
    public NationalDashboard national(HttpServletResponse response) {
        markDeprecated(response);
        return enterprise.national();
    }

    /**
     * The merged national + enterprise dashboard (source of truth): region x framework breakdown,
     * regions ranked by gap to the national average with RAG, business-unit / criticality cuts and
     * top risks. Optional repeatable {@code businessUnit} scopes every part.
     */
    @GetMapping("/national/rollup")
    public NationalRollup nationalRollup(@RequestParam(required = false) List<String> businessUnit) {
        return enterprise.nationalRollup(businessUnit);
    }

    private static void markDeprecated(HttpServletResponse response) {
        response.setHeader("Deprecation", "true");
        response.setHeader("Link", "</api/v1/insight/national/rollup>; rel=\"successor-version\"");
    }

    /** UC18 — AI-assisted audit preparation checklist. */
    @GetMapping("/audit-prep")
    public AuditPrepReport auditPrep(@RequestParam(required = false) String applicationSlug,
                                     @RequestParam(required = false) String framework) {
        return auditPrep.prepare(applicationSlug, framework);
    }

    /**
     * UC19 — compliance trend & closure. {@code applicationSlug} scopes just the closure metrics
     * (approvals/rejections/avg review time/rejection trend) to one application — see App Owner
     * dashboard Overview; mutually exclusive with {@code businessUnit}.
     */
    @GetMapping("/trend")
    public TrendReport trend(@RequestParam(required = false) List<String> businessUnit,
                             @RequestParam(required = false) String applicationSlug) {
        if (applicationSlug != null && !applicationSlug.isBlank()) {
            return trend.trendForApplication(applicationSlug);
        }
        return trend.trend(businessUnit);
    }

    @PostMapping("/trend/snapshot")
    public TrendPoint trendSnapshot() {
        return trend.snapshot();
    }

    /** App Owner dashboard — evidence lifecycle counts, rejection audit trail, Auditor SLA, pending aging. */
    @GetMapping("/evidence-lifecycle/summary")
    public EvidenceLifecycleSummary evidenceLifecycleSummary(@RequestParam String applicationSlug) {
        return lifecycleSummary.forApplication(applicationSlug);
    }

    /** Upcoming audit schedule — each scheduled audit plus a computed readyCount/totalCount. */
    @GetMapping("/audit-schedule")
    public AuditScheduleReport auditSchedule() {
        return auditSchedule.upcoming();
    }

    @GetMapping("/evidence/{id}/summary")
    public EvidenceSummary summary(@PathVariable UUID id) {
        return summaries.summarize(id);
    }

    @GetMapping("/evidence/{id}/context")
    public EvidenceContext context(@PathVariable UUID id) {
        return evidenceContext.forEvidence(id);
    }

    @GetMapping("/reuse/{id}")
    public ReuseResult reuseByEvidence(@PathVariable UUID id,
                                       @RequestParam(defaultValue = "5") int limit,
                                       @RequestParam(defaultValue = "0.3") double minScore) {
        return reuse.similarTo(id, limit, minScore);
    }

    /** UC — "Evidence similarity and reuse": the control→frameworks catalogue for the control picker. */
    @GetMapping("/reuse/controls")
    public List<ControlFrameworkCatalog.ControlFrameworks> reuseControls() {
        return reuse.controlCatalogue();
    }

    /** UC — for one control: frameworks it satisfies + evidence already held (cross-framework reuse). */
    @GetMapping("/reuse/by-control")
    public ControlReuseResult reuseByControl(@RequestParam String controlId) {
        return reuse.reuseByControl(controlId);
    }

    @PostMapping("/reuse/search")
    public ReuseResult reuseBySearch(@RequestBody Map<String, Object> body) {
        String text = body.get("text") == null ? null : String.valueOf(body.get("text"));
        int limit = body.get("limit") instanceof Number n ? n.intValue() : 5;
        double minScore = body.get("minScore") instanceof Number n ? n.doubleValue() : 0.3;
        return reuse.similarToText(text, limit, minScore);
    }

    @PostMapping("/nl-query")
    public NlQueryResult nlQuery(@RequestBody NlQueryRequest req) {
        return nlQuery.answer(req.question(), req.applicationSlug());
    }

    @PostMapping("/embeddings/reindex")
    public Map<String, Object> reindex() {
        int n = indexer.reindex();
        return Map.of("indexed", n);
    }
}
