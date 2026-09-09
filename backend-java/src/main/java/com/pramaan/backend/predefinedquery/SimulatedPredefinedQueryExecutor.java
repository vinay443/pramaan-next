package com.pramaan.backend.predefinedquery;

import com.pramaan.backend.util.Hashing;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Default executor — returns deterministic synthetic output for ANY catalogue
 * entry, regardless of its {@code executableNow} / {@code runtimeStatus} (none of
 * it reaches real infrastructure yet). Mirrors the SIM_* technology collectors:
 * same {@code "simulated": true} body shape and {@code "mock": "true"} metadata
 * convention, and byte-identical content for the same (control, technology,
 * application, day) so re-runs deduplicate cleanly.
 */
public class SimulatedPredefinedQueryExecutor implements PredefinedQueryExecutor {

    @Override
    public String mode() {
        return "SIMULATED";
    }

    @Override
    public Output execute(PredefinedQuery q, String applicationSlug, Instant asOf) {
        String day = asOf.atZone(ZoneOffset.UTC).toLocalDate().toString();
        String body = """
                {
                  "collector": "predefined-query",
                  "technology": "%s",
                  "application": "%s",
                  "controlId": "%s",
                  "controlName": "%s",
                  "controlFamily": "%s",
                  "asOf": "%s",
                  "simulated": true,
                  "status": "pass",
                  "command": %s,
                  "output": %s
                }
                """.formatted(json(q.technology()), json(applicationSlug), json(q.controlId()),
                json(q.controlName()), json(q.controlFamily()), day,
                json(q.command()), json(simulatedOutput(q)));
        byte[] content = body.getBytes(StandardCharsets.UTF_8);

        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("mock", "true");
        metadata.put("simulated", "true");
        metadata.put("collector", "predefined-query");
        metadata.put("technology", q.technology());
        metadata.put("controlFamily", q.controlFamily());
        metadata.put("runtimeStatus", q.runtimeStatus());
        metadata.put("executableNow", String.valueOf(q.executableNow()));
        metadata.put("digest.short", Hashing.sha256Hex(content).substring(0, 12));

        Map<String, String> tags = new LinkedHashMap<>();
        tags.put("technology", q.technology() == null ? "unknown" : q.technology().toLowerCase());
        tags.put("collectionMethod", "predefined-query");
        tags.put("evidenceType", q.evidenceType() == null ? "query-output" : q.evidenceType());
        tags.put("controlFamily", q.controlFamily() == null ? "" : q.controlFamily());
        tags.put("agent", "PREDEFINED_QUERY");

        return new Output(body, "application/json", metadata, tags);
    }

    private static String simulatedOutput(PredefinedQuery q) {
        String et = q.evidenceType() == null ? "" : q.evidenceType().toLowerCase();
        if (et.contains("sql")) {
            return "%s | simulated row: compliant=true (%s)".formatted(q.command(), q.controlName());
        }
        if (et.contains("powershell") || et.contains("shell") || et.contains("command")) {
            return "$ %s\\n<simulated stdout> %s: OK".formatted(q.command(), q.controlId());
        }
        return "simulated result for %s / %s: compliant".formatted(q.technology(), q.controlId());
    }

    /** Minimal JSON string escaping for embedding catalogue text into the body. */
    private static String json(String s) {
        if (s == null) {
            return "\"\"";
        }
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> sb.append(c);
            }
        }
        return sb.append('"').toString();
    }
}
