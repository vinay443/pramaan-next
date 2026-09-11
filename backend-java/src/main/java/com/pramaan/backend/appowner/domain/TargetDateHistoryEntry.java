package com.pramaan.backend.appowner.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Who/when/old→new/reason for every Target Date share or update. Never overwritten. */
@Entity
@Table(name = "target_date_history")
public class TargetDateHistoryEntry {

    @Id
    private UUID id;

    @Column(name = "evidence_record_id", nullable = false)
    private UUID evidenceRecordId;

    @Column(name = "old_target_date")
    private LocalDate oldTargetDate;

    @Column(name = "new_target_date", nullable = false)
    private LocalDate newTargetDate;

    @Column(length = 1000)
    private String reason;

    @Column(name = "actor_username", nullable = false, length = 64)
    private String actorUsername;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected TargetDateHistoryEntry() {}

    public TargetDateHistoryEntry(UUID id, UUID evidenceRecordId, LocalDate oldTargetDate,
                                  LocalDate newTargetDate, String reason, String actorUsername, Instant createdAt) {
        this.id = id;
        this.evidenceRecordId = evidenceRecordId;
        this.oldTargetDate = oldTargetDate;
        this.newTargetDate = newTargetDate;
        this.reason = reason;
        this.actorUsername = actorUsername;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public UUID getEvidenceRecordId() { return evidenceRecordId; }
    public LocalDate getOldTargetDate() { return oldTargetDate; }
    public LocalDate getNewTargetDate() { return newTargetDate; }
    public String getReason() { return reason; }
    public String getActorUsername() { return actorUsername; }
    public Instant getCreatedAt() { return createdAt; }
}
