package com.pramaan.backend.evidence;

import com.pramaan.backend.application.ApplicationEntity;
import com.pramaan.backend.application.ApplicationService;
import com.pramaan.backend.common.ApiException;
import com.pramaan.backend.evidence.EvidenceDtos.BulkIngestResponse;
import com.pramaan.backend.evidence.EvidenceDtos.EvidenceView;
import com.pramaan.backend.evidence.EvidenceDtos.IngestOutcome;
import com.pramaan.backend.evidence.EvidenceDtos.IngestRequest;
import com.pramaan.backend.evidence.EvidenceDtos.IngestResult;
import com.pramaan.backend.evidence.domain.EvidenceRecord;
import com.pramaan.backend.evidence.domain.EvidenceTag;
import com.pramaan.backend.evidence.domain.EvidenceVersion;
import com.pramaan.backend.evidence.repo.EvidenceRecordRepository;
import com.pramaan.backend.rules.CheckDtos.EvaluateRequest;
import com.pramaan.backend.rules.RuleEvaluationService;
import com.pramaan.backend.storage.ObjectStore;
import com.pramaan.backend.util.Hashing;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Evidence ingestion: dedup by SHA-256, immutable versioning, object-store write, metadata tagging. */
@Service
public class EvidenceIngestionService {

    private static final Logger log = LoggerFactory.getLogger(EvidenceIngestionService.class);

    private final EvidenceRecordRepository records;
    private final ApplicationService applications;
    private final ObjectStore objectStore;
    private final EvidenceLifecycleService lifecycle;
    private final RuleEvaluationService ruleEvaluation;
    private final ControlFrameworkCatalog controlFrameworks;
    private final EvidenceControlCatalog controlCatalog;
    private final Clock clock;

    public EvidenceIngestionService(EvidenceRecordRepository records, ApplicationService applications,
                                    ObjectStore objectStore, EvidenceLifecycleService lifecycle,
                                    RuleEvaluationService ruleEvaluation,
                                    ControlFrameworkCatalog controlFrameworks,
                                    EvidenceControlCatalog controlCatalog, Clock clock) {
        this.records = records;
        this.applications = applications;
        this.objectStore = objectStore;
        this.lifecycle = lifecycle;
        this.ruleEvaluation = ruleEvaluation;
        this.controlFrameworks = controlFrameworks;
        this.controlCatalog = controlCatalog;
        this.clock = clock;
    }

    /** How strictly {@link #ingest} checks (framework, controlId) against the xlsx
     *  bank catalog ({@link EvidenceControlCatalog}, docs/ECS_Control_Library.xlsx). */
    public enum CatalogGuard {
        /** No check — control/framework may be anything (predefined-query catalog,
         *  RBAC approval-scope testing, the dev demo-seed's own free-form callers). */
        NONE,
        /** Ingest proceeds; a non-conformant (framework, controlId) is logged as a
         *  warning so drift is discoverable without breaking existing callers that
         *  legitimately use frameworks/controls outside this catalog. */
        LOG,
        /** Reject with 400 if (framework, controlId) isn't one of the 133 xlsx
         *  bank-catalog controls. Reserved for the Scheduler — the one path where
         *  silent catalog drift is the actual risk this guard exists to prevent. */
        REJECT
    }

    /** Unguarded (NONE): used by predefined-query runs, the dev demo-seed, and the
     *  generic single-item /ingest API (also exercised by approval-RBAC tests with
     *  frameworks outside this catalog, e.g. ISG) — see {@link CatalogGuard}. */
    @Transactional
    public IngestResult ingest(IngestRequest req) {
        return ingest(req, null, null, CatalogGuard.NONE);
    }

    /** Scheduler path — always catalog-guarded, see {@link CatalogGuard#REJECT}. */
    @Transactional
    public IngestResult ingest(IngestRequest req, UUID ingestionRunId) {
        return ingest(req, ingestionRunId, null, CatalogGuard.REJECT);
    }

    /**
     * @param frameworkOverride when non-empty, the exact framework set to tag this
     *        evidence with (used for collectors whose controls aren't in
     *        {@link ControlFrameworkCatalog}, e.g. predefined-query runs). When null
     *        the mapping is resolved from the catalogue as usual.
     */
    @Transactional
    public IngestResult ingest(IngestRequest req, UUID ingestionRunId, List<String> frameworkOverride) {
        return ingest(req, ingestionRunId, frameworkOverride, CatalogGuard.NONE);
    }

    @Transactional
    public IngestResult ingest(IngestRequest req, UUID ingestionRunId, List<String> frameworkOverride,
                               CatalogGuard guard) {
        requireField("applicationSlug", req.applicationSlug());
        requireField("controlId", req.controlId());
        requireField("framework", req.framework());
        requireField("sourceSystem", req.sourceSystem());
        if (guard != CatalogGuard.NONE && !controlCatalog.isConformant(req.framework(), req.controlId())) {
            if (guard == CatalogGuard.REJECT) {
                throw ApiException.badRequest("control " + req.controlId() + " / framework " + req.framework()
                        + " is not part of the ECS control catalog (docs/ECS_Control_Library.xlsx) — "
                        + "evidence must be tagged with one of the 133 bank-catalog controls");
            }
            log.warn("non-catalog evidence ingested: control={} framework={} application={} source={} "
                            + "(not in the 133-control ECS bank catalog, docs/ECS_Control_Library.xlsx)",
                    req.controlId(), req.framework(), req.applicationSlug(), req.sourceSystem());
        }
        byte[] content = decodeContent(req);
        String sha = Hashing.sha256Hex(content);
        Instant now = clock.instant();
        Instant collectedAt = req.collectedAt() != null ? req.collectedAt() : now;

        ApplicationEntity app = applications.getOrAutoCreate(req.applicationSlug().trim());
        String key = evidenceKey(app.getSlug(), req.framework(), req.controlId(),
                req.sourceSystem(), req.sourceObjectId());

        EvidenceRecord record = records.findByEvidenceKey(key).orElse(null);
        boolean created = record == null;
        if (created) {
            record = new EvidenceRecord(UUID.randomUUID(), key, app.getId(), app.getSlug(),
                    norm(req.controlId()), norm(req.framework()), norm(req.sourceSystem()),
                    req.sourceObjectId(), req.title(), now);
        }

        String technology = EvidenceNaming.resolveTechnology(req.tags(), req.metadata(), req.sourceSystem());
        String collectionMethod = EvidenceNaming.resolveCollectionMethod(req.tags(), req.metadata());
        List<String> frameworks = normalizeFrameworks(frameworkOverride);
        if (frameworks.isEmpty()) {
            frameworks = controlFrameworks.frameworksFor(record.getControlId(), req.framework());
        }
        String evidenceType = EvidenceNaming.resolveEvidenceType(req.tags(), req.metadata(),
                record.getControlId(), req.sourceSystem());

        EvidenceVersion latest = record.latestVersion();
        if (latest != null && latest.getSha256().equalsIgnoreCase(sha)) {
            String dupName = EvidenceNaming.standardName(record.getApplicationSlug(),
                    record.getControlId(), evidenceType, collectedAt, record.getCurrentVersion());
            record.setTitle(dupName);
            applyTags(record, standardTags(req, technology, frameworks, evidenceType, dupName,
                    collectionMethod, record.getCurrentVersion()));
            records.save(record);
            return result(record, IngestOutcome.DUPLICATE, latest);
        }

        int nextVersion = record.getCurrentVersion() + 1;
        String objectKey = "evidence/%s/v%d/%s".formatted(record.getId(), nextVersion, sha);
        String contentType = req.contentType() != null ? req.contentType()
                : (req.contentText() != null ? "text/plain" : "application/octet-stream");
        objectStore.put(objectKey, content, contentType);

        EvidenceVersion version = new EvidenceVersion(UUID.randomUUID(), nextVersion, sha, contentType,
                content.length, objectKey, collectedAt, req.collectedBy(), ingestionRunId,
                sanitize(req.metadata()), now);
        record.addVersion(version);
        String standardName = EvidenceNaming.standardName(record.getApplicationSlug(),
                record.getControlId(), evidenceType, collectedAt, nextVersion);
        record.setTitle(standardName);
        applyTags(record, standardTags(req, technology, frameworks, evidenceType, standardName,
                collectionMethod, nextVersion));
        records.save(record);
        lifecycle.recordIngestion(record, created, now);
        autoEvaluate(record);

        return result(record, created ? IngestOutcome.CREATED : IngestOutcome.NEW_VERSION, version);
    }

    /**
     * Re-run deterministic rule evaluation for the ingested control so the
     * completeness / compliance / leadership rollups reflect new evidence without a
     * manual "Re-evaluate" step. Scoped to (application, control) so a scheduler run
     * or bulk upload only re-evaluates what it touched. Joins the ingest transaction:
     * evaluation is deterministic and handles missing content gracefully, so a
     * failure here is a genuine defect and rolls the ingest back rather than
     * persisting evidence with a silently stale posture.
     */
    private void autoEvaluate(EvidenceRecord record) {
        ruleEvaluation.evaluate(new EvaluateRequest(record.getApplicationSlug(), null,
                record.getControlId(), null));
    }

    /**
     * Manual/bulk-upload path ({@code /evidence/bulk}, {@code /evidence/bulk/upload}) —
     * catalog-checked with {@link CatalogGuard#LOG}, not {@code REJECT}: this endpoint
     * doubles as a generic bulk-mechanics test harness (dedup, partial-failure
     * reporting, zip expansion) using arbitrary non-catalog control IDs, so a hard
     * reject here would break that unrelated coverage. Non-conformant items still
     * ingest; they're just logged, matching the Scheduler item's log line.
     */
    @Transactional
    public BulkIngestResponse ingestBulk(List<IngestRequest> items) {
        List<IngestResult> ok = new ArrayList<>();
        List<BulkIngestResponse.BulkError> errors = new ArrayList<>();
        int created = 0, newVersions = 0, duplicates = 0;
        for (int i = 0; i < items.size(); i++) {
            try {
                IngestResult r = ingest(items.get(i), null, null, CatalogGuard.LOG);
                ok.add(r);
                switch (r.outcome()) {
                    case CREATED -> created++;
                    case NEW_VERSION -> newVersions++;
                    case DUPLICATE -> duplicates++;
                }
            } catch (RuntimeException ex) {
                errors.add(new BulkIngestResponse.BulkError(i, ex.getMessage()));
            }
        }
        return new BulkIngestResponse(items.size(), created, newVersions, duplicates,
                errors.size(), ok, errors);
    }

    /**
     * UC03 metadata tagging — associate an existing evidence record with an
     * additional compliance framework its control satisfies ("reuse this evidence
     * for framework X" instead of re-collecting). Merges into the canonical
     * {@code frameworks} tag via the same tag path as ingestion — no parallel
     * tagging mechanism. Idempotent. The framework must be one the control maps to
     * in {@link ControlFrameworkCatalog} (when the control is catalogued).
     */
    @Transactional
    public EvidenceView addFrameworkMapping(UUID evidenceId, String framework) {
        if (framework == null || framework.isBlank()) {
            throw ApiException.badRequest("framework is required");
        }
        String fw = framework.trim().toUpperCase(Locale.ROOT);
        EvidenceRecord record = records.findById(evidenceId)
                .orElseThrow(() -> ApiException.notFound("Unknown evidence: " + evidenceId));

        List<String> allowed = controlFrameworks.frameworksFor(record.getControlId(), record.getFramework());
        if (controlFrameworks.isMapped(record.getControlId()) && !allowed.contains(fw)) {
            throw ApiException.badRequest(fw + " is not a framework that control "
                    + record.getControlId() + " maps to " + allowed);
        }

        Map<String, String> current = new LinkedHashMap<>();
        record.getTags().forEach(t -> current.put(t.getTagKey(), t.getTagValue()));
        List<String> frameworks = new ArrayList<>();
        String existing = current.getOrDefault(EvidenceNaming.TAG_FRAMEWORKS, "");
        for (String f : existing.split(",")) {
            String s = f.trim().toUpperCase(Locale.ROOT);
            if (!s.isBlank() && !frameworks.contains(s)) {
                frameworks.add(s);
            }
        }
        if (record.getFramework() != null && !frameworks.contains(record.getFramework().toUpperCase(Locale.ROOT))) {
            frameworks.add(0, record.getFramework().toUpperCase(Locale.ROOT));
        }
        if (!frameworks.contains(fw)) {
            frameworks.add(fw);
        }
        applyTags(record, Map.of(EvidenceNaming.TAG_FRAMEWORKS, String.join(",", frameworks)));
        records.save(record);
        return EvidenceView.from(record);
    }

    /** Caller tags plus the canonical Use Case 3 tag set (canonical keys win). */
    private static Map<String, String> standardTags(IngestRequest req, String technology,
                                                    List<String> frameworks, String evidenceType,
                                                    String name, String collectionMethod, int version) {
        Map<String, String> merged = new LinkedHashMap<>();
        if (req.tags() != null) {
            req.tags().forEach((k, v) -> {
                if (k != null && !k.isBlank()) {
                    merged.put(k.trim(), v);
                }
            });
        }
        merged.putAll(EvidenceNaming.standardTags(req.applicationSlug(), technology, frameworks,
                req.controlId(), evidenceType, name, req.title(), collectionMethod, version));
        return merged;
    }

    private void applyTags(EvidenceRecord record, Map<String, String> tags) {
        if (tags == null) {
            return;
        }
        List<EvidenceTag> merged = new ArrayList<>();
        Map<String, String> combined = new LinkedHashMap<>();
        record.getTags().forEach(t -> combined.put(t.getTagKey(), t.getTagValue()));
        tags.forEach((k, v) -> {
            if (k != null && !k.isBlank()) {
                combined.put(k.trim(), v);
            }
        });
        combined.forEach((k, v) -> merged.add(new EvidenceTag(UUID.randomUUID(), k, v)));
        record.replaceTags(merged);
    }

    private IngestResult result(EvidenceRecord r, IngestOutcome outcome, EvidenceVersion v) {
        return new IngestResult(r.getId().toString(), r.getEvidenceKey(), r.getApplicationSlug(),
                r.getControlId(), r.getFramework(), r.getSourceSystem(), outcome,
                v.getVersionNumber(), v.getSha256(), v.getSizeBytes(), r.getSourceObjectId());
    }

    private byte[] decodeContent(IngestRequest req) {
        if (req.contentBase64() != null && !req.contentBase64().isBlank()) {
            try {
                return Base64.getDecoder().decode(req.contentBase64().trim());
            } catch (IllegalArgumentException e) {
                throw ApiException.badRequest("contentBase64 is not valid Base64");
            }
        }
        if (req.contentText() != null) {
            return req.contentText().getBytes(StandardCharsets.UTF_8);
        }
        throw ApiException.badRequest("one of contentBase64 or contentText is required");
    }

    private static Map<String, String> sanitize(Map<String, String> in) {
        if (in == null) {
            return Map.of();
        }
        Map<String, String> out = new LinkedHashMap<>();
        in.forEach((k, v) -> {
            if (k != null && !k.isBlank()) {
                out.put(k.trim(), v);
            }
        });
        return out;
    }

    private static String norm(String s) {
        return s == null ? null : s.trim();
    }

    private static List<String> normalizeFrameworks(List<String> in) {
        if (in == null) {
            return List.of();
        }
        return in.stream()
                .map(s -> s == null ? "" : s.trim())
                .filter(s -> !s.isBlank())
                .distinct()
                .toList();
    }

    private static void requireField(String name, String value) {
        if (value == null || value.isBlank()) {
            throw ApiException.badRequest(name + " is required");
        }
    }

    static String evidenceKey(String appSlug, String framework, String controlId,
                              String sourceSystem, String sourceObjectId) {
        return String.join("|",
                appSlug.toLowerCase(),
                framework.trim().toUpperCase(),
                controlId.trim().toUpperCase(),
                sourceSystem.trim().toUpperCase(),
                sourceObjectId == null || sourceObjectId.isBlank() ? "-" : sourceObjectId.trim());
    }
}
