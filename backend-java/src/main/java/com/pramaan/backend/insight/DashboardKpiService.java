package com.pramaan.backend.insight;

import com.pramaan.backend.common.ApiException;
import com.pramaan.backend.config.PramaanProperties;
import com.pramaan.backend.evidence.EvidenceQueryService;
import com.pramaan.backend.evidence.EvidenceQueryService.EvidenceFilter;
import com.pramaan.backend.evidence.EvidenceQueryService.TagFacets;
import com.pramaan.backend.evidence.domain.EvidenceLifecycleState;
import com.pramaan.backend.evidence.domain.EvidenceRecord;
import com.pramaan.backend.insight.EvidenceLifecycleSummaryService.ReviewMetrics;
import com.pramaan.backend.insight.InsightDtos.AuditorSla;
import com.pramaan.backend.insight.InsightDtos.LifecycleStateCounts;
import com.pramaan.backend.insight.InsightDtos.PendingAging;
import com.pramaan.backend.notification.NotificationRepository;
import com.pramaan.backend.retention.RetentionPolicyService;
import com.pramaan.backend.observation.AuditObservation;
import com.pramaan.backend.observation.AuditObservationRepository;
import com.pramaan.backend.scheduler.ScheduleJobRepository;
import com.pramaan.backend.scheduler.SchedulerRunRepository;
import com.pramaan.backend.scheduler.domain.ScheduleJob;
import com.pramaan.backend.scheduler.domain.SchedulerRun;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dashboard KPIs computed only from stored data: the evidence set is exactly what the repository search returns
 * for the same filters ({@link EvidenceQueryService#recordsMatching}), the automated-collection numbers come from
 * scheduler runs, schedules, observations and notifications. A metric that cannot be computed is null, never faked.
 */
@Service
public class DashboardKpiService {

    public static final Set<String> METHODS = Set.of("scheduled", "manual", "bulk", "on-demand", "predefined-query");
    private static final int EXPIRING_WINDOW_DAYS = 30;

    public record Filters(String application, String framework, String collectionMethod, Instant from, Instant to) {}

    public record EvidenceKpis(int total, LifecycleStateCounts counts, int closed, Double closureRatePct, int rejected,
                               int expiringSoon, int expired, int retentionDays, Double avgReviewTimeDays,
                               AuditorSla auditorSla, PendingAging pendingAging) {}

    public record LastRun(String runId, String status, Instant at, String scheduleName) {}

    public record AutomatedKpis(int totalRuns, int scheduledRuns, int manualRuns, int bulkRuns, int completed,
                                int partial, int failed, int inProgress, Double successRatePct, long evidenceIngested,
                                long duplicatesSkipped, LastRun lastRun, int activeSchedules, Instant nextDueAt,
                                int runsWithinSla, int runsBreached, int slaSeconds, long openObservations,
                                long unreadNotifications) {}

    public record DashboardKpis(Instant generatedAt, Filters filters, EvidenceKpis evidence, AutomatedKpis automated) {}

    private final EvidenceQueryService evidence;
    private final EvidenceLifecycleSummaryService lifecycle;
    private final SchedulerRunRepository runs;
    private final ScheduleJobRepository schedules;
    private final AuditObservationRepository observations;
    private final NotificationRepository notifications;
    private final PramaanProperties props;
    private final RetentionPolicyService retentionPolicy;
    private final Clock clock;

    public DashboardKpiService(EvidenceQueryService evidence, EvidenceLifecycleSummaryService lifecycle,
                               SchedulerRunRepository runs, ScheduleJobRepository schedules,
                               AuditObservationRepository observations, NotificationRepository notifications,
                               PramaanProperties props, Clock clock, RetentionPolicyService retentionPolicy) {
        this.retentionPolicy = retentionPolicy;
        this.evidence = evidence;
        this.lifecycle = lifecycle;
        this.runs = runs;
        this.schedules = schedules;
        this.observations = observations;
        this.notifications = notifications;
        this.props = props;
        this.clock = clock;
    }

    /** Accepts yyyy-MM-dd (start of that day, or the end of it when {@code endOfDay}) or a full ISO instant. */
    public static Instant parseInstant(String field, String v, boolean endOfDay) {
        if (v == null || v.isBlank()) return null;
        try {
            if (v.length() <= 10) {
                LocalDate d = LocalDate.parse(v.trim());
                return (endOfDay ? d.plusDays(1) : d).atStartOfDay(ZoneOffset.UTC).toInstant();
            }
            return Instant.parse(v.trim());
        } catch (java.time.format.DateTimeParseException e) {
            throw ApiException.badRequest(field + " must be a date (yyyy-MM-dd) or an ISO instant");
        }
    }

    @Transactional(readOnly = true)
    public DashboardKpis kpis(Filters f, String callerRole) {
        String method = f.collectionMethod() == null || f.collectionMethod().isBlank() ? null
                : f.collectionMethod().trim().toLowerCase(Locale.ROOT);
        if (method != null && !METHODS.contains(method)) {
            throw ApiException.badRequest("collectionMethod must be one of " + METHODS.stream().sorted().toList());
        }
        Filters norm = new Filters(blank(f.application()), blank(f.framework()), method, f.from(), f.to());
        Instant now = clock.instant();
        return new DashboardKpis(now, norm, evidenceKpis(norm, now), automatedKpis(norm, now, callerRole));
    }

    // ---- evidence -------------------------------------------------------------------------------------

    private EvidenceKpis evidenceKpis(Filters f, Instant now) {
        // same filter + facet the repository search applies, so total == repository totalItems
        List<EvidenceRecord> recs = evidence.recordsMatching(
                new EvidenceFilter(f.application(), f.framework(), null, null, f.from(), f.to(), null, 0, 100_000),
                new TagFacets(null, f.collectionMethod()));

        int draft = 0, submitted = 0, approved = 0, rejected = 0, expiredState = 0, superseded = 0, archived = 0;
        for (EvidenceRecord r : recs) {
            switch (r.getLifecycleState()) {
                case DRAFT -> draft++;
                case SUBMITTED -> submitted++;
                case APPROVED -> approved++;
                case REJECTED -> rejected++;
                case EXPIRED -> expiredState++;
                case SUPERSEDED -> superseded++;
                case ARCHIVED -> archived++;
            }
        }
        LifecycleStateCounts counts = new LifecycleStateCounts(draft, submitted, approved, rejected, expiredState,
                superseded, archived);
        int total = recs.size();
        int closed = approved + expiredState + superseded + archived; // the dashboard "Closed" chip, plus archived
        Double closureRate = total == 0 ? null : Math.round(closed * 1000.0 / total) / 10.0;

        int retention = retentionPolicy.days();
        int expiringSoon = 0, expired = 0;
        for (EvidenceRecord r : recs) {
            if (r.getLifecycleState() == EvidenceLifecycleState.SUPERSEDED
                    || r.getLifecycleState() == EvidenceLifecycleState.ARCHIVED || r.getLatestCollectedAt() == null) continue;
            Instant expiresAt = r.getLatestCollectedAt().plus(retention, ChronoUnit.DAYS);
            if (expiresAt.isBefore(now)) expired++;
            else if (!expiresAt.isAfter(now.plus(EXPIRING_WINDOW_DAYS, ChronoUnit.DAYS))) expiringSoon++;
        }
        ReviewMetrics rm = lifecycle.reviewMetrics(recs);
        return new EvidenceKpis(total, counts, closed, closureRate, rejected, expiringSoon, expired, retention,
                rm.avgReviewDays(), rm.auditorSla(), rm.pendingAging());
    }

    // ---- automated collection -------------------------------------------------------------------------

    private AutomatedKpis automatedKpis(Filters f, Instant now, String role) {
        List<SchedulerRun> matching = runs.findAll().stream().filter(r -> runMatches(r, f)).toList();
        int sla = props.scheduler() == null ? 60 : props.scheduler().slaSecondsOrDefault();

        int scheduled = 0, manual = 0, bulk = 0, completed = 0, partial = 0, failed = 0, inProgress = 0;
        int within = 0, breached = 0;
        long ingested = 0, duplicates = 0;
        for (SchedulerRun r : matching) {
            switch (r.getTrigger()) {
                case SCHEDULED -> scheduled++;
                case MANUAL -> manual++;
                case BULK -> bulk++;
            }
            switch (r.getStatus()) {
                case COMPLETED -> completed++;
                case PARTIAL -> partial++;
                case FAILED -> failed++;
                default -> inProgress++;
            }
            ingested += r.getIngested();
            duplicates += r.getDuplicates();
            if (r.getFinishedAt() != null) {
                if (Duration.between(r.getCreatedAt(), r.getFinishedAt()).compareTo(Duration.ofSeconds(sla)) > 0) breached++;
                else within++;
            }
        }
        int finished = completed + partial + failed;
        Double successRate = finished == 0 ? null : Math.round(completed * 1000.0 / finished) / 10.0;

        LastRun last = matching.stream().max(Comparator.comparing(DashboardKpiService::activityAt))
                .map(r -> new LastRun(r.getId().toString(), r.getStatus().name(), activityAt(r), r.getScheduleName()))
                .orElse(null);

        boolean scheduleMethod = f.collectionMethod() == null || f.collectionMethod().equals("scheduled");
        List<ScheduleJob> jobs = !scheduleMethod ? List.<ScheduleJob>of() : schedules.findAll().stream()
                .filter(j -> f.application() == null || j.getApplication().equalsIgnoreCase(f.application()))
                .filter(ScheduleJob::isActive).toList();
        Instant nextDue = jobs.stream().map(ScheduleJob::getNextRunAt).filter(java.util.Objects::nonNull)
                .min(Comparator.naturalOrder()).orElse(null);

        long openObs = observations.findAllByOrderByCreatedAtDesc().stream()
                .filter(o -> o.getStatus() == AuditObservation.Status.OPEN)
                .filter(o -> f.application() == null || o.getApplication().equalsIgnoreCase(f.application()))
                .filter(o -> f.framework() == null || o.getFramework().equalsIgnoreCase(f.framework()))
                .filter(o -> f.from() == null || !o.getCreatedAt().isBefore(f.from()))
                .filter(o -> f.to() == null || o.getCreatedAt().isBefore(f.to()))
                .count();
        long unread = role == null ? 0 : notifications.countByRecipientRoleAndReadAtIsNull(role);

        return new AutomatedKpis(matching.size(), scheduled, manual, bulk, completed, partial, failed, inProgress,
                successRate, ingested, duplicates, last, jobs.size(), nextDue, within, breached, sla, openObs, unread);
    }

    private static Instant activityAt(SchedulerRun r) {
        return r.getFinishedAt() != null ? r.getFinishedAt() : r.getCreatedAt();
    }

    /**
     * A run matches when it covers the application / framework (an empty list means "all", so it covers any),
     * has the trigger the collection method stands for (scheduled / manual / bulk; on-demand and
     * predefined-query evidence never comes from a scheduler run), and was created inside the date range.
     */
    private static boolean runMatches(SchedulerRun r, Filters f) {
        if (f.application() != null && !covers(r.getApplications(), f.application())) return false;
        if (f.framework() != null && !covers(r.getFrameworks(), f.framework())) return false;
        if (f.collectionMethod() != null) {
            boolean ok = switch (f.collectionMethod()) {
                case "scheduled" -> r.getTrigger() == SchedulerRun.Trigger.SCHEDULED;
                case "manual" -> r.getTrigger() == SchedulerRun.Trigger.MANUAL;
                case "bulk" -> r.getTrigger() == SchedulerRun.Trigger.BULK;
                default -> false;
            };
            if (!ok) return false;
        }
        if (f.from() != null && r.getCreatedAt().isBefore(f.from())) return false;
        return f.to() == null || r.getCreatedAt().isBefore(f.to());
    }

    private static boolean covers(String csv, String wanted) {
        if (csv == null || csv.isBlank()) return true;
        for (String s : csv.split("\\s*,\\s*")) {
            if (s.equalsIgnoreCase(wanted)) return true;
        }
        return false;
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
