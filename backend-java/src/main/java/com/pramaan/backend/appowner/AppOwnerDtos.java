package com.pramaan.backend.appowner;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class AppOwnerDtos {

    private AppOwnerDtos() {}

    public enum ActionType { UPLOAD_EVIDENCE, REVIEW_AND_RESUBMIT, SHARE_UPDATE_TD, OPEN_ITEM }

    public enum SlaStatus { NONE, ON_TRACK, DUE_SOON, OVERDUE }

    public record FrameworkKpi(
            String framework, int totalApplicable, int compliant, int pending, int rejected, double compliancePct) {}

    public record ActivityItem(String type, String message, String evidenceId, String applicationSlug, Instant occurredAt) {}

    public record ActionRequiredItem(
            String evidenceId, String applicationSlug, String controlId, String framework,
            String reason, ActionType action, LocalDate targetDate) {}

    public record AppOwnerDashboard(
            Instant generatedAt,
            List<String> ownedApplications,
            int compliancePendingCount,
            double compliancePct,
            List<FrameworkKpi> frameworkKpis,
            int evidencePending,
            int evidenceRejected,
            int tdPending,
            int dueSoon,
            int overdue,
            List<ActivityItem> recentActivity,
            List<ActionRequiredItem> actionRequired) {}

    public record CompliancePendingRow(
            String evidenceId,
            String framework,
            String controlId,
            String applicationSlug,
            String complianceStatus,
            String evidenceStatus,
            LocalDate targetDate,
            SlaStatus slaStatus,
            String auditorComment,
            Instant lastUpdated,
            List<String> allowedActions) {}

    public record TargetDateHistoryView(
            LocalDate oldTargetDate, LocalDate newTargetDate, String reason, String actorUsername, Instant createdAt) {}

    public record NotificationView(
            String id, String type, String message, String evidenceId, String applicationSlug,
            Instant createdAt, boolean read) {}

    public record ShareTargetDateRequest(LocalDate targetDate, String comment) {}

    public record ResubmitRequest(String comment) {}

    public record UploadEvidenceRequest(
            String applicationSlug, String controlId, String framework, String sourceSystem,
            String title, String contentType, String contentBase64, String contentText,
            String description) {}
}
