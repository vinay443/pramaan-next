package com.pramaan.backend.integrations;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Simulated credential state of a source system. No row means VALID. */
@Entity
@Table(name = "source_connection")
public class SourceConnection {

    public enum Status { VALID, INVALID, DISABLED }

    @Id
    @Column(name = "source_system")
    private String sourceSystem;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by")
    private String updatedBy;

    protected SourceConnection() {}

    public SourceConnection(String sourceSystem, Status status, String updatedBy, Instant now) {
        this.sourceSystem = sourceSystem;
        update(status, updatedBy, now);
    }

    public void update(Status status, String updatedBy, Instant now) {
        this.status = status;
        this.updatedBy = updatedBy;
        this.updatedAt = now;
    }

    public String getSourceSystem() { return sourceSystem; }
    public Status getStatus() { return status; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getUpdatedBy() { return updatedBy; }
}
