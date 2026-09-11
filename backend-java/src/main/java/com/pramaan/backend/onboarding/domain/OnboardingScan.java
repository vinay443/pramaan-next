package com.pramaan.backend.onboarding.domain;

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

/**
 * One run of the onboarding stepper: register application, resolve frameworks/controls,
 * validate evidence sources, trigger baseline collection, compute initial completeness/
 * compliance. Tracks per-phase status so a UI can poll and render a stepper — same
 * async-tracked shape as {@code SchedulerRun}.
 */
@Entity
@Table(name = "onboarding_scan")
public class OnboardingScan {

    public enum Status { PENDING, RUNNING, COMPLETED, FAILED }

    /** Fixed, ordered phase list — the UI stepper renders in this order. */
    public enum Phase {
        REGISTER_APPLICATION,
        RESOLVE_FRAMEWORKS_CONTROLS,
        VALIDATE_EVIDENCE_SOURCES,
        TRIGGER_BASELINE_COLLECTION,
        COMPUTE_INITIAL_POSTURE
    }

    public enum PhaseStatus { PENDING, RUNNING, COMPLETED, FAILED }

    @Id
    private UUID id;

    @Column(name = "application_slug", nullable = false)
    private String applicationSlug;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.PENDING;

    @Column(name = "requested_by")
    private String requestedBy;

    private String frameworks;
    private String sources;

    @Column(name = "current_phase")
    private String currentPhase;

    @Column(name = "scheduler_run_id")
    private String schedulerRunId;

    @Column(name = "completeness_pct")
    private Double completenessPct;

    @Column(name = "compliance_pct")
    private Double compliancePct;

    @Column(length = 2000)
    private String message;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    /** phase name -> "STATUS|message". */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "onboarding_scan_phase", joinColumns = @JoinColumn(name = "onboarding_scan_id"))
    @MapKeyColumn(name = "phase_key")
    @Column(name = "phase_status", length = 600)
    private Map<String, String> phaseSummary = new LinkedHashMap<>();

    protected OnboardingScan() {}

    public OnboardingScan(UUID id, String applicationSlug, String requestedBy, List<String> frameworks,
                          List<String> sources, Instant now) {
        this.id = id;
        this.applicationSlug = applicationSlug;
        this.requestedBy = requestedBy;
        this.frameworks = csv(frameworks);
        this.sources = csv(sources);
        this.createdAt = now;
    }

    public void markRunning(Instant now) {
        this.status = Status.RUNNING;
        this.startedAt = now;
    }

    public void phaseStarted(Phase phase) {
        this.currentPhase = phase.name();
        this.phaseSummary.put(phase.name(), PhaseStatus.RUNNING + "|");
    }

    public void phaseCompleted(Phase phase, String message) {
        this.phaseSummary.put(phase.name(), PhaseStatus.COMPLETED + "|" + (message == null ? "" : message));
    }

    public void phaseFailed(Phase phase, String message) {
        this.phaseSummary.put(phase.name(), PhaseStatus.FAILED + "|" + (message == null ? "" : message));
    }

    public void complete(Instant now, String message) {
        this.status = Status.COMPLETED;
        this.finishedAt = now;
        this.message = message;
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
    public String getApplicationSlug() { return applicationSlug; }
    public Status getStatus() { return status; }
    public String getRequestedBy() { return requestedBy; }
    public String getFrameworks() { return frameworks; }
    public String getSources() { return sources; }
    public String getCurrentPhase() { return currentPhase; }
    public String getSchedulerRunId() { return schedulerRunId; }
    public void setSchedulerRunId(String v) { this.schedulerRunId = v; }
    public Double getCompletenessPct() { return completenessPct; }
    public void setCompletenessPct(Double v) { this.completenessPct = v; }
    public Double getCompliancePct() { return compliancePct; }
    public void setCompliancePct(Double v) { this.compliancePct = v; }
    public String getMessage() { return message; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public Map<String, String> getPhaseSummary() { return phaseSummary; }
}
