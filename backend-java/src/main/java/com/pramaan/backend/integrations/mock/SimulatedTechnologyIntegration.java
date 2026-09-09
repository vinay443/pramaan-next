package com.pramaan.backend.integrations.mock;

import com.pramaan.backend.integrations.EnterpriseIntegration;
import com.pramaan.backend.util.Hashing;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Use Case 1 — a scheduled evidence pull from an infra technology (PostgreSQL,
 * MySQL, NGINX, Tomcat, …) running in MOCK / SIMULATION mode. Mirrors the
 * deterministic synthetic checks the Go collectors run, so the existing
 * {@code SchedulerRunExecutor} drives them with no executor changes and the
 * evidence lands in the same repository as manual uploads.
 *
 * <p>Byte-identical content for the same (application, control, day) so re-runs
 * deduplicate cleanly; a later day yields a new version.
 */
abstract class SimulatedTechnologyIntegration implements EnterpriseIntegration {

    /** One simulated technical check bundled into evidence for a control. */
    record SimCheck(String framework, String controlId, String checkId, String title,
                    String observed, String expected, String status) {}

    protected abstract String technology();

    protected abstract String collector();

    protected abstract List<SimCheck> checks();

    @Override
    public boolean mock() {
        return true;
    }

    @Override
    public List<CollectedEvidence> collect(CollectionRequest request) {
        Instant asOf = request.asOf() != null ? request.asOf() : Instant.now();
        String day = asOf.atZone(ZoneOffset.UTC).toLocalDate().toString();

        // group simulated checks by (framework, control) -> one evidence bundle each
        Map<String, List<SimCheck>> byControl = new LinkedHashMap<>();
        for (SimCheck c : checks()) {
            if (frameworkExcluded(request, c.framework())) {
                continue;
            }
            byControl.computeIfAbsent(c.framework() + "|" + c.controlId(), k -> new ArrayList<>()).add(c);
        }

        List<CollectedEvidence> out = new ArrayList<>();
        byControl.forEach((key, group) -> {
            String[] parts = key.split("\\|", 2);
            String framework = parts[0];
            String controlId = parts[1];
            String checksJson = renderChecks(group);
            String body = """
                    {
                      "collector": "%s",
                      "technology": "%s",
                      "application": "%s",
                      "framework": "%s",
                      "control": "%s",
                      "asOf": "%s",
                      "simulated": true,
                      "status": "pass",
                      "checks": %s
                    }
                    """.formatted(collector(), technology(), request.applicationSlug(), framework,
                    controlId, day, checksJson);
            byte[] content = body.getBytes(StandardCharsets.UTF_8);
            String objectId = technology() + "/" + controlId.toLowerCase();
            // title left null so the Use Case 3 naming convention is applied at ingest
            out.add(new CollectedEvidence(controlId, framework, objectId, null,
                    "application/json", content,
                    asOf.truncatedTo(ChronoUnit.SECONDS),
                    Map.of("collector", collector(),
                            "technology", technology(),
                            "simulated", "true",
                            "digest.short", Hashing.sha256Hex(content).substring(0, 12)),
                    Map.of("technology", technology(),
                            "collectionMethod", "scheduled",
                            "agent", sourceSystem())));
        });
        return out;
    }

    private static boolean frameworkExcluded(CollectionRequest request, String framework) {
        return request.frameworks() != null && !request.frameworks().isEmpty()
                && request.frameworks().stream().noneMatch(f -> f.equalsIgnoreCase(framework));
    }

    private static String renderChecks(List<SimCheck> group) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < group.size(); i++) {
            SimCheck c = group.get(i);
            if (i > 0) {
                sb.append(",");
            }
            sb.append("""
                    {"checkId":"%s","controlId":"%s","framework":"%s","status":"%s","observed":"%s","expected":"%s","title":"%s"}"""
                    .formatted(c.checkId(), c.controlId(), c.framework(), c.status(),
                            c.observed(), c.expected(), c.title()));
        }
        return sb.append("]").toString();
    }
}
