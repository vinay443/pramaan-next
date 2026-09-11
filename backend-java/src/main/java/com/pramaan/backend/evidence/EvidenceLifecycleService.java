package com.pramaan.backend.evidence;

import com.pramaan.backend.common.ApiException;
import com.pramaan.backend.config.PramaanProperties;
import com.pramaan.backend.evidence.EvidenceDtos.EvidenceLifecycleView;
import com.pramaan.backend.evidence.EvidenceDtos.LifecycleAction;
import com.pramaan.backend.evidence.EvidenceDtos.LifecycleEventView;
import com.pramaan.backend.evidence.EvidenceDtos.LifecycleTransitionRequest;
import com.pramaan.backend.evidence.domain.EvidenceLifecycleEvent;
import com.pramaan.backend.evidence.domain.EvidenceLifecycleState;
import com.pramaan.backend.evidence.domain.EvidenceRecord;
import com.pramaan.backend.evidence.repo.EvidenceLifecycleEventRepository;
import com.pramaan.backend.evidence.repo.EvidenceRecordRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use Case 13 — evidence lifecycle management. Owner/reviewer review states,
 * retention-based expiry, and an immutable transition audit trail. Operates on the
 * single authoritative {@link EvidenceRecord} — no separate lifecycle store.
 */
@Service
public class EvidenceLifecycleService {

    /** Allowed source states per action; the target state each action produces. */
    private static final Map<LifecycleAction, Set<EvidenceLifecycleState>> ALLOWED_FROM = Map.of(
            LifecycleAction.SUBMIT, EnumSet.of(EvidenceLifecycleState.DRAFT, EvidenceLifecycleState.REJECTED,
                    EvidenceLifecycleState.EXPIRED),
            LifecycleAction.APPROVE, EnumSet.of(EvidenceLifecycleState.SUBMITTED),
            LifecycleAction.REJECT, EnumSet.of(EvidenceLifecycleState.SUBMITTED),
            LifecycleAction.RETIRE, EnumSet.complementOf(EnumSet.of(EvidenceLifecycleState.SUPERSEDED)),
            LifecycleAction.RESET, EnumSet.allOf(EvidenceLifecycleState.class));

    private static final Map<LifecycleAction, EvidenceLifecycleState> TARGET = Map.of(
            LifecycleAction.SUBMIT, EvidenceLifecycleState.SUBMITTED,
            LifecycleAction.APPROVE, EvidenceLifecycleState.APPROVED,
            LifecycleAction.REJECT, EvidenceLifecycleState.REJECTED,
            LifecycleAction.RETIRE, EvidenceLifecycleState.SUPERSEDED,
            LifecycleAction.RESET, EvidenceLifecycleState.DRAFT);

    private final EvidenceRecordRepository records;
    private final EvidenceLifecycleEventRepository events;
    private final EvidenceApprovalAuthorizer authorizer;
    private final Clock clock;
    private final int retentionDays;

    public EvidenceLifecycleService(EvidenceRecordRepository records,
                                    EvidenceLifecycleEventRepository events,
                                    EvidenceApprovalAuthorizer authorizer,
                                    Clock clock, PramaanProperties props) {
        this.records = records;
        this.events = events;
        this.authorizer = authorizer;
        this.clock = clock;
        this.retentionDays = props.evidenceOrDefault().retentionDaysOrDefault();
    }

    /** Called by the ingestion path (the only writer of evidence). No new state machine outside this. */
    @Transactional
    public void recordIngestion(EvidenceRecord record, boolean created, Instant now) {
        if (created) {
            append(record, null, EvidenceLifecycleState.DRAFT, "INGESTED", record.getReviewedBy(),
                    "evidence ingested", now);
            return;
        }
        EvidenceLifecycleState from = record.getLifecycleState();
        if (from == EvidenceLifecycleState.DRAFT || from == EvidenceLifecycleState.SUBMITTED) {
            append(record, from, from, "NEW_VERSION", null, "new version ingested", now);
        } else {
            record.applyLifecycle(EvidenceLifecycleState.SUBMITTED, null, "superseding version ingested", now);
            append(record, from, EvidenceLifecycleState.SUBMITTED, "RE_SUBMITTED",
                    null, "new version ingested — re-review required", now);
        }
    }

    /**
     * @param userRole RBAC — the {@code X-User-Role} request header. Required for
     *     SUBMIT / APPROVE / REJECT; ignored for RETIRE / RESET.
     * @param userFramework RBAC — the optional {@code X-User-Framework} header, checked
     *     for consistency against a scoped role's own configured scope.
     */
    @Transactional
    public EvidenceLifecycleView transition(UUID evidenceId, LifecycleTransitionRequest req,
                                            String userRole, String userFramework) {
        if (req == null || req.action() == null) {
            throw ApiException.badRequest("action is required (SUBMIT | APPROVE | REJECT | RETIRE | RESET)");
        }
        EvidenceRecord r = records.findById(evidenceId)
                .orElseThrow(() -> ApiException.notFound("Unknown evidence: " + evidenceId));
        EvidenceLifecycleState from = effectiveState(r);
        if (!ALLOWED_FROM.get(req.action()).contains(from)) {
            throw ApiException.conflict("cannot " + req.action() + " evidence in state " + from);
        }
        authorizer.authorize(req.action(), r, userRole, userFramework);
        Instant now = clock.instant();
        EvidenceLifecycleState to = TARGET.get(req.action());
        r.applyLifecycle(to, req.actor(), req.note(), now);
        append(r, from, to, req.action().name(), req.actor(), req.note(), now);
        return view(r);
    }

    @Transactional(readOnly = true)
    public EvidenceLifecycleView get(UUID evidenceId) {
        return view(records.findById(evidenceId)
                .orElseThrow(() -> ApiException.notFound("Unknown evidence: " + evidenceId)));
    }

    private void append(EvidenceRecord r, EvidenceLifecycleState from, EvidenceLifecycleState to,
                        String action, String actor, String note, Instant now) {
        events.save(new EvidenceLifecycleEvent(UUID.randomUUID(), r.getId(), from, to, action, actor, note, now));
        records.save(r);
    }

    /** Persisted state, except an APPROVED record past its retention window reads as EXPIRED. */
    private EvidenceLifecycleState effectiveState(EvidenceRecord r) {
        if (r.getLifecycleState() == EvidenceLifecycleState.APPROVED && isExpired(r)) {
            return EvidenceLifecycleState.EXPIRED;
        }
        return r.getLifecycleState();
    }

    private boolean isExpired(EvidenceRecord r) {
        Instant last = r.getLatestCollectedAt();
        return last != null && ChronoUnit.DAYS.between(last, clock.instant()) > retentionDays;
    }

    private EvidenceLifecycleView view(EvidenceRecord r) {
        Instant last = r.getLatestCollectedAt();
        Integer ageDays = last == null ? null : (int) ChronoUnit.DAYS.between(last, clock.instant());
        Instant expiresAt = last == null ? null : last.plus(retentionDays, ChronoUnit.DAYS);
        EvidenceLifecycleState effective = effectiveState(r);
        List<LifecycleEventView> history = events.findByEvidenceRecordIdOrderByOccurredAtAsc(r.getId()).stream()
                .map(e -> new LifecycleEventView(
                        e.getFromState() == null ? null : e.getFromState().name(),
                        e.getToState().name(), e.getAction(), e.getActor(), e.getNote(), e.getOccurredAt()))
                .toList();
        return new EvidenceLifecycleView(r.getId().toString(), r.getApplicationSlug(), r.getControlId(),
                r.getLifecycleState().name(), effective.name(),
                effective == EvidenceLifecycleState.EXPIRED, r.getReviewedBy(), r.getReviewedAt(),
                r.getLifecycleNote(), retentionDays, ageDays, expiresAt, r.getCurrentVersion(), history);
    }
}
