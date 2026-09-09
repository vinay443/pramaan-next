package com.pramaan.backend.scheduler.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "scheduler_run")
public class SchedulerRun {

    public enum Trigger { MANUAL, SCHEDULED, BULK }

    public enum Status { PENDING, RUNNING, COMPLETED, FAILED }

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false)
    private Trigger trigger;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.PENDING;

    @Column(name = "requested_by")
    private String requestedBy;

    private String applications;
    private String frameworks;
    private String sources;

    private int received;
    private int ingested;
    private int duplicates;
    private int failed;

    @Column(length = 2000)
    private String message;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "scheduler_run_source", joinColumns = @JoinColumn(name = "scheduler_run_id"))
    @MapKeyColumn(name = "source_key")
    @Column(name = "source_summary", length = 600)
    private Map<String, String> sourceSummary = new LinkedHashMap<>();

    protected SchedulerRun() {}

    public SchedulerRun(UUID id, Trigger trigger, String requestedBy, List<String> applications,
                        List<String> frameworks, List<String> sources, Instant now) {
        this.id = id;
        this.trigger = trigger;
        this.requestedBy = requestedBy;
        this.applications = csv(applications);
        this.frameworks = csv(frameworks);
        this.sources = csv(sources);
        this.createdAt = now;
    }

    public void markRunning(Instant now) {
        this.status = Status.RUNNING;
        this.startedAt = now;
    }

    public void complete(Instant now, int received, int ingested, int duplicates, int failed,
                         Map<String, String> perSource) {
        this.received = received;
        this.ingested = ingested;
        this.duplicates = duplicates;
        this.failed = failed;
        this.sourceSummary = new LinkedHashMap<>(perSource);
        this.status = failed > 0 && ingested == 0 && duplicates == 0 ? Status.FAILED : Status.COMPLETED;
        this.finishedAt = now;
        this.message = "%d received, %d ingested, %d duplicates, %d failed"
                .formatted(received, ingested, duplicates, failed);
    }

    public void fail(Instant now, String reason) {
        this.status = Status.FAILED;
        this.finishedAt = now;
        this.message = reason;
    }

    private static String csv(List<String> v) {
        return v == null || v.isEmpty() ? null : String.join(",", v);
    }

    public UUID getId() { return id; }
    public Trigger getTrigger() { return trigger; }
    public Status getStatus() { return status; }
    public String getRequestedBy() { return requestedBy; }
    public String getApplications() { return applications; }
    public String getFrameworks() { return frameworks; }
    public String getSources() { return sources; }
    public int getReceived() { return received; }
    public int getIngested() { return ingested; }
    public int getDuplicates() { return duplicates; }
    public int getFailed() { return failed; }
    public String getMessage() { return message; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public Map<String, String> getSourceSummary() { return sourceSummary; }
}
