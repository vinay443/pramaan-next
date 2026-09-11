package com.pramaan.backend.appowner.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * View-only for the App Owner persona in this phase — nothing writes an
 * acknowledgement / read receipt yet. Types: EVIDENCE_REJECTED, TD_REQUIRED,
 * TD_APPROACHING, ITEM_OVERDUE, EVIDENCE_APPROVED.
 */
@Entity
@Table(name = "app_owner_notification")
public class AppOwnerNotification {

    @Id
    private UUID id;

    @Column(nullable = false, length = 64)
    private String username;

    @Column(nullable = false, length = 40)
    private String type;

    @Column(nullable = false, length = 500)
    private String message;

    @Column(name = "evidence_record_id")
    private UUID evidenceRecordId;

    @Column(name = "application_slug", length = 120)
    private String applicationSlug;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "read_at")
    private Instant readAt;

    protected AppOwnerNotification() {}

    public AppOwnerNotification(UUID id, String username, String type, String message,
                                UUID evidenceRecordId, String applicationSlug, Instant createdAt) {
        this.id = id;
        this.username = username;
        this.type = type;
        this.message = message;
        this.evidenceRecordId = evidenceRecordId;
        this.applicationSlug = applicationSlug;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public String getUsername() { return username; }
    public String getType() { return type; }
    public String getMessage() { return message; }
    public UUID getEvidenceRecordId() { return evidenceRecordId; }
    public String getApplicationSlug() { return applicationSlug; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getReadAt() { return readAt; }
}
