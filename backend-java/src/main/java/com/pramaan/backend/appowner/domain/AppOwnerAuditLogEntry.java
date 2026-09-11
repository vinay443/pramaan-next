package com.pramaan.backend.appowner.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * General "who did what when" trail for App-Owner actions: evidence uploaded /
 * resubmitted, TD shared / updated, status changes. Distinct from
 * {@code evidence_lifecycle_event} (lifecycle-state transitions only).
 */
@Entity
@Table(name = "app_owner_audit_log")
public class AppOwnerAuditLogEntry {

    @Id
    private UUID id;

    @Column(name = "actor_username", nullable = false, length = 64)
    private String actorUsername;

    @Column(nullable = false, length = 60)
    private String action;

    @Column(name = "entity_type", nullable = false, length = 40)
    private String entityType;

    @Column(name = "entity_id", nullable = false, length = 80)
    private String entityId;

    @Column(name = "old_value", length = 1000)
    private String oldValue;

    @Column(name = "new_value", length = 1000)
    private String newValue;

    @Column(length = 1000)
    private String reason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AppOwnerAuditLogEntry() {}

    public AppOwnerAuditLogEntry(UUID id, String actorUsername, String action, String entityType,
                                 String entityId, String oldValue, String newValue, String reason,
                                 Instant createdAt) {
        this.id = id;
        this.actorUsername = actorUsername;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.reason = reason;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public String getActorUsername() { return actorUsername; }
    public String getAction() { return action; }
    public String getEntityType() { return entityType; }
    public String getEntityId() { return entityId; }
    public String getOldValue() { return oldValue; }
    public String getNewValue() { return newValue; }
    public String getReason() { return reason; }
    public Instant getCreatedAt() { return createdAt; }
}
