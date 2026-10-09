package com.pramaan.backend.insight.summary;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/** Small helpers shared by the JSON summarizers. */
final class JsonSupport {

    /** Deepest nesting level inspected; the root container is level 1. */
    static final int MAX_DEPTH = 6;

    private static final Set<String> STATUS_KEYS = Set.of("status", "result", "state", "outcome");
    private static final Set<String> NEGATIVE = Set.of(
            "fail", "failed", "failure", "error", "errored", "false", "rejected", "denied",
            "non-compliant", "noncompliant", "non_compliant", "no", "critical");

    private JsonSupport() {}

    static String pointer(String parent, String key) {
        return parent + "/" + key.replace("~", "~0").replace("/", "~1");
    }

    static String pointer(String parent, int index) {
        return parent + "/" + index;
    }

    /** Scalar value as text, or null for null/missing/containers. */
    static String scalar(JsonNode n) {
        if (n == null || n.isNull() || n.isMissingNode() || n.isContainerNode()) {
            return null;
        }
        String t = n.asText();
        return t.isBlank() ? null : t;
    }

    static String text(JsonNode obj, String key) {
        return obj == null ? null : scalar(obj.get(key));
    }

    /** First present scalar among {@code keys}; returns {key, value} or null. */
    static String[] firstOf(JsonNode obj, String... keys) {
        for (String k : keys) {
            String v = text(obj, k);
            if (v != null) {
                return new String[]{k, v};
            }
        }
        return null;
    }

    static List<String> sortedFieldNames(JsonNode obj) {
        Set<String> names = new TreeSet<>();
        obj.fieldNames().forEachRemaining(names::add);
        return new ArrayList<>(names);
    }

    static boolean isStatusKey(String key) {
        return key != null && STATUS_KEYS.contains(key.toLowerCase(Locale.ROOT));
    }

    static boolean isNegative(String value) {
        return value != null && NEGATIVE.contains(value.strip().toLowerCase(Locale.ROOT));
    }

    /** Best-effort ISO day ("2026-09-15") from a timestamp-ish string, else the string itself. */
    static String day(String s) {
        if (s != null && s.length() >= 10 && s.substring(0, 10).matches("\\d{4}-\\d{2}-\\d{2}")) {
            return s.substring(0, 10);
        }
        return s;
    }
}
