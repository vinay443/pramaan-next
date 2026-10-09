package com.pramaan.backend.retention;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** One PREVIEW or EXECUTE call of the archival workflow. */
@Entity
@Table(name = "retention_run")
public class RetentionRun {

    @Id
    private UUID id;

    @Column(nullable = false, length = 10)
    private String mode;

    @Column(name = "triggered_by", length = 64)
    private String triggeredBy;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "retention_days", nullable = false)
    private int retentionDays;

    @Column(length = 128)
    private String application;

    @Column(nullable = false)
    private int scanned;

    @Column(nullable = false)
    private int archived;

    @Column(nullable = false)
    private int skipped;

    protected RetentionRun() {}

    public RetentionRun(String mode, String triggeredBy, Instant startedAt, Instant finishedAt, int retentionDays,
                        String application, int scanned, int archived, int skipped) {
        this.id = UUID.randomUUID();
        this.mode = mode;
        this.triggeredBy = triggeredBy;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
        this.retentionDays = retentionDays;
        this.application = application;
        this.scanned = scanned;
        this.archived = archived;
        this.skipped = skipped;
    }

    public UUID getId() { return id; }
    public String getMode() { return mode; }
    public String getTriggeredBy() { return triggeredBy; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public int getRetentionDays() { return retentionDays; }
    public String getApplication() { return application; }
    public int getScanned() { return scanned; }
    public int getArchived() { return archived; }
    public int getSkipped() { return skipped; }
}
