package com.pramaan.backend.evidence.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * A demo persona for the evidence-approval RBAC roles, surfaced through the
 * existing ECS Admin ("Users & Roles") API alongside {@code AdminService}'s
 * in-memory registry — no credentials, no login. Real identity for a request is
 * simulated via the {@code X-User-Role} header, not a lookup against this table.
 */
@Entity
@Table(name = "evidence_approval_user")
public class ApprovalUser {

    @Id
    private String username;

    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    @Column(nullable = false, length = 40)
    private String role;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ApprovalUser() {}

    public ApprovalUser(String username, String displayName, String role, Instant createdAt) {
        this.username = username;
        this.displayName = displayName;
        this.role = role;
        this.createdAt = createdAt;
    }

    public String getUsername() { return username; }
    public String getDisplayName() { return displayName; }
    public String getRole() { return role; }
    public Instant getCreatedAt() { return createdAt; }
}
