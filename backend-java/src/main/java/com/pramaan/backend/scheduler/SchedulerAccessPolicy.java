package com.pramaan.backend.scheduler;

import com.pramaan.backend.security.SecurityEvent.Type;
import com.pramaan.backend.security.SecurityEventService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * UC01-FT009 / FT012 - role-based access and request hygiene for the scheduler area (schedules, scheduler runs,
 * source connections, system status, notifications, security events). This is authorization on the persona the
 * UI declares in {@code X-Pramaan-Role} (demo mode, no real authentication). The whole policy lives here:
 * <ul>
 *   <li>VIEW (GET): every known role. Notifications: also marking read (POST), scoped to the caller role.</li>
 *   <li>MANAGE (any other method): {@link #MANAGE_ROLES} only.</li>
 *   <li>Security events: {@link #SECURITY_ROLES} only.</li>
 *   <li>Missing or unknown role: 403 everywhere - never a default role.</li>
 *   <li>{@code X-Pramaan-User} must be plain text; id path variables must be UUIDs (400 otherwise).</li>
 * </ul>
 * Every block is recorded as a security event. The {@code @Scheduled} tick is not an HTTP call and is unaffected.
 */
@Component
public class SchedulerAccessPolicy implements HandlerInterceptor {

    public static final String ROLE_HEADER = "X-Pramaan-Role";
    public static final String USER_HEADER = "X-Pramaan-User";

    /** Canonical persona codes (frontend PersonaLogin.tsx). */
    public static final Set<String> KNOWN_ROLES = Set.of("APP", "AUDITOR", "FH", "VH", "CIO", "ADMIN");
    static final Set<String> MANAGE_ROLES = Set.of("APP", "ADMIN");
    static final Set<String> SECURITY_ROLES = Set.of("ADMIN", "AUDITOR");
    /** Retention: everyone reads the policy and runs, only these change the policy or archive. */
    static final Set<String> RETENTION_ROLES = Set.of("ADMIN");
    /** Audit observations: all roles read, only these write (raise / close). */
    static final Set<String> OBSERVATION_ROLES = Set.of("ADMIN", "AUDITOR");

    private final SecurityEventService security;

    public SchedulerAccessPolicy(SecurityEventService security) {
        this.security = security;
    }

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) throws IOException {
        if (HttpMethod.OPTIONS.matches(req.getMethod())) {
            return true; // CORS preflight carries no custom headers
        }
        String uri = req.getRequestURI();
        String role = req.getHeader(ROLE_HEADER);
        if (role == null || !KNOWN_ROLES.contains(role.trim())) {
            security.record(Type.UNKNOWN_ROLE, "role received: " + (role == null ? "(none)" : role), req);
            return reject(res, HttpServletResponse.SC_FORBIDDEN, "FORBIDDEN",
                    "Request has an unknown or missing role (" + ROLE_HEADER + "); access to the scheduler is denied");
        }
        role = role.trim();

        if (uri.startsWith("/api/v1/security")) {
            if (!SECURITY_ROLES.contains(role)) {
                security.record(Type.ACCESS_DENIED, "refused: view security events", req);
                return reject(res, HttpServletResponse.SC_FORBIDDEN, "FORBIDDEN",
                        "Role " + role + " is not permitted to view security events");
            }
        } else if (uri.startsWith("/api/v1/retention")) {
            boolean read = HttpMethod.GET.matches(req.getMethod()) || HttpMethod.HEAD.matches(req.getMethod());
            if (!read && !RETENTION_ROLES.contains(role)) {
                security.record(Type.ACCESS_DENIED, "refused: change retention policy or archive (" + req.getMethod() + ")", req);
                return reject(res, HttpServletResponse.SC_FORBIDDEN, "FORBIDDEN",
                        "Role " + role + " is not permitted to change the retention policy or run archival");
            }
        } else if (isObservation(uri)) {
            boolean read = HttpMethod.GET.matches(req.getMethod()) || HttpMethod.HEAD.matches(req.getMethod());
            if (!read && !OBSERVATION_ROLES.contains(role)) {
                security.record(Type.ACCESS_DENIED, "refused: raise or close audit observations (" + req.getMethod() + ")", req);
                return reject(res, HttpServletResponse.SC_FORBIDDEN, "FORBIDDEN",
                        "Role " + role + " is not permitted to raise or close audit observations");
            }
        } else {
            boolean read = HttpMethod.GET.matches(req.getMethod()) || HttpMethod.HEAD.matches(req.getMethod())
                    // marking your own notifications read is view-level; PUT settings stays MANAGE
                    || (HttpMethod.POST.matches(req.getMethod()) && uri.startsWith("/api/v1/notifications"));
            if (!read && !MANAGE_ROLES.contains(role)) {
                security.record(Type.ACCESS_DENIED, "refused: manage the scheduler (" + req.getMethod() + ")", req);
                return reject(res, HttpServletResponse.SC_FORBIDDEN, "FORBIDDEN",
                        "Role " + role + " is not permitted to manage the scheduler "
                                + "(create or change schedules, run or retry collections, set source connections)");
            }
        }

        String user = req.getHeader(USER_HEADER);
        if (!security.validUser(user)) {
            security.record(Type.INPUT_REJECTED, USER_HEADER + " has characters that are not allowed or is too long", req);
            return reject(res, HttpServletResponse.SC_BAD_REQUEST, "BAD_REQUEST",
                    USER_HEADER + " may only contain letters, digits, space and . _ @ - (max 64 characters)");
        }

        String badId = invalidIdentifier(uri);
        if (badId != null) {
            security.record(Type.INVALID_IDENTIFIER, "not a valid id: " + badId, req);
            return reject(res, HttpServletResponse.SC_BAD_REQUEST, "BAD_REQUEST", "Invalid identifier in the request path");
        }
        return true;
    }

    private static boolean isObservation(String uri) {
        return uri.startsWith("/api/v1/observations") || (uri.startsWith("/api/v1/evidence/") && uri.endsWith("/observations"));
    }

    /** The id path segment of /scheduler/runs/{id}, /schedules/{id} and /notifications/{id}/read, if not a UUID. */
    private static String invalidIdentifier(String uri) {
        String[] s = uri.split("/"); // ["", "api", "v1", area, ...]
        if (s.length < 5) return null;
        String id = switch (s[3]) {
            case "scheduler" -> s[4].equals("runs") && s.length >= 6 ? s[5] : null;
            case "schedules" -> s[4].equals("options") ? null : s[4];
            case "notifications" -> s[4].equals("settings") || s[4].equals("read-all") ? null : s[4];
            case "evidence", "observations" -> s[4];
            default -> null;
        };
        if (id == null) return null;
        String decoded = URLDecoder.decode(id, StandardCharsets.UTF_8);
        try {
            if (decoded.length() == 36 && UUID.fromString(decoded).toString().equalsIgnoreCase(decoded)) return null;
        } catch (IllegalArgumentException ignored) {
            // falls through: not a UUID
        }
        return decoded;
    }

    private static boolean reject(HttpServletResponse res, int status, String error, String message) throws IOException {
        res.setStatus(status);
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding("UTF-8");
        res.getWriter().write("{\"error\":\"" + error + "\",\"message\":\"" + message.replace("\"", "'") + "\"}");
        return false;
    }

    @Configuration
    static class Registration implements WebMvcConfigurer {
        private final SchedulerAccessPolicy policy;

        Registration(SchedulerAccessPolicy policy) {
            this.policy = policy;
        }

        @Override
        public void addInterceptors(InterceptorRegistry registry) {
            registry.addInterceptor(policy).addPathPatterns(
                    "/api/v1/scheduler/**", "/api/v1/schedules/**", "/api/v1/schedules",
                    "/api/v1/sources/**", "/api/v1/system/**",
                    "/api/v1/notifications", "/api/v1/notifications/**", "/api/v1/security/**",
                    "/api/v1/evidence/*/observations", "/api/v1/observations", "/api/v1/observations/**",
                    "/api/v1/dashboard/**", "/api/v1/retention/**");
        }
    }
}
