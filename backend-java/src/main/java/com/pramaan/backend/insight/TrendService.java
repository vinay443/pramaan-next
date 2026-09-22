package com.pramaan.backend.insight;

import com.pramaan.backend.evidence.domain.EvidenceLifecycleEvent;
import com.pramaan.backend.evidence.domain.EvidenceLifecycleState;
import com.pramaan.backend.evidence.repo.EvidenceLifecycleEventRepository;
import com.pramaan.backend.evidence.repo.EvidenceRecordRepository;
import com.pramaan.backend.insight.InsightDtos.ClosureStats;
import com.pramaan.backend.insight.InsightDtos.CollectionPoint;
import com.pramaan.backend.insight.InsightDtos.LeadershipDashboard;
import com.pramaan.backend.insight.InsightDtos.TrendPoint;
import com.pramaan.backend.insight.InsightDtos.TrendReport;
import com.pramaan.backend.application.ApplicationService;
import com.pramaan.backend.insight.domain.ComplianceSnapshot;
import com.pramaan.backend.insight.domain.ComplianceSnapshotApp;
import com.pramaan.backend.insight.repo.ComplianceSnapshotAppRepository;
import com.pramaan.backend.insight.repo.ComplianceSnapshotRepository;
import com.pramaan.backend.scheduler.domain.SchedulerRun;
import com.pramaan.backend.scheduler.SchedulerRunRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.time.temporal.IsoFields;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * UC19 — compliance trend & closure. Trend points come from persisted
 * {@link ComplianceSnapshot} rows (each written by re-running the existing
 * {@link LeadershipService} rollup — no new scoring). Closure metrics are derived
 * from the {@code evidence_lifecycle_event} audit trail; collection throughput
 * from {@code scheduler_run} history. No new pipeline.
 */
@Service
public class TrendService {

    private final ComplianceSnapshotRepository snapshots;
    private final LeadershipService leadership;
    private final EvidenceRecordRepository records;
    private final EvidenceLifecycleEventRepository lifecycleEvents;
    private final SchedulerRunRepository runs;
    private final com.pramaan.backend.evidence.EvidenceQueryService evidenceQuery;
    private final Clock clock;
    private final ComplianceSnapshotAppRepository snapshotApps;
    private final ApplicationService applications;

    public TrendService(ComplianceSnapshotRepository snapshots, LeadershipService leadership,
                        EvidenceRecordRepository records, EvidenceLifecycleEventRepository lifecycleEvents,
                        SchedulerRunRepository runs,
                        com.pramaan.backend.evidence.EvidenceQueryService evidenceQuery, Clock clock,
                        ComplianceSnapshotAppRepository snapshotApps, ApplicationService applications) {
        this.snapshotApps = snapshotApps;
        this.applications = applications;
        this.snapshots = snapshots;
        this.leadership = leadership;
        this.records = records;
        this.lifecycleEvents = lifecycleEvents;
        this.runs = runs;
        this.evidenceQuery = evidenceQuery;
        this.clock = clock;
    }

    @Transactional
    public TrendPoint snapshot() {
        LeadershipDashboard d = leadership.dashboard();
        ComplianceSnapshot saved = snapshots.save(build(clock.instant(), d));
        snapshotApps.saveAll(appRows(saved.getId(), d));
        return toPoint(saved);
    }

    /** Build (without persisting) a snapshot for the given instant. Used by the seeder too. */
    public ComplianceSnapshot build(Instant at) {
        return build(at, leadership.dashboard());
    }

    private ComplianceSnapshot build(Instant at, LeadershipDashboard d) {
        int approved = (int) records.countByLifecycleState(EvidenceLifecycleState.APPROVED);
        int openFindings = Math.max(0, d.expected() - d.compliant());
        var board = evidenceQuery.dashboard();
        return new ComplianceSnapshot(UUID.randomUUID(), at, d.applications(), d.expected(), d.compliant(),
                d.compliancePct(), d.completenessPct(), approved, openFindings,
                (int) board.records(), board.integrity().checked(), board.integrity().intact());
    }

    @Transactional
    public ComplianceSnapshot saveRaw(ComplianceSnapshot s) {
        return snapshots.save(s);
    }

    public long snapshotCount() {
        return snapshots.count();
    }

    /** Per-application rows for a snapshot, tagged with each application's business unit. */
    private List<ComplianceSnapshotApp> appRows(UUID snapshotId, LeadershipDashboard d) {
        Map<String, String> buBySlug = businessUnits();
        return d.byApplication().stream()
                .map(a -> new ComplianceSnapshotApp(snapshotId, a.applicationSlug(),
                        buBySlug.get(a.applicationSlug()), a.expected(), a.compliant(), a.covered()))
                .toList();
    }

    private Map<String, String> businessUnits() {
        Map<String, String> out = new java.util.HashMap<>();
        applications.list().forEach(a -> out.put(a.slug(), a.businessUnit()));
        return out;
    }

    /** Current applications (slug, business unit, expected controls) — seeds synthetic per-app history. */
    public List<AppSeed> currentApps() {
        Map<String, String> bu = businessUnits();
        return leadership.dashboard().byApplication().stream()
                .map(a -> new AppSeed(a.applicationSlug(), bu.get(a.applicationSlug()), a.expected()))
                .toList();
    }

    public record AppSeed(String slug, String businessUnit, int expected) {}

    @Transactional
    public void saveAppRows(List<ComplianceSnapshotApp> rows) {
        snapshotApps.saveAll(rows);
    }

    /** Snapshot ids that already have per-application rows. */
    public java.util.Set<UUID> snapshotIdsWithApps() {
        return new java.util.HashSet<>(snapshotApps.findSnapshotIdsWithApps());
    }

    public List<ComplianceSnapshot> allSnapshots() {
        return snapshots.findAllByOrderByTakenAtAsc();
    }

    /** Trend scoped to a single business unit / function; see {@link #trend(List)}. */
    @Transactional(readOnly = true)
    public TrendReport trend(String businessUnit) {
        return trend(businessUnit == null ? null : List.of(businessUnit));
    }

    /**
     * Trend scoped to one or more business units (a function, or a vertical). Points sum the
     * per-application rows stored with each snapshot (snapshots without rows are skipped);
     * {@code current} is the live rollup restricted to those units. Evidence/integrity
     * metrics, closure and collection throughput are not tracked per application, so they are
     * zeroed/empty here. No (or blank) business units means the unscoped trend.
     */
    @Transactional(readOnly = true)
    public TrendReport trend(List<String> businessUnits) {
        java.util.Set<String> scope = ScopeFilter.of(businessUnits);
        if (scope == null) {
            return trend();
        }
        List<ComplianceSnapshot> all = snapshots.findAllByOrderByTakenAtAsc();
        Map<UUID, List<ComplianceSnapshotApp>> byId = new java.util.HashMap<>();
        snapshotApps.findBySnapshotIdIn(all.stream().map(ComplianceSnapshot::getId).toList()).stream()
                .filter(r -> ScopeFilter.matches(scope, r.getBusinessUnit()))
                .forEach(r -> byId.computeIfAbsent(r.getSnapshotId(), k -> new ArrayList<>()).add(r));
        List<TrendPoint> points = all.stream()
                .filter(s -> byId.containsKey(s.getId()))
                .map(s -> scopedPoint(s.getTakenAt(), byId.get(s.getId()).stream()
                        .map(r -> new int[] {r.getExpected(), r.getCompliant(), r.getCovered()}).toList()))
                .toList();

        List<int[]> live = leadership.dashboard(scope).byApplication().stream()
                .map(a -> new int[] {a.expected(), a.compliant(), a.covered()}).toList();
        return new TrendReport(clock.instant(), scopedPoint(clock.instant(), live), points,
                new ClosureStats(0, 0, 0, null, Map.of(), null), List.of());
    }

    /**
     * Trend scoped to a single application: current rollup and points are unscoped (portfolio-wide),
     * but {@code closure} (approvals/rejections/avg review time/rejection trend) is computed only
     * from this application's evidence lifecycle events — see App Owner dashboard "Overview" KPIs.
     */
    @Transactional(readOnly = true)
    public TrendReport trendForApplication(String applicationSlug) {
        List<TrendPoint> points = snapshots.findAllByOrderByTakenAtAsc().stream()
                .map(TrendService::toPoint).toList();
        TrendPoint current = toPoint(build(clock.instant()));
        return new TrendReport(clock.instant(), current, points, closure(applicationSlug), collection());
    }

    private static TrendPoint scopedPoint(Instant at, List<int[]> rows) {
        int expected = rows.stream().mapToInt(r -> r[0]).sum();
        int compliant = rows.stream().mapToInt(r -> r[1]).sum();
        int covered = rows.stream().mapToInt(r -> r[2]).sum();
        double compliancePct = expected == 0 ? 0.0 : CompletenessService.round(100.0 * compliant / expected);
        double completenessPct = expected == 0 ? 0.0 : CompletenessService.round(100.0 * covered / expected);
        return new TrendPoint(at, expected, compliant, compliancePct, completenessPct, 0,
                Math.max(0, expected - compliant), 0, 0, 0);
    }

    @Transactional(readOnly = true)
    public TrendReport trend() {
        List<TrendPoint> points = snapshots.findAllByOrderByTakenAtAsc().stream()
                .map(TrendService::toPoint).toList();
        // headline figure is the LIVE rollup, not the last (possibly synthetic) snapshot,
        // so Trend agrees with Compliance / Leadership / Enterprise / National.
        TrendPoint current = toPoint(build(clock.instant()));
        return new TrendReport(clock.instant(), current, points, closure(null), collection());
    }

    /** @param applicationSlug when non-null, restricts the lifecycle events to this application's evidence. */
    private ClosureStats closure(String applicationSlug) {
        List<EvidenceLifecycleEvent> scopedEvents;
        if (applicationSlug == null) {
            scopedEvents = lifecycleEvents.findAll();
        } else {
            List<UUID> ids = records.findByApplicationSlug(applicationSlug).stream()
                    .map(com.pramaan.backend.evidence.domain.EvidenceRecord::getId).toList();
            scopedEvents = ids.isEmpty() ? List.of() : lifecycleEvents.findByEvidenceRecordIdIn(ids);
        }
        Map<UUID, List<EvidenceLifecycleEvent>> byEvidence = new java.util.HashMap<>();
        int approvals = 0;
        int rejections = 0;
        int resubmissions = 0;
        Map<String, Integer> approvalsByWeek = new TreeMap<>();
        Map<String, Integer> rejectionsByMonth = new TreeMap<>();
        for (EvidenceLifecycleEvent e : scopedEvents) {
            byEvidence.computeIfAbsent(e.getEvidenceRecordId(), k -> new ArrayList<>()).add(e);
            switch (e.getAction()) {
                case "APPROVE" -> {
                    approvals++;
                    approvalsByWeek.merge(week(e.getOccurredAt()), 1, Integer::sum);
                }
                case "REJECT" -> {
                    rejections++;
                    rejectionsByMonth.merge(month(e.getOccurredAt()), 1, Integer::sum);
                }
                case "RE_SUBMITTED" -> resubmissions++;
                default -> { }
            }
        }
        List<Long> durations = new ArrayList<>();
        for (List<EvidenceLifecycleEvent> evs : byEvidence.values()) {
            evs.sort(Comparator.comparing(EvidenceLifecycleEvent::getOccurredAt));
            Instant submittedAt = null;
            for (EvidenceLifecycleEvent e : evs) {
                if (e.getToState() == EvidenceLifecycleState.SUBMITTED) {
                    submittedAt = e.getOccurredAt();
                } else if (e.getAction().equals("APPROVE") && submittedAt != null) {
                    durations.add(ChronoUnit.SECONDS.between(submittedAt, e.getOccurredAt()));
                    submittedAt = null;
                }
            }
        }
        Double avgDays = durations.isEmpty() ? null
                : Math.round((durations.stream().mapToLong(Long::longValue).average().orElse(0) / 86_400.0)
                * 100.0) / 100.0;
        String currentMonth = month(clock.instant());
        String prevMonth = month(clock.instant().minus(30, ChronoUnit.DAYS));
        int currentCount = rejectionsByMonth.getOrDefault(currentMonth, 0);
        int prevCount = rejectionsByMonth.getOrDefault(prevMonth, 0);
        Double rejectionTrendPct = prevCount == 0 ? null
                : Math.round((currentCount - prevCount) * 1000.0 / prevCount) / 10.0;
        return new ClosureStats(approvals, rejections, resubmissions, avgDays, approvalsByWeek, rejectionTrendPct);
    }

    private List<CollectionPoint> collection() {
        List<SchedulerRun> recent = runs.findAllByOrderByCreatedAtDesc(PageRequest.of(0, 50)).getContent();
        List<CollectionPoint> out = new ArrayList<>();
        recent.stream()
                .filter(r -> r.getFinishedAt() != null)
                .sorted(Comparator.comparing(SchedulerRun::getCreatedAt))
                .forEach(r -> out.add(new CollectionPoint(r.getFinishedAt(), r.getIngested(),
                        r.getDuplicates(), r.getFailed())));
        return out;
    }

    private static TrendPoint toPoint(ComplianceSnapshot s) {
        return new TrendPoint(s.getTakenAt(), s.getExpected(), s.getCompliant(), s.getCompliancePct(),
                s.getCompletenessPct(), s.getApprovedEvidence(), s.getOpenFindings(),
                s.getEvidenceCount(), s.getIntegrityChecked(), s.getIntegrityIntact());
    }

    private static String week(Instant at) {
        var d = at.atZone(ZoneOffset.UTC);
        return "%d-W%02d".formatted(d.get(IsoFields.WEEK_BASED_YEAR), d.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
    }

    private static String month(Instant at) {
        var d = at.atZone(ZoneOffset.UTC);
        return "%d-%02d".formatted(d.getYear(), d.getMonthValue());
    }
}
