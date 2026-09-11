package com.pramaan.backend.evidence;

import com.pramaan.backend.common.ApiException;
import com.pramaan.backend.evidence.EvidenceDtos.LifecycleAction;
import com.pramaan.backend.evidence.domain.ApprovalRoleScope;
import com.pramaan.backend.evidence.domain.EvidenceRecord;
import com.pramaan.backend.evidence.repo.ApprovalRoleScopeRepository;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * RBAC for evidence approval. Identity is simulated (no login) via the
 * {@code X-User-Role} request header, optionally paired with {@code X-User-Framework}
 * for scoped roles — see {@link com.pramaan.backend.evidence.EvidenceController}.
 *
 * <p>Only {@code SUBMIT} / {@code APPROVE} / {@code REJECT} are gated here.
 * {@code RETIRE} / {@code RESET} are unchanged — out of scope for this RBAC pass.
 */
@Component
public class EvidenceApprovalAuthorizer {

    private final ApprovalRoleScopeRepository roleScopes;

    public EvidenceApprovalAuthorizer(ApprovalRoleScopeRepository roleScopes) {
        this.roleScopes = roleScopes;
    }

    public void authorize(LifecycleAction action, EvidenceRecord record, String userRole, String userFramework) {
        if (action != LifecycleAction.SUBMIT && action != LifecycleAction.APPROVE
                && action != LifecycleAction.REJECT) {
            return;
        }
        if (userRole == null || userRole.isBlank()) {
            throw ApiException.badRequest(
                    "X-User-Role header is required to " + action + " evidence");
        }
        String roleName = userRole.trim().toUpperCase(Locale.ROOT);
        ApprovalRoleScope scope = roleScopes.findById(roleName)
                .orElseThrow(() -> ApiException.badRequest(
                        "unknown role '" + userRole + "' (X-User-Role)"));

        // A scoped role may declare its framework via X-User-Framework; if it does,
        // it must agree with the role's own configured scope (a client-side sanity
        // check, not the source of truth — the role's scope always governs).
        if (userFramework != null && !userFramework.isBlank() && scope.getFrameworkScope() != null
                && !scope.getFrameworkScope().equalsIgnoreCase(userFramework.trim())) {
            throw ApiException.badRequest("X-User-Framework '" + userFramework + "' does not match role "
                    + roleName + "'s scope (" + scope.getFrameworkScope() + ")");
        }

        if (action == LifecycleAction.SUBMIT) {
            if (!scope.isCanSubmit()) {
                throw ApiException.forbidden(roleName + " is not authorized to submit evidence");
            }
            return;
        }

        // APPROVE / REJECT
        if (!scope.isCanApprove()) {
            throw ApiException.forbidden(roleName + " is not authorized to approve or reject evidence");
        }
        if (!inScope(scope, record)) {
            throw ApiException.forbidden(roleName + " is out of scope for framework '" + record.getFramework()
                    + "', control '" + record.getControlId() + "'");
        }
    }

    private static boolean inScope(ApprovalRoleScope scope, EvidenceRecord record) {
        if (scope.isUnscoped()) {
            return true; // e.g. the generic AUDITOR
        }
        boolean frameworkMatch = scope.getFrameworkScope() != null
                && scope.getFrameworkScope().equalsIgnoreCase(record.getFramework());
        boolean controlFamilyMatch = "VAPT".equalsIgnoreCase(scope.getControlFamilyScope())
                && record.getControlId() != null
                && record.getControlId().toUpperCase(Locale.ROOT).startsWith("VAPT");
        return frameworkMatch || controlFamilyMatch;
    }
}
