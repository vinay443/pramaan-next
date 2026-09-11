package com.pramaan.backend.insight;

import com.pramaan.backend.evidence.ControlFrameworkCatalog;
import com.pramaan.backend.insight.InsightDtos.AuditPrepReport;
import com.pramaan.backend.insight.InsightDtos.ComparisonReport;
import com.pramaan.backend.insight.InsightDtos.ControlReuseResult;
import com.pramaan.backend.insight.InsightDtos.ComplianceReport;
import com.pramaan.backend.insight.InsightDtos.CompletenessReport;
import com.pramaan.backend.insight.InsightDtos.EnterpriseDashboard;
import com.pramaan.backend.insight.InsightDtos.EvidenceContext;
import com.pramaan.backend.insight.InsightDtos.EvidenceSummary;
import com.pramaan.backend.insight.InsightDtos.LeadershipDashboard;
import com.pramaan.backend.insight.InsightDtos.NationalDashboard;
import com.pramaan.backend.insight.InsightDtos.NationalRollup;
import com.pramaan.backend.insight.InsightDtos.NlQueryRequest;
import com.pramaan.backend.insight.InsightDtos.NlQueryResult;
import com.pramaan.backend.insight.InsightDtos.ReuseResult;
import com.pramaan.backend.insight.InsightDtos.TrendPoint;
import com.pramaan.backend.insight.InsightDtos.TrendReport;
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

    public InsightController(CompletenessService completeness, EvidenceReuseService reuse,
                             EvidenceSummaryService summaries, NlQueryService nlQuery,
                             ComplianceService compliance, LeadershipService leadership,
                             ComparisonService comparison, EnterpriseDashboardService enterprise,
                             AuditPrepService auditPrep, TrendService trend,
                             EvidenceEmbeddingIndexer indexer, EvidenceContextService evidenceContext) {
        this.completeness = completeness;
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
    }

    @GetMapping("/completeness")
    public CompletenessReport completeness(@RequestParam String applicationSlug,
                                           @RequestParam(required = false) String framework) {
        return completeness.forApplication(applicationSlug, framework);
    }

    @GetMapping("/compliance")
    public ComplianceReport compliance(@RequestParam String applicationSlug,
                                       @RequestParam(required = false) String framework) {
        return compliance.forApplication(applicationSlug, framework);
    }

    /** Leadership compliance dashboard — portfolio rollup of actual results across all applications. */
    @GetMapping("/leadership")
    public LeadershipDashboard leadership() {
        return leadership.dashboard();
    }

    /** UC14 — cross-application compliance comparison. */
    @GetMapping("/comparison")
    public ComparisonReport comparison(@RequestParam(required = false) List<String> applications,
                                       @RequestParam(required = false) String framework) {
        return comparison.compare(applications, framework);
    }

    /** UC16 — enterprise compliance dashboard (portfolio + business-unit / criticality cuts). */
    @GetMapping("/enterprise")
    public EnterpriseDashboard enterprise() {
        return enterprise.enterprise();
    }

    /** UC20 — national / pan-India compliance dashboard. */
    @GetMapping("/national")
    public NationalDashboard national() {
        return enterprise.national();
    }

    /** National rollup — region x framework breakdown + regions ranked by gap to the national average. */
    @GetMapping("/national/rollup")
    public NationalRollup nationalRollup() {
        return enterprise.nationalRollup();
    }

    /** UC18 — AI-assisted audit preparation checklist. */
    @GetMapping("/audit-prep")
    public AuditPrepReport auditPrep(@RequestParam(required = false) String applicationSlug,
                                     @RequestParam(required = false) String framework) {
        return auditPrep.prepare(applicationSlug, framework);
    }

    /** UC19 — compliance trend & closure. */
    @GetMapping("/trend")
    public TrendReport trend() {
        return trend.trend();
    }

    @PostMapping("/trend/snapshot")
    public TrendPoint trendSnapshot() {
        return trend.snapshot();
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
