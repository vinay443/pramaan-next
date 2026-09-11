package com.pramaan.backend.admin;

import com.pramaan.backend.admin.AdminDtos.RoleView;
import com.pramaan.backend.admin.AdminDtos.UserUpsertRequest;
import com.pramaan.backend.admin.AdminDtos.UserView;
import com.pramaan.backend.common.ApiException;
import com.pramaan.backend.config.PramaanProperties;
import com.pramaan.backend.evidence.domain.ApprovalRoleScope;
import com.pramaan.backend.evidence.domain.ApprovalUser;
import com.pramaan.backend.evidence.repo.ApprovalRoleScopeRepository;
import com.pramaan.backend.evidence.repo.ApprovalUserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * In-memory admin registry (no enterprise IdP, no credentials stored). Seeded with
 * demo personas on startup; the canonical role catalogue comes from
 * {@code pramaan.admin.roles}. Behavioural reference: ECS {@code admin_service.py}.
 */
@Service
public class AdminService {

    private static final Logger log = LoggerFactory.getLogger(AdminService.class);

    private static final List<String> DEFAULT_ROLES =
            List.of("ADMIN", "AUDITOR", "APP_OWNER", "COMPLIANCE_OFFICER", "VIEWER");

    private static final Map<String, String> ROLE_DESCRIPTIONS = Map.of(
            "ADMIN", "Full administrative access — manage users, roles and applications",
            "AUDITOR", "Read-only access to evidence, controls and compliance posture",
            "APP_OWNER", "Owns one or more applications; uploads and attests evidence",
            "COMPLIANCE_OFFICER", "Reviews compliance posture and approves evidence",
            "VIEWER", "Read-only dashboards");

    /** username (lower-case) -> user. */
    private final Map<String, UserView> users = new ConcurrentHashMap<>();
    private final List<String> roles;
    private final Set<String> roleLookup;
    private final Clock clock;
    private final ApprovalRoleScopeRepository approvalRoleScopes;
    private final ApprovalUserRepository approvalUsers;

    public AdminService(PramaanProperties props, Clock clock, ApprovalRoleScopeRepository approvalRoleScopes,
                        ApprovalUserRepository approvalUsers) {
        this.clock = clock;
        this.approvalRoleScopes = approvalRoleScopes;
        this.approvalUsers = approvalUsers;
        List<String> configured = props.admin() == null ? null : props.admin().roles();
        this.roles = configured == null || configured.isEmpty()
                ? DEFAULT_ROLES
                : configured.stream().map(r -> r.trim().toUpperCase(Locale.ROOT)).filter(r -> !r.isBlank()).toList();
        this.roleLookup = new LinkedHashSet<>(roles);
        seed();
    }

    private void seed() {
        Instant now = clock.instant();
        List.of(
                new UserView("admin", "Platform Admin", "admin@pramaan.local",
                        List.of("ADMIN"), true, now, now),
                new UserView("lead.auditor", "Lead Auditor", "auditor@pramaan.local",
                        List.of("AUDITOR", "VIEWER"), true, now, now),
                new UserView("netbanking.owner", "Net Banking Owner", "owner.netbanking@pramaan.local",
                        List.of("APP_OWNER"), true, now, now)
        ).forEach(u -> users.putIfAbsent(u.username(), u));
        log.info("admin registry seeded: {} users, {} roles", users.size(), roles.size());
    }

    /**
     * The configured admin role catalogue, plus the evidence-approval RBAC roles
     * ({@code ApprovalRoleScope} — seeded by {@code ApprovalRbacSeedRunner}) not
     * already present. Reuses this one Users & Roles surface rather than adding a
     * second roles endpoint for the approval feature.
     */
    public List<RoleView> roles() {
        List<RoleView> out = new ArrayList<>(roles.stream()
                .map(r -> new RoleView(r, ROLE_DESCRIPTIONS.getOrDefault(r, "")))
                .toList());
        for (ApprovalRoleScope s : approvalRoleScopes.findAll()) {
            if (roleLookup.contains(s.getRole())) {
                continue; // already in the configured catalogue (e.g. AUDITOR, APP_OWNER)
            }
            out.add(new RoleView(s.getRole(), s.getDescription()));
        }
        return out;
    }

    /** In-memory personas, plus the evidence-approval RBAC demo users (same reuse rationale as {@link #roles()}). */
    public List<UserView> listUsers() {
        List<UserView> out = new ArrayList<>(users.values());
        for (ApprovalUser u : approvalUsers.findAllByOrderByUsernameAsc()) {
            out.add(new UserView(u.getUsername(), u.getDisplayName(), null,
                    List.of(u.getRole()), true, u.getCreatedAt(), u.getCreatedAt()));
        }
        out.sort((a, b) -> a.username().compareTo(b.username()));
        return out;
    }

    public UserView getUser(String username) {
        UserView u = users.get(key(username));
        if (u != null) {
            return u;
        }
        return approvalUsers.findById(key(username))
                .map(a -> new UserView(a.getUsername(), a.getDisplayName(), null,
                        List.of(a.getRole()), true, a.getCreatedAt(), a.getCreatedAt()))
                .orElseThrow(() -> ApiException.notFound("Unknown user: " + username));
    }

    public UserView upsert(UserUpsertRequest req) {
        String username = key(req.username());
        List<String> normalizedRoles = normalizeRoles(req.roles());
        Instant now = clock.instant();
        UserView existing = users.get(username);
        Instant createdAt = existing != null ? existing.createdAt() : now;
        boolean active = req.active() != null ? req.active() : (existing == null || existing.active());
        String email = req.email() == null || req.email().isBlank() ? null : req.email().trim();

        UserView saved = new UserView(username, req.displayName().trim(), email,
                normalizedRoles, active, createdAt, now);
        users.put(username, saved);
        return saved;
    }

    public UserView setActive(String username, boolean active) {
        UserView u = getUser(username);
        UserView updated = new UserView(u.username(), u.displayName(), u.email(), u.roles(),
                active, u.createdAt(), clock.instant());
        users.put(u.username(), updated);
        return updated;
    }

    public void delete(String username) {
        if (users.remove(key(username)) == null) {
            throw ApiException.notFound("Unknown user: " + username);
        }
    }

    private List<String> normalizeRoles(List<String> requested) {
        if (requested == null || requested.isEmpty()) {
            throw ApiException.badRequest("at least one role is required");
        }
        // preserve request order, de-duplicate, validate against the catalogue
        Map<String, Boolean> seen = new LinkedHashMap<>();
        for (String r : requested) {
            if (r == null || r.isBlank()) {
                continue;
            }
            String norm = r.trim().toUpperCase(Locale.ROOT);
            if (!roleLookup.contains(norm) && !approvalRoleScopes.existsById(norm)) {
                throw ApiException.badRequest("unknown role '" + r + "'; allowed: " + roles);
            }
            seen.putIfAbsent(norm, Boolean.TRUE);
        }
        if (seen.isEmpty()) {
            throw ApiException.badRequest("at least one role is required");
        }
        return List.copyOf(seen.keySet());
    }

    private static String key(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }
}
