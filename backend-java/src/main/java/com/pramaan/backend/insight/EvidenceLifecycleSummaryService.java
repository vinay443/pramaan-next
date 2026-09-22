package com.pramaan.backend.insight;

import com.pramaan.backend.config.PramaanProperties;
import com.pramaan.backend.evidence.domain.EvidenceLifecycleEvent;
import com.pramaan.backend.evidence.domain.EvidenceLifecycleState;
import com.pramaan.backend.evidence.domain.EvidenceRecord;
import com.pramaan.backend.evidence.repo.EvidenceLifecycleEventRepository;
import com.pramaan.backend.evidence.repo.EvidenceRecordRepository;
import com.pramaan.backend.insight.InsightDtos.AuditorSla;
import com.pramaan.backend.insight.InsightDtos.EvidenceLifecycleSummary;
import com.pramaan.backend.insight.InsightDtos.LifecycleStateCounts;
import com.pramaan.backend.insight.InsightDtos.PendingAging;
import com.pramaan.backend.insight.InsightDtos.RejectionAuditRow;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * App Owner dashboard — Evidence lifecycle counts, rejection audit trail and computed
 * Auditor SLA / pending-aging metrics, scoped to one application. There is no existing
 * bulk/aggregate backend source for these (see {@code EvidenceLifecycleEvent}, per-record
 * only), so this joins {@link EvidenceRecord} -> {@link EvidenceLifecycleEvent} by
 * {@code applicationSlug} in Java, following the same join style as {@link TrendService}
 * and {@link LeadershipService}.
 */
@Service
public class EvidenceLifecycleSummaryService {

    private final EvidenceRecordRepository records;
    private final EvidenceLifecycleEventRepository lifecycleEvents;
    private final Clock clock;
    private final PramaanProperties props;

    public EvidenceLifecycleSummaryService(EvidenceRecordRepository records,
                                           EvidenceLifecycleEventRepository lifecycleEvents, Clock clock,
                                           PramaanProperties props) {
        this.records = records;
        this.lifecycleEvents = lifecycleEvents;
        this.clock = clock;
        this.props = props;
    }

    @Transactional(readOnly = true)
    public EvidenceLifecycleSummary forApplication(String applicationSlug) {
        List<EvidenceRecord> appRecords = records.findByApplicationSlug(applicationSlug);
        Map<UUID, EvidenceRecord> byId = appRecords.stream()
                .collect(Collectors.toMap(EvidenceRecord::getId, r -> r));

        List<EvidenceLifecycleEvent> events = byId.isEmpty() ? List.of()
                : lifecycleEvents.findByEvidenceRecordIdIn(byId.keySet());
        Map<UUID, List<EvidenceLifecycleEvent>> eventsByRecord = events.stream()
                .collect(Collectors.groupingBy(EvidenceLifecycleEvent::getEvidenceRecordId));

        List<RejectionAuditRow> rejections = events.stream()
                .filter(e -> "REJECT".equals(e.getAction()))
                .sorted(Comparator.comparing(EvidenceLifecycleEvent::getOccurredAt).reversed())
                .map(e -> toRejectionRow(e, byId.get(e.getEvidenceRecordId())))
                .filter(java.util.Objects::nonNull)
                .toList();

        return new EvidenceLifecycleSummary(applicationSlug, clock.instant(), counts(appRecords), rejections,
                auditorSla(appRecords, eventsByRecord), pendingAging(appRecords));
    }

    private static RejectionAuditRow toRejectionRow(EvidenceLifecycleEvent e, EvidenceRecord r) {
        if (r == null) {
            return null;
        }
        return new RejectionAuditRow(r.getId().toString(), r.getApplicationSlug(), r.getFramework(),
                r.getControlId(), e.getNote(), e.getActor(), e.getOccurredAt(), displayState(r.getLifecycleState()));
    }

    private static LifecycleStateCounts counts(List<EvidenceRecord> recs) {
        int draft = 0;
        int submitted = 0;
        int approved = 0;
        int rejected = 0;
        int expired = 0;
        int superseded = 0;
        for (EvidenceRecord r : recs) {
            switch (r.getLifecycleState()) {
                case DRAFT -> draft++;
                case SUBMITTED -> submitted++;
                case APPROVED -> approved++;
                case REJECTED -> rejected++;
                case EXPIRED -> expired++;
                case SUPERSEDED -> superseded++;
            }
        }
        return new LifecycleStateCounts(draft, submitted, approved, rejected, expired, superseded);
    }

    /** Computed metric: % of reviewed evidence (approved or rejected) reviewed within the configured target. */
    private AuditorSla auditorSla(List<EvidenceRecord> recs, Map<UUID, List<EvidenceLifecycleEvent>> eventsByRecord) {
        int targetDays = props.evidenceOrDefault().auditorSlaDaysOrDefault();
        int totalReviewed = 0;
        int withinTarget = 0;
        for (EvidenceRecord r : recs) {
            if (r.getReviewedAt() == null) {
                continue;
            }
            Instant submittedAt = eventsByRecord.getOrDefault(r.getId(), List.of()).stream()
                    .filter(e -> e.getToState() == EvidenceLifecycleState.SUBMITTED)
                    .max(Comparator.comparing(EvidenceLifecycleEvent::getOccurredAt))
                    .map(EvidenceLifecycleEvent::getOccurredAt)
                    .orElse(null);
            if (submittedAt == null) {
                continue;
            }
            totalReviewed++;
            long days = ChronoUnit.SECONDS.between(submittedAt, r.getReviewedAt()) / 86_400L;
            if (days <= targetDays) {
                withinTarget++;
            }
        }
        Double pct = totalReviewed == 0 ? null : Math.round(withinTarget * 1000.0 / totalReviewed) / 10.0;
        return new AuditorSla(withinTarget, totalReviewed, pct, targetDays);
    }

    /** Computed metric: DRAFT/SUBMITTED evidence aging in the review queue, using each record's last update. */
    private PendingAging pendingAging(List<EvidenceRecord> recs) {
        Instant now = clock.instant();
        List<Long> ages = new ArrayList<>();
        for (EvidenceRecord r : recs) {
            if (r.getLifecycleState() == EvidenceLifecycleState.DRAFT
                    || r.getLifecycleState() == EvidenceLifecycleState.SUBMITTED) {
                ages.add(ChronoUnit.SECONDS.between(r.getUpdatedAt(), now) / 86_400L);
            }
        }
        Double avg = ages.isEmpty() ? null
                : Math.round(ages.stream().mapToLong(Long::longValue).average().orElse(0) * 10.0) / 10.0;
        return new PendingAging(ages.size(), avg);
    }

    private static String displayState(EvidenceLifecycleState s) {
        return switch (s) {
            case DRAFT -> "Draft";
            case SUBMITTED -> "Submitted";
            case REJECTED -> "Re-upload Requested";
            case APPROVED, EXPIRED, SUPERSEDED -> "Closed";
        };
    }
}
