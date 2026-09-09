package com.pramaan.backend.evidence.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Use Case 13 — an immutable audit-trail entry for one evidence lifecycle transition. */
@Entity
@Table(name = "evidence_lifecycle_event")
public class EvidenceLifecycleEvent {

    @Id
    private UUID id;

    @Column(name = "evidence_record_id", nullable = false)
    private UUID evidenceRecordId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_state", length = 20)
    private EvidenceLifecycleState fromState;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_state", nullable = false, length = 20)
    private EvidenceLifecycleState toState;

    @Column(nullable = false, length = 40)
    private String action;

    private String actor;

    @Column(length = 2000)
    private String note;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected EvidenceLifecycleEvent() {}

    public EvidenceLifecycleEvent(UUID id, UUID evidenceRecordId, EvidenceLifecycleState fromState,
                                  EvidenceLifecycleState toState, String action, String actor,
                                  String note, Instant occurredAt) {
        this.id = id;
        this.evidenceRecordId = evidenceRecordId;
        this.fromState = fromState;
        this.toState = toState;
        this.action = action;
        this.actor = actor;
        this.note = note;
        this.occurredAt = occurredAt;
    }

    public UUID getId() { return id; }
    public UUID getEvidenceRecordId() { return evidenceRecordId; }
    public EvidenceLifecycleState getFromState() { return fromState; }
    public EvidenceLifecycleState getToState() { return toState; }
    public String getAction() { return action; }
    public String getActor() { return actor; }
    public String getNote() { return note; }
    public Instant getOccurredAt() { return occurredAt; }
}
