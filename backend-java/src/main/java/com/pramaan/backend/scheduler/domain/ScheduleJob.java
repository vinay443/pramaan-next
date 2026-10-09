package com.pramaan.backend.scheduler.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "schedule_job")
public class ScheduleJob {

    public enum Frequency {
        HOURLY(Duration.ofHours(1)), DAILY(Duration.ofDays(1)), WEEKLY(Duration.ofDays(7)),
        /** Testing aid: lets a tester observe a due run within about a minute. */
        MINUTELY(Duration.ofMinutes(1));

        private final Duration interval;

        Frequency(Duration interval) { this.interval = interval; }

        public Instant after(Instant from) { return from.plus(interval); }
    }

    @Id
    private UUID id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "source_system", nullable = false)
    private String sourceSystem;

    @Column(nullable = false)
    private String application;

    @Column(name = "evidence_type", nullable = false)
    private String evidenceType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Frequency frequency;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "next_run_at")
    private Instant nextRunAt;

    @Column(name = "last_run_at")
    private Instant lastRunAt;

    /** Optimistic lock: a concurrent change by another user surfaces as 409 CONFLICT. */
    @Version
    private Long version;

    protected ScheduleJob() {}

    public ScheduleJob(String name, String sourceSystem, String application, String evidenceType,
                       Frequency frequency, String createdBy, Instant now) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.sourceSystem = sourceSystem;
        this.application = application;
        this.evidenceType = evidenceType;
        this.frequency = frequency;
        this.createdBy = createdBy;
        this.createdAt = now;
        activate(now);
    }

    public void activate(Instant now) {
        this.active = true;
        this.nextRunAt = frequency.after(now);
    }

    public void deactivate() {
        this.active = false;
        this.nextRunAt = null;
    }

    public void ran(Instant now) {
        this.lastRunAt = now;
        this.nextRunAt = frequency.after(now);
    }

    public void setLastRunAt(Instant at) { this.lastRunAt = at; }

    public Long getVersion() { return version; }
    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getSourceSystem() { return sourceSystem; }
    public String getApplication() { return application; }
    public String getEvidenceType() { return evidenceType; }
    public Frequency getFrequency() { return frequency; }
    public boolean isActive() { return active; }
    public String getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getNextRunAt() { return nextRunAt; }
    public Instant getLastRunAt() { return lastRunAt; }
}
