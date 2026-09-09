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
import com.pramaan.backend.insight.domain.ComplianceSnapshot;
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

    public TrendService(ComplianceSnapshotRepository snapshots, LeadershipService leadership,
                        EvidenceRecordRepository records, EvidenceLifecycleEventRepository lifecycleEvents,
                        SchedulerRunRepository runs,
                        com.pramaan.backend.evidence.EvidenceQueryService evidenceQuery, Clock clock) {
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
        return toPoint(snapshots.save(build(clock.instant())));
    }

    /** Build (without persisting) a snapshot for the given instant. Used by the seeder too. */
    public ComplianceSnapshot build(Instant at) {
        LeadershipDashboard d = leadership.dashboard();
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

    @Transactional(readOnly = true)
    public TrendReport trend() {
        List<TrendPoint> points = snapshots.findAllByOrderByTakenAtAsc().stream()
                .map(TrendService::toPoint).toList();
        // headline figure is the LIVE rollup, not the last (possibly synthetic) snapshot,
        // so Trend agrees with Compliance / Leadership / Enterprise / National.
        TrendPoint current = toPoint(build(clock.instant()));
        return new TrendReport(clock.instant(), current, points, closure(), collection());
    }

    private ClosureStats closure() {
        Map<UUID, List<EvidenceLifecycleEvent>> byEvidence = new java.util.HashMap<>();
        int approvals = 0;
        int rejections = 0;
        int resubmissions = 0;
        Map<String, Integer> approvalsByWeek = new TreeMap<>();
        for (EvidenceLifecycleEvent e : lifecycleEvents.findAll()) {
            byEvidence.computeIfAbsent(e.getEvidenceRecordId(), k -> new ArrayList<>()).add(e);
            switch (e.getAction()) {
                case "APPROVE" -> {
                    approvals++;
                    approvalsByWeek.merge(week(e.getOccurredAt()), 1, Integer::sum);
                }
                case "REJECT" -> rejections++;
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
        return new ClosureStats(approvals, rejections, resubmissions, avgDays, approvalsByWeek);
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
}
