package com.pramaan.backend.insight.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** A scheduled framework audit and the applications in scope for it. */
@Entity
@Table(name = "audit_schedule")
public class AuditSchedule {

    @Id
    private UUID id;

    @Column(nullable = false, length = 40)
    private String framework;

    @Column(name = "audit_name", nullable = false, length = 200)
    private String auditName;

    @Column(name = "scheduled_date", nullable = false)
    private LocalDate scheduledDate;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "audit_schedule_application", joinColumns = @JoinColumn(name = "audit_schedule_id"))
    @Column(name = "application_slug", nullable = false)
    private List<String> applicationSlugs = new ArrayList<>();

    protected AuditSchedule() {}

    public AuditSchedule(String framework, String auditName, LocalDate scheduledDate, List<String> applicationSlugs) {
        this.id = UUID.randomUUID();
        this.framework = framework;
        this.auditName = auditName;
        this.scheduledDate = scheduledDate;
        this.applicationSlugs = new ArrayList<>(applicationSlugs);
    }

    public UUID getId() { return id; }
    public String getFramework() { return framework; }
    public String getAuditName() { return auditName; }
    public LocalDate getScheduledDate() { return scheduledDate; }
    public List<String> getApplicationSlugs() { return applicationSlugs; }
}
