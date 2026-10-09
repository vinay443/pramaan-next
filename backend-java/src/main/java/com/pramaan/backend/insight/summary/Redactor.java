package com.pramaan.backend.insight.summary;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Secret redaction applied to everything a summary can show. A key/label that names a
 * secret hides its value; free text is scrubbed for PEM private-key blocks and
 * {@code secret-key: value} / {@code Bearer value} pairs.
 */
public final class Redactor {

    public static final String REDACTED = "[REDACTED]";

    private static final String KEY_WORDS =
            "password|passwd|secret|token|api[_-]?key|private[_-]?key|credential|authorization|bearer";

    private static final Pattern SENSITIVE_KEY = Pattern.compile("(?i)(" + KEY_WORDS + ")");

    private static final Pattern PEM_BLOCK = Pattern.compile(
            "-----BEGIN [A-Z0-9 ]*PRIVATE KEY-----.*?(-----END [A-Z0-9 ]*PRIVATE KEY-----|$)",
            Pattern.DOTALL);

    /** {@code password=abc}, {@code "token": "abc"}, {@code api_key: abc} — the value part is hidden. */
    private static final Pattern KEY_VALUE = Pattern.compile(
            "(?i)(\\b[\\w.-]*(?:" + KEY_WORDS + ")[\\w.-]*\"?\\s*[:=]\\s*)(\\[REDACTED\\]|\"[^\"]*\"|'[^']*'|[^\\s,;}\\]]+)");

    private static final Pattern BEARER = Pattern.compile("(?i)\\bbearer\\s+[A-Za-z0-9._~+/=-]+");

    private Redactor() {}

    public static boolean isSensitiveKey(String key) {
        return key != null && SENSITIVE_KEY.matcher(key).find();
    }

    public static boolean looksLikePrivateKey(String value) {
        return value != null && value.contains("PRIVATE KEY-----") && PEM_BLOCK.matcher(value).find();
    }

    /** Scrub free text; returns the input unchanged when there is nothing to hide. */
    public static String redactText(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String out = text;
        if (out.contains("PRIVATE KEY-----")) {
            out = PEM_BLOCK.matcher(out).replaceAll(REDACTED);
        }
        out = BEARER.matcher(out).replaceAll("Bearer " + REDACTED);
        out = KEY_VALUE.matcher(out).replaceAll(m -> Matcher.quoteReplacement(m.group(1) + REDACTED));
        return out;
    }
}
