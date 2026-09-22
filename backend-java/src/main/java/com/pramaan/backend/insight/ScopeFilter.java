package com.pramaan.backend.insight;

import java.util.Collection;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** Business-unit scope for the insight endpoints (case-insensitive; null = unscoped). */
final class ScopeFilter {

    private ScopeFilter() {}

    /** Normalised scope, or null when no (non-blank) business unit was requested. */
    static Set<String> of(Collection<String> businessUnits) {
        if (businessUnits == null) {
            return null;
        }
        Set<String> s = businessUnits.stream()
                .filter(b -> b != null && !b.isBlank())
                .map(b -> b.trim().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        return s.isEmpty() ? null : s;
    }

    static boolean matches(Set<String> scope, String businessUnit) {
        return scope == null || (businessUnit != null && scope.contains(businessUnit.trim().toLowerCase(Locale.ROOT)));
    }
}
