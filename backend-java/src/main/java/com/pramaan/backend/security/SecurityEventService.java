package com.pramaan.backend.security;

import com.pramaan.backend.common.ApiException;
import com.pramaan.backend.security.SecurityEvent.Type;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Records blocked requests. Each event is written in its own transaction (so it survives the failed request's
 * rollback), and a logging failure never changes the response. Only sanitized, truncated metadata is kept -
 * never request bodies or credentials.
 */
@Service
public class SecurityEventService {

    private static final Logger log = LoggerFactory.getLogger(SecurityEventService.class);

    static final int MAX = 300;
    static final int MAX_FIELD = 100;
    private static final String FORBIDDEN_CHARS = "<>\"'`&;";

    public record EventView(String id, Instant occurredAt, String eventType, String outcome, String role,
                            String actor, String method, String path, String detail, String remoteAddr) {
        static EventView from(SecurityEvent e) {
            return new EventView(e.getId().toString(), e.getOccurredAt(), e.getEventType().name(), e.getOutcome(),
                    e.getRole(), e.getActor(), e.getMethod(), e.getPath(), e.getDetail(), e.getRemoteAddr());
        }
    }

    private final SecurityEventRepository events;
    private final TransactionTemplate requiresNew;
    private final Clock clock;

    public SecurityEventService(SecurityEventRepository events, PlatformTransactionManager tm, Clock clock) {
        this.events = events;
        this.clock = clock;
        this.requiresNew = new TransactionTemplate(tm);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    // ---- recording ------------------------------------------------------------------------------------

    /** Record against the request currently being handled (method, path, role, user, address). */
    public void record(Type type, String detail) {
        var attrs = RequestContextHolder.getRequestAttributes();
        record(type, detail, attrs instanceof ServletRequestAttributes sra ? sra.getRequest() : null);
    }

    public void record(Type type, String detail, HttpServletRequest req) {
        try {
            SecurityEvent e = new SecurityEvent(clock.instant(), type,
                    req == null ? null : clean(req.getHeader("X-Pramaan-Role"), 64),
                    req == null ? null : clean(req.getHeader("X-Pramaan-User"), 64),
                    req == null ? null : clean(req.getMethod(), 10),
                    req == null ? null : clean(req.getRequestURI(), MAX),
                    clean(detail, MAX),
                    req == null ? null : clean(req.getRemoteAddr(), 64));
            requiresNew.executeWithoutResult(s -> events.save(e));
        } catch (RuntimeException ex) {
            log.warn("could not record security event {}: {}", type, ex.getMessage());
        }
    }

    /** Strip control characters and truncate. */
    static String clean(String v, int max) {
        if (v == null) return null;
        StringBuilder sb = new StringBuilder(Math.min(v.length(), max));
        for (int i = 0; i < v.length() && sb.length() < max; i++) {
            char c = v.charAt(i);
            if (!Character.isISOControl(c)) sb.append(c);
        }
        return sb.toString();
    }

    // ---- input validation (server side) ---------------------------------------------------------------

    /** 1-100 chars, none of < > " ' ` & ; or control characters; otherwise 400 + INPUT_REJECTED. */
    public String requireSafe(String field, String value) {
        return requireSafe(field, value, MAX_FIELD);
    }

    /** {@link #requireSafe} for identifiers that may end up in keys/paths: also rejects "/", "\" and "..". */
    public String requireSafePath(String field, String value) {
        String safe = requireSafe(field, value);
        if (safe.indexOf('/') >= 0 || safe.indexOf('\\') >= 0 || safe.contains("..")) {
            reject(field, value, "must not contain path separators or \"..\" (/ \\ ..)");
        }
        return safe;
    }

    /** Same character rules with a custom maximum length (e.g. free-text observations). */
    public String requireSafe(String field, String value, int max) {
        String problem = value == null || value.isBlank() ? "is required"
                : value.trim().length() > max ? "must be at most " + max + " characters"
                : hasForbidden(value) ? "contains characters that are not allowed (< > \" ' ` & ; or control characters)"
                : null;
        if (problem != null) {
            reject(field, value, problem);
        }
        return value.trim();
    }

    /** Optional list items (applications, sources, ...): each present item must be safe. */
    public void requireSafeItems(String field, List<String> values) {
        if (values != null) values.forEach(v -> requireSafe(field, v));
    }

    /** X-Pramaan-User: [A-Za-z0-9 ._@-], max 64. */
    public boolean validUser(String user) {
        if (user == null || user.length() > 64) return user == null;
        for (char c : user.toCharArray()) {
            if (!(Character.isLetterOrDigit(c) && c < 128) && " ._@-".indexOf(c) < 0) return false;
        }
        return true;
    }

    private static boolean hasForbidden(String v) {
        for (char c : v.toCharArray()) {
            if (Character.isISOControl(c) || FORBIDDEN_CHARS.indexOf(c) >= 0) return true;
        }
        return false;
    }

    private void reject(String field, String value, String problem) {
        record(Type.INPUT_REJECTED, field + " " + problem + ": " + clean(value, 80));
        throw ApiException.badRequest(field + " " + problem);
    }

    // ---- query ----------------------------------------------------------------------------------------

    public List<EventView> list(String type, Integer limit) {
        int n = limit == null ? 100 : Math.max(1, Math.min(limit, 500));
        var page = PageRequest.of(0, n);
        if (type == null || type.isBlank()) {
            return events.findAllByOrderByOccurredAtDesc(page).stream().map(EventView::from).toList();
        }
        Type t;
        try {
            t = Type.valueOf(type.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("type must be one of " + java.util.Arrays.toString(Type.values()));
        }
        return events.findByEventTypeOrderByOccurredAtDesc(t, page).stream().map(EventView::from).toList();
    }
}
