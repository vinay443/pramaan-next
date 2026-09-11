package com.pramaan.backend.appowner.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Which App Owner persona is authorized for which application. Additive — new table only. */
@Entity
@Table(name = "application_owner")
public class ApplicationOwner {

    @Id
    private UUID id;

    @Column(name = "application_slug", nullable = false, length = 120)
    private String applicationSlug;

    @Column(nullable = false, length = 64)
    private String username;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ApplicationOwner() {}

    public ApplicationOwner(UUID id, String applicationSlug, String username, Instant createdAt) {
        this.id = id;
        this.applicationSlug = applicationSlug;
        this.username = username;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public String getApplicationSlug() { return applicationSlug; }
    public String getUsername() { return username; }
    public Instant getCreatedAt() { return createdAt; }
}
