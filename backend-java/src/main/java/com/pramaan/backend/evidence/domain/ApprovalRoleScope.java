package com.pramaan.backend.evidence.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * RBAC for evidence approval — one row per role, defining what it may do and its
 * scope. {@code frameworkScope} / {@code controlFamilyScope} are {@code null} for an
 * unscoped role (e.g. the generic {@code AUDITOR}); a scoped role may approve/reject
 * evidence matching either dimension (an "or", not an "and" — see
 * {@code EvidenceApprovalAuthorizer}).
 */
@Entity
@Table(name = "evidence_approval_role")
public class ApprovalRoleScope {

    @Id
    private String role;

    @Column(name = "framework_scope", length = 40)
    private String frameworkScope;

    @Column(name = "control_family_scope", length = 40)
    private String controlFamilyScope;

    @Column(name = "can_submit", nullable = false)
    private boolean canSubmit;

    @Column(name = "can_approve", nullable = false)
    private boolean canApprove;

    @Column(nullable = false, length = 200)
    private String description;

    protected ApprovalRoleScope() {}

    public ApprovalRoleScope(String role, String frameworkScope, String controlFamilyScope,
                             boolean canSubmit, boolean canApprove, String description) {
        this.role = role;
        this.frameworkScope = frameworkScope;
        this.controlFamilyScope = controlFamilyScope;
        this.canSubmit = canSubmit;
        this.canApprove = canApprove;
        this.description = description;
    }

    public String getRole() { return role; }
    public String getFrameworkScope() { return frameworkScope; }
    public String getControlFamilyScope() { return controlFamilyScope; }
    public boolean isCanSubmit() { return canSubmit; }
    public boolean isCanApprove() { return canApprove; }
    public String getDescription() { return description; }

    /** True when this role has no framework/control-family restriction (e.g. generic AUDITOR). */
    public boolean isUnscoped() {
        return frameworkScope == null && controlFamilyScope == null;
    }
}
