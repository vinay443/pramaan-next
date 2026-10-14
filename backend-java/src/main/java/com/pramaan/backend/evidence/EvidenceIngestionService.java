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

    /** Tags that describe the batch which created a version (see BulkUploadService); never moved by a duplicate.
     *  {@code environment} is here so re-uploading identical content from another environment (UAT then PROD) does not
     *  overwrite the environment the evidence was first stored under. */
    private static final java.util.Set<String> BATCH_SCOPED_TAGS = java.util.Set.of("batchId", "uploadedBy", "environment");

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
        if (!created && "unknown".equals(technology)) {
            // nothing was supplied: a new version must not replace a known technology with the "unknown" placeholder
            technology = record.getTags().stream()
                    .filter(t -> EvidenceNaming.TAG_TECHNOLOGY.equals(t.getTagKey()))
                    .map(EvidenceTag::getTagValue)
                    .filter(v -> v != null && !v.isBlank())
                    .findFirst().orElse(technology);
        }
        String collectionMethod = EvidenceNaming.resolveCollectionMethod(req.tags(), req.metadata());
        List<String> frameworks = normalizeFrameworks(frameworkOverride);
        if (frameworks.isEmpty()) {
            frameworks = controlFrameworks.frameworksFor(record.getControlId(), req.framework());
        }
        String evidenceType = EvidenceNaming.resolveEvidenceType(req.tags(), req.metadata(),
                record.getControlId(), req.sourceSystem());

        EvidenceVersion latest = record.latestVersion();
        if (latest != null && latest.getSha256().equalsIgnoreCase(sha)) {
            // a duplicate re-upload keeps the environment the evidence was first stored under (see BATCH_SCOPED_TAGS)
            String dupName = evidenceName(record, evidenceType, storedEnvironment(record, environmentOf(req)),
                    collectedAt);
            record.setTitle(dupName);
            Map<String, String> dupTags = new LinkedHashMap<>(standardTags(req, technology, frameworks, evidenceType,
                    dupName, collectionMethod, record.getCurrentVersion()));
            // a duplicate re-upload adds no version, so it must not re-home the record to the newer batch/uploader
            dupTags.keySet().removeAll(BATCH_SCOPED_TAGS);
            applyTags(record, dupTags);
            records.save(record);
            return result(record, IngestOutcome.DUPLICATE, latest);
        }

        int nextVersion = record.getCurrentVersion() + 1;
        String objectKey = "evidence/%s/v%d/%s".formatted(record.getId(), nextVersion, sha);
        String contentType = req.contentType() != null ? req.contentType()
                : (req.contentText() != null ? "text/plain" : "application/octet-stream");
        // Known gap (deferred): the object is written before the DB work commits, so a later failure rolls the rows back
        // but leaves this immutable object behind. The put cannot simply move to beforeCommit: autoEvaluate() below
        // reads the latest object through RuleEvaluationService.latestContent. The store is immutable (no delete), so
        // the fix is an orphan sweep (needs ObjectStore.list) - out of scope for now. A retry of a NEW VERSION reuses
        // this exact key (record id + version + sha) and so adds no second object; only a failed first CREATE orphans one.
        objectStore.put(objectKey, content, contentType);

        EvidenceVersion version = new EvidenceVersion(UUID.randomUUID(), nextVersion, sha, contentType,
                content.length, objectKey, collectedAt, req.collectedBy(), ingestionRunId,
                sanitize(req.metadata()), now);
        record.addVersion(version);
        String reqEnv = environmentOf(req);
        String standardName = evidenceName(record, evidenceType,
                reqEnv != null ? reqEnv : storedEnvironment(record, null), collectedAt);
        record.setTitle(standardName);
        applyTags(record, standardTags(req, technology, frameworks, evidenceType, standardName,
                collectionMethod, nextVersion));
        records.save(record);
        lifecycle.recordIngestion(record, created, now, req.collectedBy());
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

    /** Fields a metadata edit may change ({@code title} is the human source title, the {@code sourceTitle} tag). */
    static final java.util.Set<String> EDITABLE_METADATA =
            java.util.Set.of("title", "environment", "technology", "evidenceType", "frameworks");
    private static final int MAX_REASON = 500;
    private static final int MAX_AUDIT_VALUE = 120;
    private static final com.fasterxml.jackson.databind.ObjectMapper AUDIT_JSON =
            new com.fasterxml.jackson.databind.ObjectMapper();

    /**
     * FT006 - edit an evidence record's metadata with a mandatory reason. Reuses the ingest naming/tag helpers: an
     * environment or evidence-type change recomputes the name prefix under the same per-application naming lock,
     * keeping the record's sequence when the new prefix is free and otherwise taking the next one. The edit is
     * audited as a METADATA_EDITED lifecycle event (old/new per field, role, reason); content, hashes and versions
     * are never touched.
     *
     * <p>Callers are limited to APP and ADMIN by SchedulerAccessPolicy. APP ownership of the application is NOT
     * enforced: {@code applications.owner} is free text, with no user-to-application mapping (docs/METADATA_EDIT.md).
     *
     * @return false when nothing actually changed (no event, no state change)
     */
    @Transactional
    public boolean updateMetadata(UUID evidenceId, Map<String, Object> changes, String reason, String actor,
                                  String role) {
        if (changes == null || changes.isEmpty()) {
            throw ApiException.badRequest("changes must contain at least one field; editable: "
                    + EDITABLE_METADATA.stream().sorted().toList());
        }
        for (String key : changes.keySet()) {
            if (!EDITABLE_METADATA.contains(key)) {
                throw ApiException.badRequest("'" + key + "' cannot be edited; editable fields: "
                        + EDITABLE_METADATA.stream().sorted().toList());
            }
        }
        String why = reason == null ? "" : reason.trim();
        if (why.isEmpty()) {
            throw ApiException.badRequest("reason is required");
        }
        if (why.length() > MAX_REASON || why.chars().anyMatch(Character::isISOControl)) {
            throw ApiException.badRequest("reason must be at most " + MAX_REASON + " characters of plain text");
        }
        EvidenceRecord record = records.findById(evidenceId)
                .orElseThrow(() -> ApiException.notFound("Unknown evidence: " + evidenceId));
        if (record.getLifecycleState() == com.pramaan.backend.evidence.domain.EvidenceLifecycleState.ARCHIVED) {
            throw ApiException.conflict("cannot edit metadata of evidence in state ARCHIVED");
        }

        Map<String, String> tags = new LinkedHashMap<>();
        record.getTags().forEach(t -> tags.put(t.getTagKey(), t.getTagValue()));
        String primary = record.getFramework() == null ? "" : record.getFramework().trim().toUpperCase(Locale.ROOT);

        String oldTitle = tags.getOrDefault(EvidenceNaming.TAG_SOURCE_TITLE, "");
        String oldEnv = storedEnvironment(record, null);
        String oldTech = tags.getOrDefault(EvidenceNaming.TAG_TECHNOLOGY, "unknown");
        String oldType = tags.containsKey(EvidenceNaming.TAG_EVIDENCE_TYPE)
                ? tags.get(EvidenceNaming.TAG_EVIDENCE_TYPE)
                : EvidenceNaming.resolveEvidenceType(tags, null, record.getControlId(), record.getSourceSystem());
        List<String> oldFrameworks = frameworkList(tags.get(EvidenceNaming.TAG_FRAMEWORKS), primary);

        String newTitle = oldTitle;
        String newEnv = oldEnv;
        String newTech = oldTech;
        String newType = oldType;
        List<String> newFrameworks = oldFrameworks;
        if (changes.containsKey("title")) {
            newTitle = editText("title", changes.get("title"), 150);
        }
        if (changes.containsKey("environment")) {
            Object v = changes.get("environment");
            String env = v == null ? "" : v.toString().trim().toUpperCase(Locale.ROOT);
            if (!env.isEmpty() && !BulkUploadService.ENVIRONMENTS.contains(env)) {
                throw ApiException.badRequest("environment '" + env + "' is not valid; choose one of "
                        + BulkUploadService.ENVIRONMENTS + " or leave it blank");
            }
            newEnv = env.isEmpty() ? null : env;
        }
        if (changes.containsKey("technology")) {
            newTech = editText("technology", changes.get("technology"), 60).toLowerCase(Locale.ROOT);
        }
        if (changes.containsKey("evidenceType")) {
            newType = EvidenceNaming.typeToken(editText("evidenceType", changes.get("evidenceType"), 60));
        }
        if (changes.containsKey("frameworks")) {
            newFrameworks = editedFrameworks(record, primary, changes.get("frameworks"));
        }

        Map<String, String[]> diff = new LinkedHashMap<>(); // field -> {old, new}
        if (!java.util.Objects.equals(oldTitle, newTitle)) diff.put("title", new String[]{oldTitle, newTitle});
        if (!java.util.Objects.equals(oldEnv, newEnv)) diff.put("environment", new String[]{oldEnv, newEnv});
        if (!java.util.Objects.equals(oldTech, newTech)) diff.put("technology", new String[]{oldTech, newTech});
        if (!java.util.Objects.equals(oldType, newType)) diff.put("evidenceType", new String[]{oldType, newType});
        if (!oldFrameworks.equals(newFrameworks)) {
            diff.put("frameworks", new String[]{String.join(",", oldFrameworks), String.join(",", newFrameworks)});
        }
        if (diff.isEmpty()) {
            return false;
        }

        String oldName = record.getTitle();
        String newName = oldName;
        if (diff.containsKey("environment") || diff.containsKey("evidenceType")) {
            Instant named = record.getLatestCollectedAt() != null ? record.getLatestCollectedAt() : record.getCreatedAt();
            String prefix = EvidenceNaming.namePrefix(record.getApplicationSlug(), record.getControlId(), newType,
                    newEnv, named);
            if (EvidenceNaming.sequenceOf(prefix, oldName) == 0) {
                // same lock as ingest: the next allocator sees this record's new title once we commit
                applications.lockForNaming(record.getApplicationSlug());
                List<String> others = records.titlesWithPrefix(record.getApplicationSlug(), record.getId(),
                        EvidenceNaming.titlePattern(prefix));
                int seq = EvidenceNaming.resolveEditedSequence(prefix, oldName, others);
                newName = EvidenceNaming.standardName(record.getApplicationSlug(), record.getControlId(), newType,
                        newEnv, named, seq);
            }
        }

        Map<String, String> next = new LinkedHashMap<>(tags);
        next.put(EvidenceNaming.TAG_NAME, newName);
        if (newTitle == null || newTitle.isBlank()) next.remove(EvidenceNaming.TAG_SOURCE_TITLE);
        else next.put(EvidenceNaming.TAG_SOURCE_TITLE, newTitle);
        next.put(EvidenceNaming.TAG_TECHNOLOGY, newTech);
        next.put(EvidenceNaming.TAG_EVIDENCE_TYPE, newType);
        next.put(EvidenceNaming.TAG_FRAMEWORKS, String.join(",", newFrameworks));
        if (newEnv == null) next.remove("environment"); else next.put("environment", newEnv);
        List<EvidenceTag> merged = new ArrayList<>();
        next.forEach((k, v) -> merged.add(new EvidenceTag(UUID.randomUUID(), k, v)));
        record.replaceTags(merged);
        record.setTitle(newName);

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("reason", why);
        audit.put("role", role == null ? null : role.trim());
        Map<String, Object> fields = new LinkedHashMap<>();
        diff.forEach((k, v) -> fields.put(k, auditValues(v[0], v[1])));
        if (!java.util.Objects.equals(oldName, newName)) fields.put("name", auditValues(oldName, newName));
        audit.put("changes", fields);
        String note;
        try {
            note = AUDIT_JSON.writeValueAsString(audit);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("cannot serialise the metadata audit payload", e);
        }
        lifecycle.recordMetadataEdit(record, actor == null || actor.isBlank() ? "anonymous" : actor.trim(), note,
                clock.instant());
        return true;
    }

    private static Map<String, String> auditValues(String oldValue, String newValue) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("old", auditTrunc(oldValue));
        m.put("new", auditTrunc(newValue));
        return m;
    }

    private static String auditTrunc(String v) {
        return v == null || v.length() <= MAX_AUDIT_VALUE ? v : v.substring(0, MAX_AUDIT_VALUE) + "…";
    }

    private static String editText(String field, Object value, int max) {
        String v = value == null ? "" : value.toString().trim();
        if (v.isEmpty()) {
            throw ApiException.badRequest(field + " must not be blank");
        }
        if (v.length() > max || v.chars().anyMatch(Character::isISOControl)) {
            throw ApiException.badRequest(field + " must be at most " + max + " characters of plain text");
        }
        return v;
    }

    /** Framework list from the {@code frameworks} tag, de-duplicated, upper case, the record's primary first. */
    private static List<String> frameworkList(String tag, String primary) {
        List<String> out = new ArrayList<>();
        if (!primary.isBlank()) out.add(primary);
        if (tag != null) {
            for (String f : tag.split(",")) {
                String s = f.trim().toUpperCase(Locale.ROOT);
                if (!s.isBlank() && !out.contains(s)) out.add(s);
            }
        }
        return out;
    }

    private List<String> editedFrameworks(EvidenceRecord record, String primary, Object value) {
        List<String> given = new ArrayList<>();
        if (value instanceof java.util.Collection<?> c) {
            c.forEach(o -> given.add(String.valueOf(o)));
        } else if (value != null) {
            given.addAll(List.of(value.toString().split(",")));
        }
        List<String> wanted = new ArrayList<>();
        for (String g : given) {
            String f = g.trim().toUpperCase(Locale.ROOT);
            if (f.isEmpty()) continue;
            if (!controlFrameworks.frameworks().contains(f)) {
                throw ApiException.badRequest("'" + f + "' is not a known framework; choose from "
                        + controlFrameworks.frameworks());
            }
            if (!wanted.contains(f)) wanted.add(f);
        }
        if (wanted.isEmpty()) {
            throw ApiException.badRequest("frameworks must list at least the record's framework " + primary);
        }
        if (!wanted.contains(primary)) {
            throw ApiException.badRequest("frameworks must keep the record's primary framework " + primary);
        }
        if (!controlFrameworks.maps(record.getControlId(), primary)
                && !controlCatalog.isConformant(primary, record.getControlId())) {
            throw ApiException.badRequest("control " + record.getControlId() + " does not belong to framework "
                    + primary);
        }
        wanted.remove(primary);
        wanted.add(0, primary);
        return wanted;
    }

    /**
     * Canonical name for the record. The trailing sequence is the record's own (kept across versions);
     * a record new to this name prefix takes the next free number among the application's other records.
     */
    private String evidenceName(EvidenceRecord record, String evidenceType, String environment, Instant collectedAt) {
        String prefix = EvidenceNaming.namePrefix(record.getApplicationSlug(), record.getControlId(),
                evidenceType, environment, collectedAt);
        List<String> others = new ArrayList<>();
        if (EvidenceNaming.sequenceOf(prefix, record.getTitle()) == 0) {
            // FT015: concurrent uploads must not read the same max(seq). The lock is held until this ingest commits,
            // so the next allocator sees this record's title. Existing titles are never renumbered.
            applications.lockForNaming(record.getApplicationSlug());
            // only the titles under this prefix matter to the sequence; never load the application's whole evidence set
            others.addAll(records.titlesWithPrefix(record.getApplicationSlug(), record.getId(),
                    EvidenceNaming.titlePattern(prefix)));
        }
        int seq = EvidenceNaming.resolveSequence(prefix, record.getTitle(), others);
        return EvidenceNaming.standardName(record.getApplicationSlug(), record.getControlId(), evidenceType,
                environment, collectedAt, seq);
    }

    private static String environmentOf(IngestRequest req) {
        String v = req.tags() == null ? null : req.tags().get("environment");
        return v == null || v.isBlank() ? null : v.trim().toUpperCase(Locale.ROOT);
    }

    private static String storedEnvironment(EvidenceRecord record, String fallback) {
        return record.getTags().stream()
                .filter(t -> "environment".equals(t.getTagKey()))
                .map(EvidenceTag::getTagValue)
                .filter(v -> v != null && !v.isBlank())
                .map(v -> v.trim().toUpperCase(Locale.ROOT))
                .findFirst().orElse(fallback);
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
