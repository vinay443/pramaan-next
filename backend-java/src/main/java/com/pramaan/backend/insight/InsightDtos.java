package com.pramaan.backend.insight;

import com.pramaan.backend.evidence.EvidenceDtos.IntegrityStatus;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Response shapes for the Phase 2 insight endpoints. */
public final class InsightDtos {

    private InsightDtos() {}

    // ---- completeness -----------------------------------------------------

    public enum Coverage { COVERED, STALE, MISSING }

    public record ControlCoverage(
            String framework,
            String controlId,
            String title,
            Coverage coverage,
            String evidenceId,
            Integer currentVersion,
            Instant lastCollectedAt,
            Integer ageDays) {}

    public record CompletenessReport(
            String applicationSlug,
            String framework,
            Instant generatedAt,
            int staleAfterDays,
            int expected,
            int covered,
            int stale,
            int missing,
            double completenessPct,
            List<ControlCoverage> controls) {}

    // ---- per-evidence-item completeness (audit-readiness at the item level) ----

    /** One scored factor contributing to an item's completeness percentage. */
    public record EvidenceCompletenessFactor(String factor, String status, String detail) {}

    public record EvidenceCompletenessItem(
            String evidenceId,
            String applicationSlug,
            String framework,
            String controlId,
            String sourceSystem,
            String collectedBy,
            String technology,
            int currentVersion,
            Instant lastCollectedAt,
            Integer ageDays,
            String sha256,
            IntegrityStatus integrityStatus,
            int completenessPct,
            String band,
            List<EvidenceCompletenessFactor> factors) {}

    public record EvidenceCompletenessReport(
            String applicationSlug,
            String framework,
            Instant generatedAt,
            int staleAfterDays,
            int totalItems,
            double avgCompletenessPct,
            int completeCount,
            int partialCount,
            int incompleteCount,
            List<EvidenceCompletenessItem> items) {}

    // ---- framework/control rollup (Evidence Completeness page, aggregated view) ----

    /** One framework's rollup: how many of its {@link ControlCatalog} controls have >=1 mapped
     *  evidence item, and the mean completeness across those evaluated controls only. */
    public record FrameworkCompletenessRow(
            String framework,
            int totalControls,
            int controlsEvaluated,
            int controlsNotEvaluated,
            /** Null when no control in this framework has any evidence yet. */
            Double avgCompletenessPct) {}

    /** One control's rollup within a framework — {@code completenessPct} is the mean of its
     *  mapped evidence items' scores, null (not 0%) when the control has no evidence. */
    public record ControlCompletenessRow(
            String controlId,
            String title,
            boolean evaluated,
            Double completenessPct,
            int evidenceCount) {}

    // ---- reuse / similarity --------------------------------------------

    public record SimilarEvidence(
            String evidenceId,
            String applicationSlug,
            String framework,
            String controlId,
            String sha256,
            double score,
            boolean crossApplication,
            boolean sameControl,
            boolean exactDuplicate,
            String reuseHint) {}

    public record ReuseResult(
            String queryEvidenceId,
            String queryText,
            String embeddingModel,
            String vectorStore,
            int indexed,
            String querySha256,
            List<SimilarEvidence> exactDuplicates,
            List<SimilarEvidence> matches) {}

    // ---- reuse by control (cross-framework) ---------------------------

    /** One evidence record held for a control, with the frameworks it is already tagged to. */
    public record ControlReuseEvidence(
            String evidenceId,
            String applicationSlug,
            String controlId,
            String sourceSystem,
            String collectionMethod,
            Instant collectedAt,
            String sha256,
            List<String> mappedFrameworks) {}

    /** For a control: every framework it satisfies (UC03) + the evidence already held for it. */
    public record ControlReuseResult(
            String controlId,
            List<String> frameworks,
            List<ControlReuseEvidence> evidence) {}

    // ---- AI summary ---------------------------------------------------

    public record EvidenceSummary(
            String evidenceId,
            String applicationSlug,
            String framework,
            String controlId,
            String model,
            boolean simulated,
            /** False when the text is a deterministic prompt digest rather than model output. */
            boolean modelGenerated,
            String summary,
            List<String> groundedOn,
            Instant generatedAt) {}

    // ---- evidence context (Use Case 1 — Evidence Detail enrichment) ----

    public record EvidenceContext(
            String evidenceId,
            String applicationSlug,
            String controlId,
            String technology,
            String collectionMethod,
            String sourceSystem,
            Instant collectedAt,
            int currentVersion,
            List<String> satisfiesControls,
            List<String> frameworks) {}

    // ---- natural-language query -------------------------------------

    public record NlQueryRequest(String question, String applicationSlug) {}

    public record NlQueryResult(
            String question,
            String interpretedAs,
            String matchedQuery,
            Map<String, Object> answer,
            String narrative,
            String model,
            boolean simulated,
            /** False when the narrative is a deterministic prompt digest, not model output. */
            boolean modelGenerated,
            /** False when the question matched no supported intent — see {@code supportedQuestionTypes}. */
            boolean supported,
            /** The question types this deterministic router can actually answer. */
            List<String> supportedQuestionTypes,
            Instant generatedAt) {}

    // ---- compliance aggregation ------------------------------------

    public enum ControlStatus { COMPLIANT, PARTIALLY_COMPLIANT, NON_COMPLIANT, NOT_ASSESSED, MISSING_EVIDENCE }

    public record ControlPosture(
            String framework,
            String controlId,
            ControlStatus status,
            String detail) {}

    public record FrameworkPosture(
            String framework,
            int expected,
            int compliant,
            int partiallyCompliant,
            int nonCompliant,
            int notAssessed,
            int missingEvidence,
            double compliancePct) {}

    public record ComplianceReport(
            String applicationSlug,
            Instant generatedAt,
            int expected,
            int compliant,
            double compliancePct,
            List<FrameworkPosture> byFramework,
            List<ControlPosture> controls) {}

    // ---- leadership compliance dashboard (portfolio rollup) --------------

    public record AppPosture(
            String applicationSlug,
            String name,
            String criticality,
            int expected,
            int compliant,
            double compliancePct,
            int covered,
            int missing,
            double completenessPct,
            int nonCompliant,
            int missingEvidence) {}

    public record LeadershipDashboard(
            Instant generatedAt,
            int applications,
            int expected,
            int compliant,
            double compliancePct,
            int covered,
            int stale,
            int missing,
            double completenessPct,
            Map<String, Integer> checkVerdicts,
            List<AppPosture> byApplication,
            List<FrameworkPosture> byFramework) {}

    // ---- UC14 cross-application compliance comparison -------------------

    public record FrameworkComparisonRow(String framework, Map<String, Double> compliancePctByApp,
                                         double minPct, double maxPct, double spreadPct) {}

    public record ControlComparisonRow(String framework, String controlId,
                                       Map<String, String> statusByApp, boolean consistent) {}

    public record GapRow(String applicationSlug, String framework, String controlId, String status) {}

    public record ComparisonReport(Instant generatedAt, String framework, List<String> applications,
                                   List<FrameworkComparisonRow> frameworks,
                                   List<ControlComparisonRow> controls, List<GapRow> gaps) {}

    // ---- UC16 enterprise compliance dashboard --------------------------

    public record GroupPosture(String key, int applications, int expected, int compliant,
                               double compliancePct, double completenessPct) {}

    public record EnterpriseDashboard(Instant generatedAt, LeadershipDashboard portfolio,
                                      List<GroupPosture> byBusinessUnit,
                                      List<GroupPosture> byCriticality,
                                      List<AppPosture> topRisks) {}

    // ---- UC20 national compliance dashboard ----------------------------

    public record RegionPosture(String region, List<String> applications, int expected, int compliant,
                                double compliancePct, double completenessPct, String rag) {}

    public record NationalDashboard(Instant generatedAt, double nationalCompliancePct,
                                    double nationalCompletenessPct, int applications,
                                    List<RegionPosture> regions) {}

    // ---- national rollup (region x framework breakdown + lagging-region ranking) ----

    public record RegionFrameworkRow(String region, String framework, int expected, int compliant,
                                     double compliancePct) {}

    /** {@code gapVsNationalPct} is negative when the region trails the national average. */
    public record RegionGap(String region, double compliancePct, double gapVsNationalPct, String rag) {}

    public record NationalRollup(Instant generatedAt, double nationalCompliancePct,
                                 double nationalCompletenessPct, int applications,
                                 List<RegionFrameworkRow> byRegionFramework,
                                 List<RegionGap> laggingRegions,
                                 // merged in from the former /enterprise dashboard:
                                 LeadershipDashboard portfolio, List<RegionPosture> regions,
                                 List<GroupPosture> byBusinessUnit, List<GroupPosture> byCriticality,
                                 List<AppPosture> topRisks) {}

    // ---- UC18 AI-assisted audit preparation ----------------------------

    public record PrepFinding(String category, String severity, String applicationSlug,
                              String framework, String controlId, String detail, String action) {}

    public record AuditPrepReport(Instant generatedAt, String scope, double readinessScore,
                                  int totalFindings, Map<String, Integer> bySeverity,
                                  List<PrepFinding> findings, String narrative, String model,
                                  boolean simulated) {}

    // ---- UC19 compliance trend & closure ------------------------------

    public record TrendPoint(Instant takenAt, int expected, int compliant, double compliancePct,
                             double completenessPct, int approvedEvidence, int openFindings,
                             int evidenceCount, int integrityChecked, int integrityIntact) {

        /** % of checked current versions that hashed intact (100 when nothing checked). */
        public double integrityPct() {
            return integrityChecked == 0 ? 100.0
                    : Math.round(integrityIntact * 1000.0 / integrityChecked) / 10.0;
        }
    }

    public record ClosureStats(int approvals, int rejections, int resubmissions,
                               Double avgDaysToApprove, Map<String, Integer> approvalsByWeek,
                               /** Month-over-month % change in rejection count; null with &lt;2 months of data. */
                               Double rejectionTrendPct) {}

    public record CollectionPoint(Instant at, int ingested, int duplicates, int failed) {}

    public record TrendReport(Instant generatedAt, TrendPoint current, List<TrendPoint> points,
                              ClosureStats closure, List<CollectionPoint> collection) {}

    // ---- App Owner dashboard — evidence lifecycle summary (counts/rejections/SLA/aging) ----

    public record LifecycleStateCounts(int draft, int submitted, int approved, int rejected,
                                       int expired, int superseded) {}

    public record RejectionAuditRow(String evidenceId, String applicationSlug, String framework,
                                    String controlId, String reason, String rejectedBy,
                                    Instant rejectedAt, String workflowState) {}

    /** Computed metric — no direct backend source; see {@link PramaanProperties.Evidence#auditorSlaDaysOrDefault()}. */
    public record AuditorSla(int reviewedWithinTarget, int totalReviewed, Double pct, int targetDays) {}

    /** Computed metric — DRAFT/SUBMITTED evidence aging in the review queue. */
    public record PendingAging(int count, Double avgDaysInQueue) {}

    public record EvidenceLifecycleSummary(String applicationSlug, Instant generatedAt,
                                           LifecycleStateCounts counts, List<RejectionAuditRow> rejections,
                                           AuditorSla auditorSla, PendingAging pendingAging) {}
}
