package com.pramaan.backend.appowner;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Denies the APP_OWNER role admin/role-administration access at the transport
 * layer — hard "No-Access" per the App Owner UI-state rules, independent of the
 * frontend hiding those actions. Purely additive: for every role other than
 * APP_OWNER (including no header at all — every other role/path in this app has
 * no header requirement), this filter is a no-op and the request proceeds exactly
 * as before this filter existed.
 *
 * <p>Approve/Reject is already blocked for APP_OWNER independently, at the
 * service layer, by {@code EvidenceApprovalAuthorizer} (the seeded APP_OWNER role
 * has {@code canApprove=false}) — nothing here duplicates that.
 */
@Component
public class AppOwnerAccessFilter extends OncePerRequestFilter {

    private static final String APP_OWNER_ROLE = "APP_OWNER";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String role = request.getHeader("X-User-Role");
        if (role != null && APP_OWNER_ROLE.equalsIgnoreCase(role.trim()) && isAdminPath(request.getRequestURI())) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"status\":403,\"error\":\"Forbidden\",\"message\":\"APP_OWNER has no access to role administration\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    private static boolean isAdminPath(String uri) {
        return uri != null && uri.startsWith("/api/v1/admin");
    }
}
