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
 * Deterministic synthetic evidence generator. The same (application, control, day)
 * always produces byte-identical content, so re-runs dedup cleanly; a later day
 * produces a new version.
 */
abstract class AbstractMockIntegration implements EnterpriseIntegration {

    /** (framework, controlId, objectType) tuples this mock system reports on. */
    protected abstract List<String[]> catalog();

    /** Extra provenance stamped onto every collected item (e.g. the configured, secret-free endpoint). */
    protected Map<String, String> extraMetadata() {
        return Map.of();
    }

    @Override
    public boolean mock() {
        return true;
    }

    @Override
    public List<CollectedEvidence> collect(CollectionRequest request) {
        Instant asOf = request.asOf() != null ? request.asOf() : Instant.now();
        String day = asOf.atZone(ZoneOffset.UTC).toLocalDate().toString();
        List<CollectedEvidence> out = new ArrayList<>();
        for (String[] entry : catalog()) {
            String framework = entry[0];
            String controlId = entry[1];
            String objectType = entry[2];
            if (request.frameworks() != null && !request.frameworks().isEmpty()
                    && request.frameworks().stream().noneMatch(f -> f.equalsIgnoreCase(framework))) {
                continue;
            }
            String objectId = sourceSystem().toLowerCase() + "-" + controlId.toLowerCase();
            String body = """
                    {
                      "sourceSystem": "%s",
                      "application": "%s",
                      "framework": "%s",
                      "control": "%s",
                      "objectType": "%s",
                      "asOf": "%s",
                      "status": "COLLECTED",
                      "reference": "%s"
                    }
                    """.formatted(sourceSystem(), request.applicationSlug(), framework, controlId,
                    objectType, day, objectId);
            byte[] content = body.getBytes(StandardCharsets.UTF_8);
            Map<String, String> metadata = new LinkedHashMap<>();
            metadata.put("mock", "true");
            metadata.put("digest.short", Hashing.sha256Hex(content).substring(0, 12));
            metadata.put("collector", sourceSystem());
            metadata.putAll(extraMetadata());
            out.add(new CollectedEvidence(controlId, framework, objectId,
                    objectType + " for " + controlId, "application/json", content,
                    asOf.truncatedTo(ChronoUnit.SECONDS),
                    metadata,
                    Map.of("source", sourceSystem(), "objectType", objectType)));
        }
        return out;
    }
}
