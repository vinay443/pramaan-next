package com.pramaan.backend.integrations;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * A source of evidence from an external enterprise system.
 *
 * <p>Phase 1 provides deterministic mock implementations only. Real connectors
 * (or Go collectors calling in) can implement this same interface later.
 */
public interface EnterpriseIntegration {

    /** Stable source-system identifier, e.g. {@code MOCK_JIRA}. */
    String sourceSystem();

    boolean mock();

    List<CollectedEvidence> collect(CollectionRequest request);

    record CollectionRequest(String applicationSlug, List<String> frameworks, Instant asOf) {}

    record CollectedEvidence(
            String controlId,
            String framework,
            String sourceObjectId,
            String title,
            String contentType,
            byte[] content,
            Instant collectedAt,
            Map<String, String> metadata,
            Map<String, String> tags) {}
}
