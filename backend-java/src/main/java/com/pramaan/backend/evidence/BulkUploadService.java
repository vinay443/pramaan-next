package com.pramaan.backend.evidence;

import com.pramaan.backend.common.ApiException;
import com.pramaan.backend.evidence.EvidenceDtos.BulkIngestResponse.BulkError;
import com.pramaan.backend.evidence.EvidenceDtos.IngestOutcome;
import com.pramaan.backend.evidence.EvidenceDtos.IngestRequest;
import com.pramaan.backend.evidence.EvidenceDtos.IngestResult;
import com.pramaan.backend.evidence.EvidenceIngestionService.CatalogGuard;
import com.pramaan.backend.evidence.repo.EvidenceRecordRepository;
import com.pramaan.backend.insight.EvidenceEmbeddingIndexer;
import com.pramaan.backend.notification.NotificationService;
import com.pramaan.backend.scheduler.SchedulerRunRepository;
import com.pramaan.backend.scheduler.domain.SchedulerRun;
import com.pramaan.backend.scheduler.domain.SchedulerRun.Trigger;
import com.pramaan.backend.security.SecurityEvent.Type;
import com.pramaan.backend.security.SecurityEventService;
import com.pramaan.backend.util.Hashing;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * UC02 - bulk evidence upload. A batch is a {@link SchedulerRun} with {@link Trigger#BULK} (no new table): it gives
 * every upload a safe UUID batch id, a persisted status and counts, a per-file result map, the uploader and
 * timestamps, a retry link, and run notifications through {@link NotificationService}.
 *
 * <p>Deliberately NOT {@code @Transactional} (same reasoning as the scheduler executor): each file goes through
 * {@link EvidenceIngestionService#ingest}, which is transactional per call, so one bad or racing file never
 * affects its siblings and a unique-key race is resolved by retrying in a fresh transaction.
 */
@Service
public class BulkUploadService {

    private static final Logger log = LoggerFactory.getLogger(BulkUploadService.class);

    public static final String CHANNEL = "bulk-upload";
    private static final int MAX_NAME = 200;
    private static final String EICAR = "X5O!P%@AP[4\\PZX54(P^)7CC)7}$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*";

    static final Set<String> DEFAULT_EXTENSIONS = Set.of("pdf", "txt", "csv", "tsv", "json", "xml", "yaml", "yml",
            "log", "conf", "cfg", "ini", "properties", "md", "xlsx", "xls", "docx", "doc", "pptx", "png", "jpg",
            "jpeg", "zip");
    private static final Set<String> ZIP_BASED = Set.of("xlsx", "docx", "pptx", "zip");

    /** What a caller supplies for one multipart part (bytes already read). */
    public record UploadedFile(String name, String contentType, byte[] bytes) {}

    /** Common metadata for the whole batch. */
    public record BatchMeta(String applicationSlug, String framework, String controlId, String sourceSystem,
                            String technology, String environment, String uploadedBy, String retryOfBatchId,
                            Map<String, String> clientChecksums) {}

    /** One per-file outcome. {@code status}: CREATED | NEW_VERSION | DUPLICATE | REJECTED | FAILED. */
    public record FileOutcome(int index, String filename, String archive, String status, String code,
                              String message, String evidenceId, Integer version, String sha256, Long sizeBytes,
                              boolean retryable) {}

    public record BulkUploadResponse(String batchId, String status, String uploadedBy, int received, int created,
                                     int newVersions, int duplicates, int failed, List<IngestResult> results,
                                     List<BulkError> errors, List<FileOutcome> files) {}

    private record Item(String name, String entryName, String archive, String contentType, byte[] bytes,
                        String preErrorCode, String preError) {}

    private record Reject(String code, String message) {}

    private final EvidenceIngestionService ingestion;
    private final EvidenceLifecycleService lifecycle;
    private final EvidenceRecordRepository records;
    private final EvidenceEmbeddingIndexer indexer;
    private final SchedulerRunRepository runs;
    private final NotificationService notifications;
    private final SecurityEventService security;
    private final Clock clock;

    private final long maxFileBytes;
    private final int maxFiles;
    private final int maxZipEntries;
    private final long maxZipBytes;
    private final Set<String> allowedExtensions;
    private final String failMarker;
    /** Names already failed once by the simulated-outage hook (so the retry succeeds). */
    private final Set<String> failedOnce = ConcurrentHashMap.newKeySet();

    public BulkUploadService(EvidenceIngestionService ingestion, EvidenceLifecycleService lifecycle,
                             EvidenceRecordRepository records, EvidenceEmbeddingIndexer indexer,
                             SchedulerRunRepository runs, NotificationService notifications,
                             SecurityEventService security, Clock clock,
                             @Value("${pramaan.bulk-upload.max-file-bytes:10485760}") long maxFileBytes,
                             @Value("${pramaan.bulk-upload.max-files:500}") int maxFiles,
                             @Value("${pramaan.bulk-upload.max-zip-entries:200}") int maxZipEntries,
                             @Value("${pramaan.bulk-upload.max-zip-bytes:104857600}") long maxZipBytes,
                             @Value("${pramaan.bulk-upload.allowed-extensions:}") String allowedExtensions,
                             @Value("${pramaan.bulk-upload.simulate-failure-marker:}") String failMarker) {
        this.ingestion = ingestion;
        this.lifecycle = lifecycle;
        this.records = records;
        this.indexer = indexer;
        this.runs = runs;
        this.notifications = notifications;
        this.security = security;
        this.clock = clock;
        this.maxFileBytes = maxFileBytes;
        this.maxFiles = maxFiles;
        this.maxZipEntries = maxZipEntries;
        this.maxZipBytes = maxZipBytes;
        this.allowedExtensions = allowedExtensions == null || allowedExtensions.isBlank() ? DEFAULT_EXTENSIONS
                : Set.of(allowedExtensions.toLowerCase(Locale.ROOT).split("\\s*,\\s*"));
        this.failMarker = failMarker == null ? "" : failMarker.trim().toLowerCase(Locale.ROOT);
    }

    public BulkUploadResponse upload(BatchMeta in, List<UploadedFile> files) {
        // ---- batch-level validation: blocks the whole submission with a clear message (FT002 / FT014) ----
        String app = security.requireSafe("applicationSlug", in.applicationSlug());
        String framework = security.requireSafe("framework", in.framework());
        String control = security.requireSafe("controlId", in.controlId());
        String source = in.sourceSystem() == null || in.sourceSystem().isBlank() ? "BULK_UPLOAD"
                : security.requireSafe("sourceSystem", in.sourceSystem());
        String technology = blankToNull(in.technology()) == null ? null : security.requireSafe("technology", in.technology());
        String environment = blankToNull(in.environment()) == null ? null
                : security.requireSafe("environment", in.environment()).toUpperCase(Locale.ROOT);
        String user = blankToNull(in.uploadedBy()) == null ? "anonymous" : security.requireSafe("uploadedBy", in.uploadedBy());
        if (files == null || files.isEmpty()) {
            throw ApiException.badRequest("select at least one file (or a single .zip archive)");
        }
        if (files.size() > maxFiles) {
            security.record(Type.UPLOAD_REJECTED, "batch of " + files.size() + " files exceeds the limit of " + maxFiles);
            throw ApiException.badRequest("a batch may contain at most " + maxFiles + " files");
        }
        UUID retryOf = parseRetryOf(in.retryOfBatchId());

        // ---- the batch record ----
        Instant started = clock.instant();
        SchedulerRun run = new SchedulerRun(UUID.randomUUID(), Trigger.BULK, user, List.of(app), List.of(framework),
                List.of(source), started);
        run.setTriggeredBy(user);
        if (retryOf != null) run.markRetryOf(retryOf);
        run.markRunning(started);
        runs.save(run);
        String batchId = run.getId().toString();

        try {
            return process(run, batchId, app, framework, control, source, technology, environment, user,
                    in.clientChecksums() == null ? Map.of() : in.clientChecksums(), files);
        } catch (RuntimeException ex) {
            // never leave a batch RUNNING: it must show as FAILED and alert the recipients
            log.error("bulk upload batch {} aborted", batchId, ex);
            run.fail(clock.instant(), "batch aborted: " + trunc(String.valueOf(ex.getMessage()), 1800));
            runs.save(run);
            notifications.onRunFinished(run);
            throw ex;
        }
    }

    private BulkUploadResponse process(SchedulerRun run, String batchId, String app, String framework, String control,
                                       String source, String technology, String environment, String user,
                                       Map<String, String> checksums, List<UploadedFile> files) {
        List<Item> items = new ArrayList<>();
        for (UploadedFile f : files) {
            items.addAll(expand(f));
        }

        List<FileOutcome> outcomes = new ArrayList<>();
        List<IngestResult> results = new ArrayList<>();
        List<BulkError> errors = new ArrayList<>();
        Map<String, String> perFile = new LinkedHashMap<>();
        int created = 0, newVersions = 0, duplicates = 0, failed = 0;

        for (int i = 0; i < items.size(); i++) {
            Item it = items.get(i);
            String shown = it.entryName() != null ? it.entryName() : it.name();
            FileOutcome o = handle(i, it, run, batchId, app, framework, control, source, technology, environment,
                    user, checksums);
            outcomes.add(o);
            switch (o.status()) {
                case "CREATED" -> created++;
                case "NEW_VERSION" -> newVersions++;
                case "DUPLICATE" -> duplicates++;
                default -> {
                    failed++;
                    errors.add(new BulkError(i, shown + ": " + o.message()));
                }
            }
            if (o.evidenceId() != null) {
                results.add(new IngestResult(o.evidenceId(), null, app, control, framework, source,
                        IngestOutcome.valueOf(o.status()), o.version(), o.sha256(), o.sizeBytes(), shown));
            }
            perFile.put(uniqueKey(perFile, shown, i), summary(o));
        }

        run.complete(clock.instant(), items.size(), created + newVersions, duplicates, failed, perFile, failed,
                items.size());
        runs.save(run);
        notifications.onRunFinished(run); // best effort, idempotent per run
        return new BulkUploadResponse(batchId, run.getStatus().name(), user, items.size(), created, newVersions,
                duplicates, failed, results, errors, outcomes);
    }

    // ---- one file --------------------------------------------------------------------------------------------

    private FileOutcome handle(int index, Item it, SchedulerRun run, String batchId, String app, String framework,
                               String control, String source, String technology, String environment, String user,
                               Map<String, String> checksums) {
        String shown = it.entryName() != null ? it.entryName() : it.name();
        if (it.preError() != null) {
            return rejected(index, it, it.preErrorCode(), it.preError(), null);
        }
        Reject bad = validate(it);
        if (bad != null) {
            return rejected(index, it, bad.code(), bad.message(), null);
        }
        String sha = Hashing.sha256Hex(it.bytes());
        String claimed = checksums.get(it.name());
        if (claimed == null && it.entryName() != null) claimed = checksums.get(it.entryName());
        if (claimed != null && !claimed.isBlank() && !claimed.trim().equalsIgnoreCase(sha)) {
            return rejected(index, it, "INTEGRITY_MISMATCH", "SHA-256 supplied by the client does not match the received "
                    + "bytes (file changed in transit or the hash was tampered with)", sha);
        }

        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("upload.filename", it.name());
        metadata.put("upload.batchId", batchId);
        metadata.put("upload.uploadedBy", user);
        if (it.archive() != null) metadata.put("upload.archive", it.archive());
        if (claimed != null && !claimed.isBlank()) metadata.put("upload.clientSha256", claimed.trim().toLowerCase(Locale.ROOT));
        if (environment != null) metadata.put("upload.environment", environment);
        Map<String, String> tags = new LinkedHashMap<>();
        tags.put("ingest.channel", CHANNEL);
        tags.put("batchId", batchId);
        tags.put("uploadedBy", user);
        if (technology != null) tags.put("technology", technology);
        if (environment != null) tags.put("environment", environment);

        IngestRequest req = new IngestRequest(app, control, framework, source, shown, it.name(), it.contentType(),
                Base64.getEncoder().encodeToString(it.bytes()), null, clock.instant(), user, metadata, tags);
        try {
            simulateOutage(it.name());
            IngestResult r = ingestWithRetry(req, run.getId());
            if (r.outcome() == IngestOutcome.DUPLICATE) {
                auditDuplicate(r, run, user);
            } else {
                indexEmbedding(r);
            }
            return new FileOutcome(index, it.name(), it.archive(), r.outcome().name(), null,
                    r.outcome() == IngestOutcome.DUPLICATE
                            ? "identical to existing evidence (version " + r.version() + ") - not stored again"
                            : null,
                    r.evidenceId(), r.version(), r.sha256(), r.sizeBytes(), false);
        } catch (RuntimeException ex) {
            log.warn("bulk upload {}: {} failed: {}", batchId, shown, ex.getMessage());
            return new FileOutcome(index, it.name(), it.archive(), "FAILED", "PROCESSING_ERROR",
                    "could not be stored (" + trunc(String.valueOf(ex.getMessage()), 200) + ") - retry this file",
                    null, null, sha, (long) it.bytes().length, true);
        }
    }

    private FileOutcome rejected(int index, Item it, String code, String message, String sha) {
        String shown = it.entryName() != null ? it.entryName() : it.name();
        security.record(Type.UPLOAD_REJECTED, shown + ": " + code + " - " + message);
        return new FileOutcome(index, it.name(), it.archive(), "REJECTED", code, message, null, null, sha,
                it.bytes() == null ? null : (long) it.bytes().length, false);
    }

    /** FT014: type allow-list, size, empty, executable / malware signatures, magic-byte vs extension. */
    private Reject validate(Item it) {
        String name = it.name();
        String ext = extension(name);
        if (ext.isEmpty()) {
            return new Reject("TYPE_NOT_ALLOWED", "file has no extension; allowed types: " + sortedAllowed());
        }
        if (!allowedExtensions.contains(ext)) {
            return new Reject("TYPE_NOT_ALLOWED", "." + ext + " files are not accepted; allowed types: " + sortedAllowed());
        }
        byte[] b = it.bytes();
        if (b.length == 0) {
            return new Reject("EMPTY_FILE", "file is empty");
        }
        if (b.length > maxFileBytes) {
            return new Reject("TOO_LARGE", "file is " + b.length + " bytes; the limit is " + maxFileBytes + " bytes");
        }
        if (startsWith(b, 'M', 'Z') || startsWith(b, 0x7F, 'E', 'L', 'F') || startsWith(b, '#', '!')
                || startsWith(b, 0xCA, 0xFE, 0xBA, 0xBE)) {
            return new Reject("EXECUTABLE_CONTENT", "file content is an executable or script, not evidence");
        }
        String head = new String(b, 0, Math.min(b.length, 2048), StandardCharsets.ISO_8859_1);
        if (head.contains(EICAR)) {
            return new Reject("MALWARE_SIGNATURE", "file matches a known malware test signature");
        }
        boolean magicOk = switch (ext) {
            case "pdf" -> startsWith(b, '%', 'P', 'D', 'F');
            case "png" -> startsWith(b, 0x89, 'P', 'N', 'G');
            case "jpg", "jpeg" -> startsWith(b, 0xFF, 0xD8, 0xFF);
            default -> !ZIP_BASED.contains(ext) || startsWith(b, 'P', 'K', 0x03, 0x04);
        };
        if (!magicOk) {
            return new Reject("CONTENT_MISMATCH", "file content does not match its ." + ext + " extension");
        }
        return null;
    }

    private String sortedAllowed() {
        return String.join(", ", allowedExtensions.stream().sorted().toList());
    }

    // ---- expansion (plain file or zip) ----------------------------------------------------------------------

    private List<Item> expand(UploadedFile f) {
        String raw = f.name() == null ? "" : f.name();
        String bad = badName(raw);
        String name = baseName(raw);
        if (bad != null) {
            return List.of(new Item(name.isEmpty() ? "(unnamed)" : name, null, null, f.contentType(), f.bytes(),
                    "INVALID_NAME", bad));
        }
        if (!extension(name).equals("zip")) {
            return List.of(new Item(name, null, null, f.contentType(), f.bytes(), null, null));
        }
        List<Item> out = new ArrayList<>();
        long total = 0;
        int entries = 0;
        byte[] buf = new byte[8192];
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(f.bytes()))) {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                if (e.isDirectory() || e.getName().startsWith("__MACOSX/")) continue;
                if (++entries > maxZipEntries) {
                    out.add(new Item(name, null, name, null, null, "ARCHIVE_LIMIT",
                            "archive has more than " + maxZipEntries + " entries; the rest were not processed"));
                    break;
                }
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                boolean tooLarge = false;
                int n;
                while ((n = zis.read(buf)) > 0) {
                    total += n;
                    if (total > maxZipBytes) {
                        out.add(new Item(name, null, name, null, null, "ARCHIVE_LIMIT",
                                "archive expands beyond " + maxZipBytes + " bytes (possible zip bomb); processing stopped"));
                        return out;
                    }
                    if (!tooLarge) {
                        if (bos.size() + n > maxFileBytes) tooLarge = true;
                        else bos.write(buf, 0, n);
                    }
                }
                String entryName = e.getName();
                String entryBad = badName(entryName);
                String fileName = baseName(entryName);
                if (fileName.isBlank()) continue;
                if (entryBad != null) {
                    out.add(new Item(fileName, entryName, name, null, null, "INVALID_NAME", entryBad));
                } else if (extension(fileName).equals("zip")) {
                    out.add(new Item(fileName, entryName, name, null, null, "NESTED_ARCHIVE",
                            "nested archives are not accepted; extract it and upload its files"));
                } else if (tooLarge) {
                    out.add(new Item(fileName, entryName, name, null, null, "TOO_LARGE",
                            "file is larger than the limit of " + maxFileBytes + " bytes"));
                } else {
                    out.add(new Item(fileName, entryName, name, guessType(fileName), bos.toByteArray(), null, null));
                }
            }
        } catch (IOException | RuntimeException ex) {
            out.add(new Item(name, null, name, null, null, "BAD_ARCHIVE",
                    "archive could not be read (" + trunc(String.valueOf(ex.getMessage()), 120) + ")"));
        }
        if (out.isEmpty()) {
            out.add(new Item(name, null, name, null, null, "EMPTY_ARCHIVE", "archive contains no files"));
        }
        return out;
    }

    // ---- ingest helpers (same pattern as SchedulerRunExecutor) ------------------------------------------------

    /** A concurrent upload of the same new file loses the unique-key race; the retry sees the winner's row. */
    private IngestResult ingestWithRetry(IngestRequest req, UUID runId) {
        for (int attempt = 1; ; attempt++) {
            try {
                return ingestion.ingest(req, runId, null, CatalogGuard.LOG);
            } catch (DataIntegrityViolationException ex) {
                if (attempt >= 3) throw ex;
            }
        }
    }

    /** Opt-in outage simulation (pramaan.bulk-upload.simulate-failure-marker): each marked name fails once. */
    private void simulateOutage(String name) {
        if (!failMarker.isEmpty() && name.toLowerCase(Locale.ROOT).contains(failMarker)
                && failedOnce.add(name.toLowerCase(Locale.ROOT))) {
            throw new IllegalStateException("object store temporarily unavailable (simulated outage)");
        }
    }

    private void auditDuplicate(IngestResult r, SchedulerRun run, String user) {
        try {
            lifecycle.recordDuplicateSkipped(UUID.fromString(r.evidenceId()), run.getId(), "BULK", user);
        } catch (RuntimeException ex) {
            log.warn("duplicate-skipped audit failed for evidence {}: {}", r.evidenceId(), ex.getMessage());
        }
    }

    /** Best effort: ensureIndexed() catches the vector index up on the next search regardless. */
    private void indexEmbedding(IngestResult r) {
        try {
            records.findById(UUID.fromString(r.evidenceId())).ifPresent(indexer::indexOne);
        } catch (RuntimeException ex) {
            log.warn("embedding index failed for evidence {}: {}", r.evidenceId(), ex.getMessage());
        }
    }

    private UUID parseRetryOf(String id) {
        if (blankToNull(id) == null) return null;
        UUID uuid;
        try {
            uuid = UUID.fromString(id.trim());
        } catch (IllegalArgumentException e) {
            security.record(Type.INVALID_IDENTIFIER, "retryOfBatchId is not a UUID");
            throw ApiException.badRequest("retryOfBatchId must be a batch id (UUID)");
        }
        SchedulerRun prev = runs.findById(uuid)
                .orElseThrow(() -> ApiException.notFound("unknown batch: " + uuid));
        if (prev.getTrigger() != Trigger.BULK) {
            throw ApiException.badRequest("run " + uuid + " is not a bulk-upload batch");
        }
        return uuid;
    }

    // ---- small utilities --------------------------------------------------------------------------------------

    private static String summary(FileOutcome o) {
        return switch (o.status()) {
            case "CREATED", "NEW_VERSION", "DUPLICATE" ->
                    "outcome=%s version=%d sha256=%s size=%d received=1 ingested=%d duplicates=%d failed=0 connectivity=CONNECTED"
                            .formatted(o.status(), o.version(), o.sha256(), o.sizeBytes(),
                                    o.status().equals("DUPLICATE") ? 0 : 1, o.status().equals("DUPLICATE") ? 1 : 0);
            default -> "error: " + trunc(o.message().replaceAll("\\s+", " "), 300).replace(" connectivity=", " ")
                    + " connectivity=FAILED errorCode=" + o.code();
        };
    }

    /** scheduler_run_source.source_key is VARCHAR(120) and part of the primary key. */
    private static String uniqueKey(Map<String, String> existing, String shown, int index) {
        String key = trunc(shown, 110);
        return existing.containsKey(key) ? trunc(shown, 100) + " #" + (index + 1) : key;
    }

    /** Returns a reason when the raw name is unusable/unsafe, else null. */
    private static String badName(String raw) {
        if (raw == null || raw.isBlank()) return "file name is missing";
        if (raw.length() > 400) return "file name is too long";
        for (char c : raw.toCharArray()) {
            if (Character.isISOControl(c)) return "file name contains control characters";
        }
        String norm = raw.replace('\\', '/');
        if (norm.startsWith("/") || norm.matches("^[A-Za-z]:.*")) return "absolute paths are not allowed in file names";
        for (String seg : norm.split("/")) {
            if (seg.equals("..")) return "path traversal ('..') is not allowed in file names";
        }
        return baseName(raw).length() > MAX_NAME ? "file name is longer than " + MAX_NAME + " characters" : null;
    }

    static String baseName(String raw) {
        if (raw == null) return "";
        String n = raw.replace('\\', '/');
        return n.substring(n.lastIndexOf('/') + 1).trim();
    }

    static String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 || dot == name.length() - 1 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static String guessType(String fileName) {
        String t = java.net.URLConnection.guessContentTypeFromName(fileName);
        return t == null ? "application/octet-stream" : t;
    }

    private static boolean startsWith(byte[] b, int... prefix) {
        if (b.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) {
            if ((b[i] & 0xFF) != prefix[i]) return false;
        }
        return true;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    private static String trunc(String s, int max) {
        return s.length() > max ? s.substring(0, max) : s;
    }
}
