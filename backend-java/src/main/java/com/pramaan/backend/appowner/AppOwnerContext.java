package com.pramaan.backend.appowner;

import com.pramaan.backend.appowner.repo.ApplicationOwnerRepository;
import com.pramaan.backend.common.ApiException;
import com.pramaan.backend.evidence.domain.ApprovalUser;
import com.pramaan.backend.evidence.repo.ApprovalUserRepository;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Identity + application scope for App-Owner-facing endpoints. There is no login
 * anywhere in this app (see {@code EvidenceApprovalAuthorizer}) — identity is the
 * same simulated {@code X-User-Role} / {@code X-User-Username} header pair already
 * used for evidence-approval RBAC, extended here with a username so ownership can
 * be checked server-side rather than trusted from the client.
 *
 * <p>Every App-Owner endpoint calls {@link #resolve} first and then
 * {@link AppOwnerIdentity#requireOwns} for any application-scoped read/write —
 * this is what makes cross-application access rejected server-side even if an
 * evidence id for an unowned application is guessed.
 */
@Component
public class AppOwnerContext {

    public static final String APP_OWNER_ROLE = "APP_OWNER";

    private final ApprovalUserRepository users;
    private final ApplicationOwnerRepository owners;

    public AppOwnerContext(ApprovalUserRepository users, ApplicationOwnerRepository owners) {
        this.users = users;
        this.owners = owners;
    }

    public AppOwnerIdentity resolve(String userRole, String userUsername) {
        if (userRole == null || userRole.isBlank()) {
            throw ApiException.badRequest("X-User-Role header is required");
        }
        if (!APP_OWNER_ROLE.equalsIgnoreCase(userRole.trim())) {
            throw ApiException.forbidden("this endpoint is restricted to the " + APP_OWNER_ROLE + " role");
        }
        if (userUsername == null || userUsername.isBlank()) {
            throw ApiException.badRequest("X-User-Username header is required");
        }
        String username = userUsername.trim();
        ApprovalUser user = users.findById(username)
                .orElseThrow(() -> ApiException.forbidden("unknown user '" + username + "'"));
        if (!APP_OWNER_ROLE.equalsIgnoreCase(user.getRole())) {
            // The header claimed APP_OWNER but the actual registered role differs — reject,
            // don't trust the client-supplied role in isolation.
            throw ApiException.forbidden("'" + username + "' is not registered as " + APP_OWNER_ROLE);
        }
        List<String> ownedSlugs = owners.findByUsernameOrderByApplicationSlugAsc(username).stream()
                .map(com.pramaan.backend.appowner.domain.ApplicationOwner::getApplicationSlug)
                .toList();
        return new AppOwnerIdentity(username, ownedSlugs);
    }

    public record AppOwnerIdentity(String username, List<String> ownedApplicationSlugs) {

        public boolean owns(String applicationSlug) {
            return applicationSlug != null
                    && ownedApplicationSlugs.stream().anyMatch(s -> s.equalsIgnoreCase(applicationSlug));
        }

        public void requireOwns(String applicationSlug) {
            if (!owns(applicationSlug)) {
                throw ApiException.forbidden(username + " is not authorized for application '" + applicationSlug + "'");
            }
        }
    }
}
