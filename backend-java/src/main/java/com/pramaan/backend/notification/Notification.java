package com.pramaan.backend.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notification", uniqueConstraints = @jakarta.persistence.UniqueConstraint(
        name = "ux_notification_run_role_channel", columnNames = {"run_id", "recipient_role", "channel"}))
public class Notification {

    public enum Type { RUN_COMPLETED, RUN_PARTIAL, RUN_FAILED }

    public enum Severity { INFO, WARNING, ERROR }

    public enum Channel { IN_APP, EMAIL_SIMULATED }

    @Id
    private UUID id;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Type type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Severity severity;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(nullable = false, length = 2000)
    private String message;

    @Column(name = "run_id")
    private UUID runId;

    @Column(name = "schedule_name", length = 200)
    private String scheduleName;

    @Column(name = "recipient_role", nullable = false, length = 20)
    private String recipientRole;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Channel channel;

    @Column(name = "read_at")
    private Instant readAt;

    protected Notification() {}

    public Notification(Type type, Severity severity, String title, String message, UUID runId,
                        String scheduleName, String recipientRole, Channel channel, Instant now, Instant readAt) {
        this.id = UUID.randomUUID();
        this.createdAt = now;
        this.type = type;
        this.severity = severity;
        this.title = title;
        this.message = message;
        this.runId = runId;
        this.scheduleName = scheduleName;
        this.recipientRole = recipientRole;
        this.channel = channel;
        this.readAt = readAt;
    }

    public void markRead(Instant now) {
        if (readAt == null) readAt = now;
    }

    public UUID getId() { return id; }
    public Instant getCreatedAt() { return createdAt; }
    public Type getType() { return type; }
    public Severity getSeverity() { return severity; }
    public String getTitle() { return title; }
    public String getMessage() { return message; }
    public UUID getRunId() { return runId; }
    public String getScheduleName() { return scheduleName; }
    public String getRecipientRole() { return recipientRole; }
    public Channel getChannel() { return channel; }
    public Instant getReadAt() { return readAt; }
}
