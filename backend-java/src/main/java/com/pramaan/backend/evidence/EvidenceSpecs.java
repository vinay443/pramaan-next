package com.pramaan.backend.evidence;

import com.pramaan.backend.evidence.domain.EvidenceRecord;
import com.pramaan.backend.evidence.domain.EvidenceTag;
import jakarta.persistence.criteria.Join;
import java.time.Instant;
import org.springframework.data.jpa.domain.Specification;

/** Composable, deterministic filters for evidence-record queries. */
final class EvidenceSpecs {

    private EvidenceSpecs() {}

    static Specification<EvidenceRecord> applicationSlug(String v) {
        return eq("applicationSlug", v == null ? null : v.toLowerCase());
    }

    static Specification<EvidenceRecord> framework(String v) {
        return eq("framework", v == null ? null : v.toUpperCase());
    }

    static Specification<EvidenceRecord> controlId(String v) {
        return eq("controlId", v == null ? null : v.toUpperCase());
    }

    static Specification<EvidenceRecord> sourceSystem(String v) {
        return eq("sourceSystem", v == null ? null : v.toUpperCase());
    }

    static Specification<EvidenceRecord> collectedAfter(Instant v) {
        return (root, q, cb) -> v == null ? null : cb.greaterThanOrEqualTo(root.get("latestCollectedAt"), v);
    }

    static Specification<EvidenceRecord> collectedBefore(Instant v) {
        return (root, q, cb) -> v == null ? null : cb.lessThan(root.get("latestCollectedAt"), v);
    }

    /** Filter by tag "key" or "key:value". */
    static Specification<EvidenceRecord> tag(String expr) {
        return (root, query, cb) -> {
            if (expr == null || expr.isBlank()) {
                return null;
            }
            if (query != null) {
                query.distinct(true);
            }
            String[] parts = expr.split(":", 2);
            Join<EvidenceRecord, EvidenceTag> join = root.join("tags");
            var keyMatch = cb.equal(join.get("tagKey"), parts[0].trim());
            return parts.length == 2
                    ? cb.and(keyMatch, cb.equal(join.get("tagValue"), parts[1].trim()))
                    : keyMatch;
        };
    }

    /** Filter by an exact tag key + value (used for the technology / collection-method facets). */
    static Specification<EvidenceRecord> tagPair(String key, String value) {
        return (root, query, cb) -> {
            if (value == null || value.isBlank()) {
                return null;
            }
            if (query != null) {
                query.distinct(true);
            }
            Join<EvidenceRecord, EvidenceTag> join = root.join("tags");
            return cb.and(cb.equal(join.get("tagKey"), key),
                    cb.equal(cb.lower(join.get("tagValue")), value.trim().toLowerCase()));
        };
    }

    private static Specification<EvidenceRecord> eq(String field, String value) {
        return (root, q, cb) -> value == null || value.isBlank() ? null : cb.equal(root.get(field), value);
    }
}
