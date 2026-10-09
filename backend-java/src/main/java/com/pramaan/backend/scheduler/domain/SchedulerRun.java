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

    public enum Status { PENDING, RUNNING, COMPLETED, PARTIAL, FAILED }

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

    @Column(name = "schedule_id")
    private UUID scheduleId;

    @Column(name = "triggered_by", length = 160)
    private String triggeredBy;

    @Column(name = "schedule_frequency", length = 20)
    private String scheduleFrequency;

    @Column(name = "schedule_created_by", length = 160)
    private String scheduleCreatedBy;

    @Column(name = "retry_of_run_id")
    private UUID retryOfRunId;

    @Column(name = "schedule_name", length = 200)
    private String scheduleName;

    /** Set by a schedule job; overrides the evidenceType tag on everything this run ingests. */
    @Column(name = "evidence_type", length = 120)
    private String evidenceType;

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

    public void attachSchedule(UUID scheduleId, String scheduleName, String evidenceType,
                               String frequency, String createdBy) {
        this.scheduleId = scheduleId;
        this.scheduleName = scheduleName;
        this.evidenceType = evidenceType;
        this.scheduleFrequency = frequency;
        this.scheduleCreatedBy = createdBy;
    }

    public void setTriggeredBy(String triggeredBy) {
        this.triggeredBy = triggeredBy;
    }

    public void markRetryOf(UUID previousRunId) {
        this.retryOfRunId = previousRunId;
    }

    public void markRunning(Instant now) {
        this.status = Status.RUNNING;
        this.startedAt = now;
    }

    public void complete(Instant now, int received, int ingested, int duplicates, int failed,
                         Map<String, String> perSource, int sourcesFailed, int sourcesTotal) {
        this.received = received;
        this.ingested = ingested;
        this.duplicates = duplicates;
        this.failed = failed;
        this.sourceSummary = new LinkedHashMap<>(perSource);
        // Never COMPLETED when a source failed: all sources failed -> FAILED, some -> PARTIAL.
        if (sourcesFailed > 0) {
            this.status = sourcesFailed >= sourcesTotal ? Status.FAILED : Status.PARTIAL;
        } else {
            this.status = failed > 0 && ingested == 0 && duplicates == 0 ? Status.FAILED : Status.COMPLETED;
        }
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
    public String getTriggeredBy() { return triggeredBy; }
    public String getScheduleFrequency() { return scheduleFrequency; }
    public String getScheduleCreatedBy() { return scheduleCreatedBy; }
    public UUID getRetryOfRunId() { return retryOfRunId; }
    public UUID getScheduleId() { return scheduleId; }
    public String getScheduleName() { return scheduleName; }
    public String getEvidenceType() { return evidenceType; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public Map<String, String> getSourceSummary() { return sourceSummary; }
}
