package com.pramaan.backend.retention;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

/** The single retention policy row (id = 1). No row means the configured default applies. */
@Entity
@Table(name = "retention_policy")
public class RetentionPolicy {

    static final int ID = 1;

    @Id
    private int id = ID;

    @Column(name = "retention_days", nullable = false)
    private int retentionDays;

    @Column(name = "updated_by", length = 64)
    private String updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    protected RetentionPolicy() {}

    public RetentionPolicy(int retentionDays, String updatedBy, Instant now) {
        update(retentionDays, updatedBy, now);
    }

    public void update(int retentionDays, String updatedBy, Instant now) {
        this.retentionDays = retentionDays;
        this.updatedBy = updatedBy;
        this.updatedAt = now;
    }

    public int getRetentionDays() { return retentionDays; }
    public String getUpdatedBy() { return updatedBy; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Long getVersion() { return version; }
}
