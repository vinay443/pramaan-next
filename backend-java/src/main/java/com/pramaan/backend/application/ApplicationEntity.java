package com.pramaan.backend.application;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "applications")
public class ApplicationEntity {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(nullable = false)
    private String name;

    private String businessUnit;

    @Column(nullable = false)
    private String criticality = "MEDIUM";

    private String owner;

    /** Comma-separated technology stack. */
    private String technology;

    @Column(nullable = false)
    private boolean autoCreated = false;

    /** Onboarded (true) vs deboarded (false). Deboarding stops new scheduled
     *  collection but retains the row and its evidence. */
    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected ApplicationEntity() {}

    public ApplicationEntity(UUID id, String slug, String name, Instant now) {
        this.id = id;
        this.slug = slug;
        this.name = name;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public String getSlug() { return slug; }
    public String getName() { return name; }
    public void setName(String v) { this.name = v; }
    public String getBusinessUnit() { return businessUnit; }
    public void setBusinessUnit(String v) { this.businessUnit = v; }
    public String getCriticality() { return criticality; }
    public void setCriticality(String v) { this.criticality = v; }
    public String getOwner() { return owner; }
    public void setOwner(String v) { this.owner = v; }
    public String getTechnology() { return technology; }
    public void setTechnology(String v) { this.technology = v; }
    public boolean isAutoCreated() { return autoCreated; }
    public void setAutoCreated(boolean v) { this.autoCreated = v; }
    public boolean isActive() { return active; }
    public void setActive(boolean v) { this.active = v; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void touch(Instant now) { this.updatedAt = now; }
}
