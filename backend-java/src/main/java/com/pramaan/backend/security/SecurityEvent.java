package com.pramaan.backend.security;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** A request the system blocked. Outcome is always BLOCKED; values are sanitized and truncated before storage. */
@Entity
@Table(name = "security_event")
public class SecurityEvent {

    public enum Type { ACCESS_DENIED, UNKNOWN_ROLE, INPUT_REJECTED, INVALID_IDENTIFIER, UPLOAD_REJECTED }

    @Id
    private UUID id;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private Type eventType;

    @Column(nullable = false, length = 10)
    private String outcome = "BLOCKED";

    @Column(length = 64)
    private String role;

    @Column(length = 64)
    private String actor;

    @Column(length = 10)
    private String method;

    @Column(length = 300)
    private String path;

    @Column(length = 300)
    private String detail;

    @Column(name = "remote_addr", length = 64)
    private String remoteAddr;

    protected SecurityEvent() {}

    public SecurityEvent(Instant occurredAt, Type eventType, String role, String actor, String method,
                         String path, String detail, String remoteAddr) {
        this.id = UUID.randomUUID();
        this.occurredAt = occurredAt;
        this.eventType = eventType;
        this.role = role;
        this.actor = actor;
        this.method = method;
        this.path = path;
        this.detail = detail;
        this.remoteAddr = remoteAddr;
    }

    public UUID getId() { return id; }
    public Instant getOccurredAt() { return occurredAt; }
    public Type getEventType() { return eventType; }
    public String getOutcome() { return outcome; }
    public String getRole() { return role; }
    public String getActor() { return actor; }
    public String getMethod() { return method; }
    public String getPath() { return path; }
    public String getDetail() { return detail; }
    public String getRemoteAddr() { return remoteAddr; }
}
