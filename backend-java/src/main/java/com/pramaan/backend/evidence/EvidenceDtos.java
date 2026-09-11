package com.pramaan.backend.evidence;

import com.pramaan.backend.evidence.domain.EvidenceRecord;
import com.pramaan.backend.evidence.domain.EvidenceVersion;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class EvidenceDtos {

    private EvidenceDtos() {}

    /** Single evidence item to ingest. Exactly one of {@code contentBase64} / {@code contentText} is required. */
    public record IngestRequest(
            @NotBlank String applicationSlug,
            @NotBlank String controlId,
            @NotBlank String framework,
            @NotBlank String sourceSystem,
            String sourceObjectId,
            String title,
            String contentType,
            String contentBase64,
            String contentText,
            Instant collectedAt,
            String collectedBy,
            Map<String, String> metadata,
            Map<String, String> tags) {}

    public enum IngestOutcome { CREATED, NEW_VERSION, DUPLICATE }

    public record IngestResult(
            String evidenceId,
            String evidenceKey,
            String applicationSlug,
            String controlId,
            String framework,
            String sourceSystem,
            IngestOutcome outcome,
            int version,
            String sha256,
            long sizeBytes) {}

    public record BulkIngestResponse(
            int received,
            int created,
            int newVersions,
            int duplicates,
            int failed,
            List<IngestResult> results,
            List<BulkError> errors) {

        public record BulkError(int index, String message) {}
    }

    public record EvidenceVersionView(
            int version,
            String sha256,
            String contentType,
            long sizeBytes,
            String objectKey,
            Instant collectedAt,
            String collectedBy,
            String ingestionRunId,
            Map<String, String> metadata) {

        public static EvidenceVersionView from(EvidenceVersion v) {
            return new EvidenceVersionView(v.getVersionNumber(), v.getSha256(), v.getContentType(),
                    v.getSizeBytes(), v.getObjectKey(), v.getCollectedAt(), v.getCollectedBy(),
                    v.getIngestionRunId() == null ? null : v.getIngestionRunId().toString(),
                    new TreeMap<>(v.getMetadata()));
        }
    }

    /**
     * Per-record hash-integrity verdict (UC04). {@code VERIFIED} — current version's
     * SHA-256 was just (re)computed and matches the stored value; {@code TAMPERED} —
     * recomputed hash does not match; {@code UNKNOWN} — no version, or the object is
     * missing from the store, so nothing could be verified.
     */
    public enum IntegrityStatus { VERIFIED, TAMPERED, UNKNOWN }

    public record EvidenceView(
            String evidenceId,
            String evidenceKey,
            String applicationSlug,
            String controlId,
            String framework,
            String sourceSystem,
            String sourceObjectId,
            String title,
            int currentVersion,
            String lifecycleState,
            Instant createdAt,
            Instant updatedAt,
            Map<String, String> tags,
            EvidenceVersionView latest,
            IntegrityStatus integrityStatus) {

        /** Integrity unverified — caller has no object-store access at this call site (e.g. right after ingest). */
        public static EvidenceView from(EvidenceRecord r) {
            return from(r, IntegrityStatus.UNKNOWN);
        }

        public static EvidenceView from(EvidenceRecord r, IntegrityStatus integrityStatus) {
            Map<String, String> tags = new TreeMap<>();
            r.getTags().forEach(t -> tags.put(t.getTagKey(), t.getTagValue()));
            EvidenceVersion latest = r.latestVersion();
            return new EvidenceView(r.getId().toString(), r.getEvidenceKey(), r.getApplicationSlug(),
                    r.getControlId(), r.getFramework(), r.getSourceSystem(), r.getSourceObjectId(),
                    r.getTitle(), r.getCurrentVersion(), r.getLifecycleState().name(),
                    r.getCreatedAt(), r.getUpdatedAt(), tags,
                    latest == null ? null : EvidenceVersionView.from(latest),
                    integrityStatus);
        }
    }

    public record IntegrityReport(String evidenceId, int version, String expectedSha256,
                                  String actualSha256, boolean intact, String detail) {}

    /** Use Case 4 — consolidated evidence dashboard + repository-wide hash integrity. */
    public record EvidenceDashboard(
            Instant generatedAt,
            long records,
            long versions,
            int applications,
            int frameworks,
            int sources,
            int staleAfterDays,
            Freshness freshness,
            Integrity integrity,
            int duplicateHashes,
            List<Map<String, Object>> bySource) {

        public record Freshness(int fresh, int aging, int stale, int unknown) {}

        /** Result of recomputing SHA-256 for the current version of every persisted record. */
        public record Integrity(int checked, int intact, int mismatch, int missingObject) {}
    }

    // ---- Use Case 13 — evidence lifecycle ------------------------------

    public enum LifecycleAction { SUBMIT, APPROVE, REJECT, RETIRE, RESET }

    public record LifecycleTransitionRequest(LifecycleAction action, String actor, String note) {}

    public record LifecycleEventView(String fromState, String toState, String action,
                                     String actor, String note, Instant occurredAt) {}

    public record EvidenceLifecycleView(
            String evidenceId,
            String applicationSlug,
            String controlId,
            String state,
            String effectiveState,
            boolean expired,
            String reviewedBy,
            Instant reviewedAt,
            String note,
            int retentionDays,
            Integer ageDays,
            Instant expiresAt,
            int currentVersion,
            List<LifecycleEventView> history) {}

    public record DeterministicQueryResult(
            String name,
            String answerText,
            Instant generatedAt,
            Map<String, Object> counts,
            List<Map<String, Object>> rows) {}
}
