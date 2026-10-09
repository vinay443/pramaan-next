package com.pramaan.backend.observation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** An auditor's observation on one evidence record; control / framework / application are copied at creation. */
@Entity
@Table(name = "audit_observation")
public class AuditObservation {

    public enum Status { OPEN, CLOSED }

    @Id
    private UUID id;

    @Column(name = "evidence_id", nullable = false)
    private UUID evidenceId;

    @Column(name = "control_id", nullable = false, length = 120)
    private String controlId;

    @Column(nullable = false, length = 120)
    private String framework;

    @Column(nullable = false, length = 128)
    private String application;

    @Column(nullable = false, length = 1000)
    private String text;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.OPEN;

    @Column(name = "raised_by_role", nullable = false, length = 20)
    private String raisedByRole;

    @Column(name = "raised_by", length = 64)
    private String raisedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    protected AuditObservation() {}

    public AuditObservation(UUID evidenceId, String controlId, String framework, String application, String text,
                            String raisedByRole, String raisedBy, Instant now) {
        this.id = UUID.randomUUID();
        this.evidenceId = evidenceId;
        this.controlId = controlId;
        this.framework = framework;
        this.application = application;
        this.text = text;
        this.raisedByRole = raisedByRole;
        this.raisedBy = raisedBy;
        this.createdAt = now;
    }

    public void close(Instant now) {
        if (status == Status.OPEN) {
            status = Status.CLOSED;
            closedAt = now;
        }
    }

    public UUID getId() { return id; }
    public UUID getEvidenceId() { return evidenceId; }
    public String getControlId() { return controlId; }
    public String getFramework() { return framework; }
    public String getApplication() { return application; }
    public String getText() { return text; }
    public Status getStatus() { return status; }
    public String getRaisedByRole() { return raisedByRole; }
    public String getRaisedBy() { return raisedBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getClosedAt() { return closedAt; }
}
