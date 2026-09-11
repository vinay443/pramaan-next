package com.pramaan.backend.appowner;

import com.pramaan.backend.appowner.AppOwnerContext.AppOwnerIdentity;
import com.pramaan.backend.appowner.AppOwnerDtos.ActionRequiredItem;
import com.pramaan.backend.appowner.AppOwnerDtos.ActionType;
import com.pramaan.backend.appowner.AppOwnerDtos.ActivityItem;
import com.pramaan.backend.appowner.AppOwnerDtos.AppOwnerDashboard;
import com.pramaan.backend.appowner.AppOwnerDtos.CompliancePendingRow;
import com.pramaan.backend.appowner.AppOwnerDtos.FrameworkKpi;
import com.pramaan.backend.appowner.AppOwnerDtos.NotificationView;
import com.pramaan.backend.appowner.AppOwnerDtos.SlaStatus;
import com.pramaan.backend.appowner.AppOwnerDtos.TargetDateHistoryView;
import com.pramaan.backend.appowner.AppOwnerDtos.UploadEvidenceRequest;
import com.pramaan.backend.appowner.domain.AppOwnerAuditLogEntry;
import com.pramaan.backend.appowner.domain.AppOwnerNotification;
import com.pramaan.backend.appowner.domain.TargetDateHistoryEntry;
import com.pramaan.backend.appowner.repo.AppOwnerAuditLogRepository;
import com.pramaan.backend.appowner.repo.AppOwnerNotificationRepository;
import com.pramaan.backend.appowner.repo.TargetDateHistoryRepository;
import com.pramaan.backend.common.ApiException;
import com.pramaan.backend.evidence.EvidenceDtos.EvidenceView;
import com.pramaan.backend.evidence.EvidenceDtos.IngestRequest;
import com.pramaan.backend.evidence.EvidenceDtos.LifecycleAction;
import com.pramaan.backend.evidence.EvidenceDtos.LifecycleTransitionRequest;
import com.pramaan.backend.evidence.EvidenceIngestionService;
import com.pramaan.backend.evidence.EvidenceLifecycleService;
import com.pramaan.backend.evidence.EvidenceQueryService;
import com.pramaan.backend.evidence.EvidenceQueryService.EvidenceFilter;
import com.pramaan.backend.evidence.domain.EvidenceLifecycleState;
import com.pramaan.backend.evidence.domain.EvidenceRecord;
import com.pramaan.backend.evidence.repo.EvidenceRecordRepository;
import com.pramaan.backend.insight.ControlCatalog;
import com.pramaan.backend.insight.ControlCatalog.Expectation;
import com.pramaan.backend.insight.InsightDtos.ControlStatus;
import com.pramaan.backend.reporting.ReportService;
import com.pramaan.backend.rules.CheckStatus;
import com.pramaan.backend.rules.domain.CheckResult;
import com.pramaan.backend.rules.repo.CheckResultRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application Owner (APP) persona — dashboard, compliance-pending list, evidence
 * upload/resubmit, Target Date sharing, notifications (view-only) and scoped
 * reports. Every method takes the caller's {@link AppOwnerIdentity} (resolved by
 * {@link AppOwnerContext} from request headers) and enforces application-ownership
 * scope itself — it never trusts a slug/id the caller passed without checking it
 * against {@code identity.ownedApplicationSlugs}.
 *
 * <p>No Approve/Reject/Admin action exists anywhere in this class — those remain
 * exclusively in {@code EvidenceLifecycleService} (gated by
 * {@code EvidenceApprovalAuthorizer}, which already refuses APP_OWNER) and
 * {@code AdminController} (blocked from this role entirely by
 * {@link AppOwnerAccessFilter}).
 */
@Service
public class AppOwnerService {

    private static final int DUE_SOON_DAYS = 7;
    /** Simple TD policy: must be in the future, and not absurdly far out. No config file for this yet. */
    private static final int TD_MIN_DAYS_AHEAD = 1;
    private static final int TD_MAX_DAYS_AHEAD = 365;
    private static final java.util.Set<EvidenceLifecycleState> PENDING_STATES =
            java.util.Set.of(EvidenceLifecycleState.DRAFT, EvidenceLifecycleState.SUBMITTED,
                    EvidenceLifecycleState.REJECTED);

    private final EvidenceQueryService evidenceQueries;
    private final EvidenceIngestionService ingestion;
    private final EvidenceLifecycleService lifecycle;
    private final EvidenceRecordRepository records;
    private final ControlCatalog catalog;
    private final CheckResultRepository checkResults;
    private final ReportService reportService;
    private final TargetDateHistoryRepository tdHistory;
    private final AppOwnerNotificationRepository notifications;
    private final AppOwnerAuditLogRepository auditLog;
    private final Clock clock;

    public AppOwnerService(EvidenceQueryService evidenceQueries, EvidenceIngestionService ingestion,
                           EvidenceLifecycleService lifecycle, EvidenceRecordRepository records,
                           ControlCatalog catalog, CheckResultRepository checkResults,
                           ReportService reportService, TargetDateHistoryRepository tdHistory,
                           AppOwnerNotificationRepository notifications, AppOwnerAuditLogRepository auditLog,
                           Clock clock) {
        this.evidenceQueries = evidenceQueries;
        this.ingestion = ingestion;
        this.lifecycle = lifecycle;
        this.records = records;
        this.catalog = catalog;
        this.checkResults = checkResults;
        this.reportService = reportService;
        this.tdHistory = tdHistory;
        this.notifications = notifications;
        this.auditLog = auditLog;
        this.clock = clock;
    }

    // ---- dashboard ----------------------------------------------------------

    @Transactional(readOnly = true)
    public AppOwnerDashboard dashboard(AppOwnerIdentity identity) {
        Instant now = clock.instant();
        LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);
        List<CompliancePendingRow> all = allControlRows(identity, null, today);
        List<CompliancePendingRow> pending = all.stream()
                .filter(r -> !ControlStatus.COMPLIANT.name().equals(r.complianceStatus()))
                .toList();

        int evidencePending = 0, evidenceRejected = 0, tdPending = 0, dueSoon = 0, overdue = 0;
        for (CompliancePendingRow r : pending) {
            if ("SUBMITTED".equals(r.evidenceStatus()) || "DRAFT".equals(r.evidenceStatus())) evidencePending++;
            if ("REJECTED".equals(r.evidenceStatus())) evidenceRejected++;
            if (r.slaStatus() == SlaStatus.NONE && r.evidenceStatus() != null) tdPending++;
            if (r.slaStatus() == SlaStatus.DUE_SOON) dueSoon++;
            if (r.slaStatus() == SlaStatus.OVERDUE) overdue++;
        }

        Map<String, List<CompliancePendingRow>> byFramework = all.stream()
                .collect(Collectors.groupingBy(CompliancePendingRow::framework, TreeMap::new, Collectors.toList()));
        List<FrameworkKpi> frameworkKpis = new ArrayList<>();
        byFramework.forEach((fw, rows) -> {
            int totalApplicable = rows.size();
            int compliantCount = (int) rows.stream()
                    .filter(r -> ControlStatus.COMPLIANT.name().equals(r.complianceStatus())).count();
            int pendingCount = totalApplicable - compliantCount;
            int rejected = (int) rows.stream().filter(r -> "REJECTED".equals(r.evidenceStatus())).count();
            double pct = totalApplicable == 0 ? 0.0 : Math.round(10000.0 * compliantCount / totalApplicable) / 100.0;
            frameworkKpis.add(new FrameworkKpi(fw, totalApplicable, compliantCount, pendingCount, rejected, pct));
        });

        int totalApplicable = frameworkKpis.stream().mapToInt(FrameworkKpi::totalApplicable).sum();
        int totalCompliant = frameworkKpis.stream().mapToInt(FrameworkKpi::compliant).sum();
        double compliancePct = totalApplicable == 0 ? 0.0
                : Math.round(10000.0 * totalCompliant / totalApplicable) / 100.0;

        List<ActionRequiredItem> actionRequired = pending.stream()
                .map(r -> new ActionRequiredItem(r.evidenceId(), r.applicationSlug(), r.controlId(), r.framework(),
                        reasonFor(r), primaryActionFor(r), r.targetDate()))
                .sorted(Comparator.comparing((ActionRequiredItem a) -> a.action() == ActionType.OPEN_ITEM ? 1 : 0))
                .limit(50)
                .toList();

        List<ActivityItem> recentActivity = recentActivity(identity, today);

        return new AppOwnerDashboard(now, identity.ownedApplicationSlugs(), pending.size(), compliancePct,
                frameworkKpis, evidencePending, evidenceRejected, tdPending, dueSoon, overdue,
                recentActivity, actionRequired);
    }

    private static String reasonFor(CompliancePendingRow r) {
        if ("REJECTED".equals(r.evidenceStatus())) return "Evidence rejected — resubmission required";
        if (r.evidenceStatus() == null) return "No evidence submitted yet";
        if (r.slaStatus() == SlaStatus.OVERDUE) return "Target date overdue";
        if (r.slaStatus() == SlaStatus.DUE_SOON) return "Target date approaching";
        if (r.slaStatus() == SlaStatus.NONE) return "Target date not set";
        return "Awaiting review";
    }

    private static ActionType primaryActionFor(CompliancePendingRow r) {
        if (r.evidenceStatus() == null) return ActionType.UPLOAD_EVIDENCE;
        if ("REJECTED".equals(r.evidenceStatus())) return ActionType.REVIEW_AND_RESUBMIT;
        if (r.slaStatus() == SlaStatus.NONE || r.slaStatus() == SlaStatus.DUE_SOON
                || r.slaStatus() == SlaStatus.OVERDUE) {
            return ActionType.SHARE_UPDATE_TD;
        }
        return ActionType.OPEN_ITEM;
    }

    private List<ActivityItem> recentActivity(AppOwnerIdentity identity, LocalDate today) {
        List<ActivityItem> out = new ArrayList<>();
        for (String slug : identity.ownedApplicationSlugs()) {
            for (EvidenceRecord r : evidenceQueries.recordsMatching(filterFor(slug))) {
                out.add(new ActivityItem(r.getLifecycleState().name(),
                        "%s / %s — %s".formatted(r.getFramework(), r.getControlId(), r.getLifecycleState()),
                        r.getId().toString(), r.getApplicationSlug(), r.getUpdatedAt()));
            }
        }
        return out.stream()
                .sorted(Comparator.comparing(ActivityItem::occurredAt).reversed())
                .limit(20)
                .toList();
    }

    // ---- compliance pending ---------------------------------------------------

    @Transactional(readOnly = true)
    public List<CompliancePendingRow> compliancePending(AppOwnerIdentity identity, String complianceStatus,
            String evidenceStatus, String slaStatus, String framework, String sortBy) {
        LocalDate today = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
        List<CompliancePendingRow> rows = allControlRows(identity, framework, today).stream()
                .filter(r -> !ControlStatus.COMPLIANT.name().equals(r.complianceStatus()))
                .filter(r -> complianceStatus == null || complianceStatus.isBlank()
                        || complianceStatus.equalsIgnoreCase(r.complianceStatus()))
                .filter(r -> evidenceStatus == null || evidenceStatus.isBlank()
                        || evidenceStatus.equalsIgnoreCase(r.evidenceStatus()))
                .filter(r -> slaStatus == null || slaStatus.isBlank()
                        || slaStatus.equalsIgnoreCase(r.slaStatus().name()))
                .collect(Collectors.toCollection(ArrayList::new));

        Comparator<CompliancePendingRow> cmp = switch (sortBy == null ? "" : sortBy) {
            case "targetDate" -> Comparator.comparing(CompliancePendingRow::targetDate,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case "status" -> Comparator.comparing(CompliancePendingRow::complianceStatus);
            case "priority" -> Comparator.comparing((CompliancePendingRow r) -> r.slaStatus().ordinal()).reversed();
            default -> Comparator.comparing(CompliancePendingRow::lastUpdated,
                    Comparator.nullsLast(Comparator.reverseOrder()));
        };
        rows.sort(cmp);
        return rows;
    }

    /** Every expected control across the owner's applications, compliant or not — the shared source
     *  for both the compliance-pending list (filtered) and the framework KPI rollup (unfiltered). */
    private List<CompliancePendingRow> allControlRows(AppOwnerIdentity identity, String framework, LocalDate today) {
        List<CompliancePendingRow> rows = new ArrayList<>();
        for (String slug : identity.ownedApplicationSlugs()) {
            Map<String, List<EvidenceRecord>> byControl = evidenceQueries.recordsMatching(filterFor(slug)).stream()
                    .collect(Collectors.groupingBy(r -> key(r.getFramework(), r.getControlId())));

            for (Expectation e : catalog.forApplication(slug, framework)) {
                List<EvidenceRecord> matches = byControl.getOrDefault(key(e.framework(), e.controlId()), List.of());
                ControlStatus status = postureOf(matches);
                EvidenceRecord latest = matches.stream()
                        .max(Comparator.comparing(EvidenceRecord::getUpdatedAt)).orElse(null);
                LocalDate td = latest == null ? null : latest.getTargetDate();
                rows.add(new CompliancePendingRow(
                        latest == null ? null : latest.getId().toString(),
                        e.framework(), e.controlId(), slug, status.name(),
                        latest == null ? null : latest.getLifecycleState().name(),
                        td, slaOf(td, today),
                        latest != null && latest.getLifecycleState() == EvidenceLifecycleState.REJECTED
                                ? latest.getLifecycleNote() : null,
                        latest == null ? null : latest.getUpdatedAt(),
                        allowedActionsFor(latest, td)));
            }
        }
        return rows;
    }

    private static List<String> allowedActionsFor(EvidenceRecord latest, LocalDate td) {
        List<String> actions = new ArrayList<>();
        if (latest == null) {
            actions.add(ActionType.UPLOAD_EVIDENCE.name());
            return actions;
        }
        if (latest.getLifecycleState() == EvidenceLifecycleState.REJECTED) {
            actions.add(ActionType.REVIEW_AND_RESUBMIT.name());
        }
        if (latest.getLifecycleState() == EvidenceLifecycleState.DRAFT
                || latest.getLifecycleState() == EvidenceLifecycleState.SUBMITTED
                || latest.getLifecycleState() == EvidenceLifecycleState.REJECTED) {
            actions.add(ActionType.SHARE_UPDATE_TD.name());
        }
        actions.add(ActionType.OPEN_ITEM.name());
        return actions;
    }

    private static SlaStatus slaOf(LocalDate targetDate, LocalDate today) {
        if (targetDate == null) return SlaStatus.NONE;
        if (targetDate.isBefore(today)) return SlaStatus.OVERDUE;
        if (!targetDate.isAfter(today.plusDays(DUE_SOON_DAYS))) return SlaStatus.DUE_SOON;
        return SlaStatus.ON_TRACK;
    }

    /** Same derivation as ComplianceService.posture(), at evidence-record granularity. */
    private ControlStatus postureOf(List<EvidenceRecord> matches) {
        if (matches.isEmpty()) {
            return ControlStatus.MISSING_EVIDENCE;
        }
        List<CheckResult> verdicts = new ArrayList<>();
        for (EvidenceRecord r : matches) {
            verdicts.addAll(checkResults.findByEvidenceRecordId(r.getId()));
        }
        if (verdicts.isEmpty()) {
            return ControlStatus.NOT_ASSESSED;
        }
        boolean anyFail = verdicts.stream().anyMatch(v -> v.getStatus() == CheckStatus.FAIL);
        boolean anyWarn = verdicts.stream().anyMatch(v -> v.getStatus() == CheckStatus.WARNING);
        boolean anyPass = verdicts.stream().anyMatch(v -> v.getStatus() == CheckStatus.PASS);
        if (anyFail) return ControlStatus.NON_COMPLIANT;
        if (anyWarn) return ControlStatus.PARTIALLY_COMPLIANT;
        if (anyPass) return ControlStatus.COMPLIANT;
        return ControlStatus.NOT_ASSESSED;
    }

    private static EvidenceFilter filterFor(String slug) {
        return new EvidenceFilter(slug, null, null, null, null, null, null, 0, 100_000);
    }

    private static String key(String framework, String controlId) {
        return (framework == null ? "" : framework.toUpperCase()) + "|" + (controlId == null ? "" : controlId.toUpperCase());
    }

    // ---- evidence detail / upload / resubmit ----------------------------------

    @Transactional(readOnly = true)
    public EvidenceView evidenceDetail(AppOwnerIdentity identity, UUID id) {
        EvidenceView view = evidenceQueries.get(id);
        identity.requireOwns(view.applicationSlug());
        return view;
    }

    @Transactional
    public EvidenceView uploadEvidence(AppOwnerIdentity identity, UploadEvidenceRequest req) {
        identity.requireOwns(req.applicationSlug());
        if (req.contentBase64() == null && req.contentText() == null) {
            throw ApiException.badRequest("attach a file (contentBase64) or provide contentText");
        }
        var result = ingestion.ingest(new IngestRequest(req.applicationSlug(), req.controlId(), req.framework(),
                req.sourceSystem() == null || req.sourceSystem().isBlank() ? "APP_OWNER_UPLOAD" : req.sourceSystem(),
                null, req.title(), req.contentType(), req.contentBase64(), req.contentText(), clock.instant(),
                identity.username(), Map.of(), req.description() == null ? Map.of()
                        : Map.of("app-owner.description", req.description())));
        audit(identity.username(), "EVIDENCE_UPLOADED", "EVIDENCE", result.evidenceId(), null,
                result.outcome().name(), req.description());
        return evidenceQueries.get(UUID.fromString(result.evidenceId()));
    }

    /** Resubmit a rejected item: reuses the existing SUBMIT transition (REJECTED -> SUBMITTED). */
    @Transactional
    public com.pramaan.backend.evidence.EvidenceDtos.EvidenceLifecycleView resubmitEvidence(
            AppOwnerIdentity identity, UUID id, String comment) {
        EvidenceView view = evidenceQueries.get(id);
        identity.requireOwns(view.applicationSlug());
        if (!"REJECTED".equals(view.lifecycleState())) {
            throw ApiException.conflict("evidence is in state " + view.lifecycleState() + ", not REJECTED");
        }
        String oldState = view.lifecycleState();
        var result = lifecycle.transition(id, new LifecycleTransitionRequest(LifecycleAction.SUBMIT,
                identity.username(), comment), AppOwnerContext.APP_OWNER_ROLE, null);
        audit(identity.username(), "EVIDENCE_RESUBMITTED", "EVIDENCE", id.toString(), oldState,
                result.state(), comment);
        return result;
    }

    // ---- target date ------------------------------------------------------

    @Transactional
    public EvidenceView shareOrUpdateTargetDate(AppOwnerIdentity identity, UUID id, LocalDate targetDate, String comment) {
        EvidenceRecord record = records.findById(id).orElseThrow(() -> ApiException.notFound("Unknown evidence: " + id));
        identity.requireOwns(record.getApplicationSlug());
        if (!PENDING_STATES.contains(record.getLifecycleState())) {
            throw ApiException.conflict("cannot set a target date while evidence is " + record.getLifecycleState());
        }
        if (targetDate == null) {
            throw ApiException.badRequest("targetDate is required");
        }
        Instant now = clock.instant();
        LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);
        if (targetDate.isBefore(today.plusDays(TD_MIN_DAYS_AHEAD))) {
            throw ApiException.badRequest("targetDate must be at least " + TD_MIN_DAYS_AHEAD + " day(s) in the future");
        }
        if (targetDate.isAfter(today.plusDays(TD_MAX_DAYS_AHEAD))) {
            throw ApiException.badRequest("targetDate cannot be more than " + TD_MAX_DAYS_AHEAD + " days out");
        }
        LocalDate old = record.getTargetDate();
        if (old == null && (comment == null || comment.isBlank())) {
            throw ApiException.badRequest("a comment is required when sharing a target date for the first time");
        }
        record.applyTargetDate(targetDate, comment, now);
        records.save(record);
        tdHistory.save(new TargetDateHistoryEntry(UUID.randomUUID(), id, old, targetDate, comment,
                identity.username(), now));
        audit(identity.username(), old == null ? "TD_SHARED" : "TD_UPDATED", "EVIDENCE", id.toString(),
                old == null ? null : old.toString(), targetDate.toString(), comment);
        notify(identity.username(), record, old == null ? "TD_REQUIRED" : "TD_APPROACHING",
                "Target date " + (old == null ? "shared" : "updated") + " for " + record.getFramework()
                        + " / " + record.getControlId() + ": " + targetDate);
        return evidenceQueries.get(id);
    }

    @Transactional(readOnly = true)
    public List<TargetDateHistoryView> targetDateHistory(AppOwnerIdentity identity, UUID id) {
        EvidenceRecord record = records.findById(id).orElseThrow(() -> ApiException.notFound("Unknown evidence: " + id));
        identity.requireOwns(record.getApplicationSlug());
        return tdHistory.findByEvidenceRecordIdOrderByCreatedAtAsc(id).stream()
                .map(h -> new TargetDateHistoryView(h.getOldTargetDate(), h.getNewTargetDate(), h.getReason(),
                        h.getActorUsername(), h.getCreatedAt()))
                .toList();
    }

    // ---- notifications (view-only) -----------------------------------------

    /**
     * View-only feed. TD_REQUIRED / TD_APPROACHING are persisted (recorded when the owner
     * acts — see {@link #shareOrUpdateTargetDate}); EVIDENCE_REJECTED / EVIDENCE_APPROVED /
     * ITEM_OVERDUE are synthesized from current evidence/TD state so they never go stale and
     * never require hooking into the shared approve/reject code path (out of scope for this role).
     */
    @Transactional(readOnly = true)
    public List<NotificationView> notifications(AppOwnerIdentity identity) {
        List<NotificationView> out = new ArrayList<>(notifications.findByUsernameOrderByCreatedAtDesc(identity.username())
                .stream()
                .map(n -> new NotificationView(n.getId().toString(), n.getType(), n.getMessage(),
                        n.getEvidenceRecordId() == null ? null : n.getEvidenceRecordId().toString(),
                        n.getApplicationSlug(), n.getCreatedAt(), n.getReadAt() != null))
                .toList());

        for (String slug : identity.ownedApplicationSlugs()) {
            for (EvidenceRecord r : evidenceQueries.recordsMatching(filterFor(slug))) {
                if (r.getLifecycleState() == EvidenceLifecycleState.REJECTED) {
                    out.add(syntheticNotification("EVIDENCE_REJECTED",
                            "Evidence rejected: " + r.getFramework() + " / " + r.getControlId()
                                    + (r.getLifecycleNote() != null ? " — " + r.getLifecycleNote() : ""),
                            r, r.getReviewedAt() != null ? r.getReviewedAt() : r.getUpdatedAt()));
                } else if (r.getLifecycleState() == EvidenceLifecycleState.APPROVED) {
                    out.add(syntheticNotification("EVIDENCE_APPROVED",
                            "Evidence approved: " + r.getFramework() + " / " + r.getControlId(),
                            r, r.getReviewedAt() != null ? r.getReviewedAt() : r.getUpdatedAt()));
                }
            }
        }
        for (CompliancePendingRow row : compliancePending(identity, null, null, "OVERDUE", null, "targetDate")) {
            out.add(new NotificationView(null, "ITEM_OVERDUE",
                    "Overdue: " + row.framework() + " / " + row.controlId() + " (target date " + row.targetDate() + ")",
                    row.evidenceId(), row.applicationSlug(), row.lastUpdated() != null ? row.lastUpdated()
                            : clock.instant(), false));
        }
        return out.stream().sorted(Comparator.comparing(NotificationView::createdAt).reversed()).limit(50).toList();
    }

    private static NotificationView syntheticNotification(String type, String message, EvidenceRecord r, Instant at) {
        return new NotificationView(null, type, message, r.getId().toString(), r.getApplicationSlug(), at, false);
    }

    private void notify(String username, EvidenceRecord record, String type, String message) {
        // View-only feed for the App Owner themself — the "configured notification on submit/update".
        notifications.save(new AppOwnerNotification(UUID.randomUUID(), username, type, message,
                record.getId(), record.getApplicationSlug(), clock.instant()));
    }

    // ---- reports (read-only, scoped) ---------------------------------------

    @Transactional(readOnly = true)
    public List<ReportService.ReportInfo> reportCatalog() {
        return reportService.catalog();
    }

    @Transactional(readOnly = true)
    public Object reportJson(AppOwnerIdentity identity, String name, String applicationSlug, String framework) {
        String scopedSlug = requireOwnedOrFirstOwned(identity, applicationSlug);
        return reportService.json(name, scopedSlug, framework);
    }

    @Transactional(readOnly = true)
    public String reportCsv(AppOwnerIdentity identity, String name, String applicationSlug, String framework) {
        String scopedSlug = requireOwnedOrFirstOwned(identity, applicationSlug);
        return reportService.csv(name, scopedSlug, framework);
    }

    private String requireOwnedOrFirstOwned(AppOwnerIdentity identity, String applicationSlug) {
        if (applicationSlug != null && !applicationSlug.isBlank()) {
            identity.requireOwns(applicationSlug);
            return applicationSlug;
        }
        if (identity.ownedApplicationSlugs().isEmpty()) {
            throw ApiException.forbidden(identity.username() + " has no authorized applications");
        }
        return identity.ownedApplicationSlugs().get(0);
    }

    // ---- audit --------------------------------------------------------------

    private void audit(String actor, String action, String entityType, String entityId, String oldValue,
                       String newValue, String reason) {
        auditLog.save(new AppOwnerAuditLogEntry(UUID.randomUUID(), actor, action, entityType, entityId,
                oldValue, newValue, reason, clock.instant()));
    }
}
