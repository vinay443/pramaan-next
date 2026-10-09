package com.pramaan.backend.insight;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pramaan.backend.common.ApiException;
import com.pramaan.backend.evidence.EvidenceDtos.IntegrityReport;
import com.pramaan.backend.evidence.EvidenceNaming;
import com.pramaan.backend.evidence.EvidenceQueryService;
import com.pramaan.backend.evidence.domain.EvidenceRecord;
import com.pramaan.backend.evidence.domain.EvidenceTag;
import com.pramaan.backend.evidence.domain.EvidenceVersion;
import com.pramaan.backend.insight.InsightDtos.EvidenceSummary;
import com.pramaan.backend.insight.InsightDtos.SummaryAlsoSatisfies;
import com.pramaan.backend.insight.InsightDtos.SummaryCheck;
import com.pramaan.backend.insight.InsightDtos.SummaryControl;
import com.pramaan.backend.insight.InsightDtos.SummaryFact;
import com.pramaan.backend.insight.InsightDtos.SummaryFinding;
import com.pramaan.backend.insight.InsightDtos.SummaryHealth;
import com.pramaan.backend.insight.summary.BinaryMetadataSummarizer;
import com.pramaan.backend.insight.summary.ContentKind;
import com.pramaan.backend.insight.summary.ContentProbe;
import com.pramaan.backend.insight.summary.ContentSummarizer;
import com.pramaan.backend.insight.summary.Extraction;
import com.pramaan.backend.insight.summary.FactSink;
import com.pramaan.backend.insight.summary.Redactor;
import com.pramaan.backend.insight.summary.SummaryContext;
import com.pramaan.backend.insight.summary.SummaryEnums.ControlSource;
import com.pramaan.backend.insight.summary.SummaryEnums.Freshness;
import com.pramaan.backend.insight.summary.SummaryEnums.Integrity;
import com.pramaan.backend.insight.summary.SummaryEnums.Severity;
import com.pramaan.backend.insight.summary.SummaryEnums.Verdict;
import com.pramaan.backend.insight.summary.SummaryTemplates;
import com.pramaan.backend.rules.CheckStatus;
import com.pramaan.backend.rules.domain.CheckResult;
import com.pramaan.backend.rules.repo.CheckResultRepository;
import com.pramaan.backend.evidence.repo.EvidenceRecordRepository;
import com.pramaan.backend.storage.ObjectStore;
import com.pramaan.backend.util.Hashing;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deterministic, rule- and template-based evidence summary. Everything shown is extracted from the
 * stored bytes, the persisted check verdicts, the evidence metadata or the control library and
 * rendered through fixed templates ({@link SummaryTemplates}). No model is involved, and a GET never
 * writes (rule evaluation is not triggered from here).
 *
 * <p>Only the content-derived part is cached (bounded LRU); health, verdict and the sentences that
 * depend on them are assembled on every call.
 */
@Service
@Transactional(readOnly = true)
public class EvidenceSummaryService {

    public static final String METHOD = "DETERMINISTIC_RULES";
    public static final String MODEL = "deterministic-rules/v1";
    /** Most bytes read from the object store for one summary. */
    public static final int READ_LIMIT = 1024 * 1024;
    /** PDF and Office (zip) files are read further: their structure sits across the whole file. */
    public static final int DOCUMENT_READ_LIMIT = 20 * 1024 * 1024;
    /** Most facts in one response (content facts are capped lower so record-level facts always fit). */
    public static final int MAX_FACTS = 60;
    public static final int CACHE_MAX_ENTRIES = 2000;

    private final EvidenceRecordRepository records;
    private final CheckResultRepository checkResults;
    private final ObjectStore objectStore;
    private final ControlCatalog catalog;
    private final EvidenceQueryService queries;
    private final EvidenceCompletenessService completeness;
    private final EvidenceContextService context;
    private final EvidenceReuseService reuse;
    private final List<ContentSummarizer> summarizers;
    private final ObjectMapper mapper;
    private final Clock clock;

    /** A cached content part; {@code clockDependent} ones (PDF/Office: review dates vs today) are valid for one day only. */
    private record Cached(Extraction extraction, LocalDate day, boolean clockDependent) {}

    private final Map<String, Cached> cache = Collections.synchronizedMap(
            new LinkedHashMap<>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Cached> eldest) {
                    return size() > CACHE_MAX_ENTRIES;
                }
            });
    private final AtomicLong cacheHits = new AtomicLong();

    public EvidenceSummaryService(EvidenceRecordRepository records, CheckResultRepository checkResults,
                                  ObjectStore objectStore, ControlCatalog catalog, EvidenceQueryService queries,
                                  EvidenceCompletenessService completeness, EvidenceContextService context,
                                  EvidenceReuseService reuse, List<ContentSummarizer> summarizers,
                                  ObjectMapper mapper, Clock clock) {
        this.records = records;
        this.checkResults = checkResults;
        this.objectStore = objectStore;
        this.catalog = catalog;
        this.queries = queries;
        this.completeness = completeness;
        this.context = context;
        this.reuse = reuse;
        this.summarizers = summarizers.stream().sorted(Comparator.comparingInt(ContentSummarizer::order)).toList();
        this.mapper = mapper;
        this.clock = clock;
    }

    public EvidenceSummary summarize(UUID evidenceId) {
        return summarize(evidenceId, null);
    }

    /** @param versionNumber null for the current version; an unknown version is a 404. */
    public EvidenceSummary summarize(UUID evidenceId, Integer versionNumber) {
        EvidenceRecord r = records.findById(evidenceId)
                .orElseThrow(() -> ApiException.notFound("Unknown evidence: " + evidenceId));
        EvidenceVersion v = resolveVersion(r, versionNumber);
        Instant now = clock.instant();

        SummaryControl control = controlOf(r);
        List<String> limits = new ArrayList<>();
        List<CheckResult> rows = checkRowsFor(r, v, limits);
        Verdict verdict = verdictOf(rows);

        Integrity integrity = Integrity.UNVERIFIABLE;
        String actualSha = null;
        if (v != null) {
            try {
                IntegrityReport report = queries.verify(r.getId(), v.getVersionNumber());
                actualSha = report.actualSha256();
                integrity = actualSha == null ? Integrity.UNVERIFIABLE
                        : report.intact() ? Integrity.INTACT : Integrity.MISMATCH;
            } catch (ApiException e) {
                throw e;
            } catch (RuntimeException e) {
                integrity = Integrity.UNVERIFIABLE;
            }
        }
        Extraction content = v == null || actualSha == null
                ? unavailable(v == null
                        ? "No version has been collected for this evidence."
                        : "The stored object could not be read, so its integrity cannot be verified.")
                : contentFor(r, v, actualSha, control, rows, now);

        List<SummaryFinding> findings = new ArrayList<>();
        if (integrity == Integrity.MISMATCH) {
            findings.add(new SummaryFinding(Severity.FAIL, SummaryTemplates.mismatchFinding(), "record"));
        }
        findings.addAll(content.findings());
        if (verdict == Verdict.NOT_ASSESSED) {
            findings.add(new SummaryFinding(Severity.INFO, SummaryTemplates.noAutomatedRule(), "record"));
        }
        findings.sort(Comparator.comparing((SummaryFinding f) -> f.severity()).reversed()); // stable: FAIL, WARNING, INFO
        limits.addAll(content.limits());

        String contentSentence = content.kind() == ContentKind.UNAVAILABLE
                ? SummaryTemplates.unavailableSentence() : content.contentSentence();
        String headline = SummaryTemplates.headline(control.name(), control.controlId(), control.framework(),
                verdict, contentSentence);
        if (integrity == Integrity.MISMATCH) {
            headline += " " + SummaryTemplates.integrityMismatchSentence();
        }
        headline = Redactor.redactText(headline);

        Instant collectedAt = v != null ? v.getCollectedAt() : r.getLatestCollectedAt();
        Integer ageDays = collectedAt == null ? null : completeness.ageDays(collectedAt, now);
        Freshness freshness = ageDays != null && completeness.isCurrent(ageDays) ? Freshness.CURRENT : Freshness.STALE;
        int shownVersion = v != null ? v.getVersionNumber() : r.getCurrentVersion();

        List<SummaryFact> facts = new ArrayList<>(recordFacts(r, v, collectedAt));
        facts.addAll(content.facts());
        if (facts.size() > MAX_FACTS) {
            facts = new ArrayList<>(facts.subList(0, MAX_FACTS));
            limits.add("Facts were capped at " + MAX_FACTS + ".");
        }

        String summary = Redactor.redactText(SummaryTemplates.summary(headline, findings, collectedAt,
                r.getSourceSystem(), r.getApplicationSlug(), shownVersion, integrity, freshness));
        List<String> grounded = facts.stream().map(SummaryTemplates::groundedLine).toList();

        SummaryHealth health = new SummaryHealth(integrity, collectedAt, ageDays, freshness,
                r.getLifecycleState().name(), r.getVersions().size(), missingTags(r));

        return new EvidenceSummary(r.getId().toString(), r.getApplicationSlug(), r.getFramework(),
                r.getControlId(), MODEL, isSimulated(v), false, summary, grounded, now,
                METHOD, shownVersion, v != null ? v.getSha256() : r.getLatestSha256(), content.kind(),
                verdict, headline, control, checksOf(rows), List.copyOf(facts), List.copyOf(findings),
                health, List.copyOf(limits));
    }

    /** Cache hits since start — lets tests prove the content-derived part is reused. */
    long cacheHits() {
        return cacheHits.get();
    }

    // ---- version / control / checks ------------------------------------------------------

    private static EvidenceVersion resolveVersion(EvidenceRecord r, Integer versionNumber) {
        if (versionNumber == null) {
            return r.latestVersion();
        }
        return r.getVersions().stream().filter(x -> x.getVersionNumber() == versionNumber).findFirst()
                .orElseThrow(() -> ApiException.notFound(
                        "Evidence %s has no version %d".formatted(r.getId(), versionNumber)));
    }

    private SummaryControl controlOf(EvidenceRecord r) {
        Optional<ControlCatalog.Expectation> entry = catalog.find(r.getFramework(), r.getControlId());
        String name = entry.map(ControlCatalog.Expectation::title).filter(t -> !SummaryTemplates.blank(t))
                .orElse(r.getControlId());
        String description = entry.map(e -> SummaryTemplates.blank(e.description()) ? e.title() : e.description())
                .orElse(null);
        ControlSource source = entry.isEmpty() ? ControlSource.UNKNOWN
                : entry.get().fromLibrary() ? ControlSource.LIBRARY : ControlSource.LEGACY;
        return new SummaryControl(r.getControlId(), name, description, r.getFramework(), source, alsoSatisfies(r));
    }

    /** Other frameworks this evidence is mapped to — the existing provenance and framework-tag logic, not a new one. */
    private List<SummaryAlsoSatisfies> alsoSatisfies(EvidenceRecord r) {
        Map<String, String> frameworks = new TreeMap<>();
        for (String fw : reuse.mappedFrameworks(r)) {
            frameworks.putIfAbsent(fw.toUpperCase(java.util.Locale.ROOT), fw);
        }
        for (String fw : context.forEvidence(r.getId()).frameworks()) {
            frameworks.putIfAbsent(fw.toUpperCase(java.util.Locale.ROOT), fw);
        }
        String primary = r.getFramework() == null ? "" : r.getFramework().toUpperCase(java.util.Locale.ROOT);
        List<SummaryAlsoSatisfies> out = new ArrayList<>();
        frameworks.forEach((key, fw) -> {
            if (!key.equals(primary)) {
                String name = catalog.find(fw, r.getControlId()).map(ControlCatalog.Expectation::title)
                        .orElse(r.getControlId());
                out.add(new SummaryAlsoSatisfies(fw, r.getControlId(), name));
            }
        });
        return out;
    }

    /** Persisted check rows that describe the shown version, plus limit lines when they do not line up. */
    private List<CheckResult> checkRowsFor(EvidenceRecord r, EvidenceVersion v, List<String> limits) {
        List<CheckResult> all = checkResults.findByEvidenceRecordId(r.getId());
        if (all.isEmpty() || v == null) {
            return List.of();
        }
        List<CheckResult> rows;
        if (v.getVersionNumber() == r.getCurrentVersion()) {
            rows = all;
            Instant newest = all.stream().map(CheckResult::getEvaluatedAt).max(Comparator.naturalOrder()).orElse(null);
            boolean older = all.stream().anyMatch(c -> c.getEvidenceVersion() < v.getVersionNumber())
                    || (newest != null && v.getCollectedAt() != null && newest.isBefore(v.getCollectedAt()));
            if (older) {
                limits.add("Check results were evaluated on an older version.");
            }
        } else {
            rows = all.stream().filter(c -> c.getEvidenceVersion() == v.getVersionNumber()).toList();
            if (rows.isEmpty()) {
                limits.add("Check results were evaluated on a different version; none exist for version "
                        + v.getVersionNumber() + ".");
            }
        }
        return rows.stream().sorted(Comparator.comparing(CheckResult::getCheckId)).toList();
    }

    private static Verdict verdictOf(List<CheckResult> rows) {
        if (rows.isEmpty()) {
            return Verdict.NOT_ASSESSED;
        }
        boolean warn = false, pass = false;
        for (CheckResult c : rows) {
            switch (c.getStatus()) {
                case FAIL -> {
                    return Verdict.FAIL;
                }
                case WARNING -> warn = true;
                case PASS -> pass = true;
                case NOT_APPLICABLE -> { }
            }
        }
        return warn ? Verdict.WARNING : pass ? Verdict.PASS : Verdict.NOT_APPLICABLE;
    }

    private static List<SummaryCheck> checksOf(List<CheckResult> rows) {
        return rows.stream()
                .map(c -> new SummaryCheck(c.getCheckId(), c.getStatus(), Redactor.redactText(c.getObserved()),
                        Redactor.redactText(c.getExpected()), Redactor.redactText(c.getDetail())))
                .toList();
    }

    // ---- content ---------------------------------------------------------------------------

    private static Extraction unavailable(String reason) {
        return new Extraction(ContentKind.UNAVAILABLE, List.of(),
                List.of(new SummaryFinding(Severity.WARNING, reason, "record")),
                List.of("Content could not be read, so no content facts are shown."), "");
    }

    private Extraction contentFor(EvidenceRecord r, EvidenceVersion v, String actualSha, SummaryControl control,
                                  List<CheckResult> rows, Instant now) {
        String key = String.join("|", r.getId().toString(), String.valueOf(v.getVersionNumber()), actualSha,
                checkFingerprint(rows), catalogFingerprint(control));
        LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);
        Cached cached = cache.get(key);
        if (cached != null && (!cached.clockDependent() || cached.day().equals(today))) {
            cacheHits.incrementAndGet();
            return cached.extraction();
        }
        byte[] all;
        try {
            all = objectStore.get(v.getObjectKey()).orElse(null);
        } catch (RuntimeException e) {
            all = null;
        }
        if (all == null) {
            return unavailable("The stored object could not be read.");
        }
        Extraction built = extract(r, v, all, control, now);
        cache.put(key, new Cached(built, today, ContentProbe.isDocumentContainer(all)));
        return built;
    }

    private Extraction extract(EvidenceRecord r, EvidenceVersion v, byte[] all, SummaryControl control, Instant now) {
        int limit = ContentProbe.isDocumentContainer(all) ? DOCUMENT_READ_LIMIT : READ_LIMIT;
        boolean truncated = all.length > limit;
        byte[] read = truncated ? Arrays.copyOf(all, limit) : all;
        ContentProbe probe = ContentProbe.of(read, truncated, all.length, v.getContentType(),
                v.getMetadata().get("upload.filename"), mapper);

        FactSink sink = new FactSink();
        if (truncated) {
            sink.limit("Only the first " + SummaryTemplates.humanSize(limit) + " of "
                    + SummaryTemplates.humanSize(all.length) + " was read.");
        }
        if (probe.declaredDisagrees()) {
            sink.finding(Severity.INFO, "The declared type "
                    + FactSink.text(probe.declaredContentType() != null ? probe.declaredContentType()
                            : probe.fileName())
                    + " does not match the detected content (" + probe.detectedLabel() + ").", "record");
        }
        SummaryContext ctx = new SummaryContext(probe, r.getSourceSystem(), r.getApplicationSlug(), sink, now,
                control.name(), control.description());
        ContentSummarizer chosen = summarizers.stream().filter(s -> s.supports(probe)).findFirst()
                .orElseGet(BinaryMetadataSummarizer::new);
        try {
            return chosen.summarize(ctx);
        } catch (RuntimeException e) {
            FactSink fallback = new FactSink();
            fallback.finding(Severity.WARNING, "The content could not be summarised ("
                    + e.getClass().getSimpleName() + "); only its type and size are shown.", "content");
            return new BinaryMetadataSummarizer().summarize(new SummaryContext(probe, r.getSourceSystem(),
                    r.getApplicationSlug(), fallback, now, control.name(), control.description()));
        }
    }

    private static String checkFingerprint(List<CheckResult> rows) {
        StringBuilder sb = new StringBuilder();
        rows.forEach(c -> sb.append(c.getCheckId()).append(':').append(c.getStatus()).append(':')
                .append(c.getEvidenceVersion()).append(':').append(c.getEvaluatedAt()).append(';'));
        return Hashing.sha256Hex(sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static String catalogFingerprint(SummaryControl c) {
        return Hashing.sha256Hex((c.name() + "\u0001" + c.description() + "\u0001" + c.source())
                .getBytes(StandardCharsets.UTF_8));
    }

    // ---- record-level facts and health -----------------------------------------------------------

    private static List<SummaryFact> recordFacts(EvidenceRecord r, EvidenceVersion v, Instant collectedAt) {
        List<SummaryFact> out = new ArrayList<>();
        addRecord(out, "application", r.getApplicationSlug());
        addRecord(out, "source system", r.getSourceSystem());
        addRecord(out, "collected at", collectedAt == null ? null : collectedAt.toString());
        addRecord(out, "collected by", v == null ? null : v.getCollectedBy());
        addRecord(out, "version", v == null ? null : "v" + v.getVersionNumber() + " of " + r.getVersions().size());
        addRecord(out, "declared content type", v == null ? null : v.getContentType());
        return out;
    }

    private static void addRecord(List<SummaryFact> out, String label, String value) {
        if (!SummaryTemplates.blank(value)) {
            out.add(new SummaryFact(label, FactSink.value(label, value), "record"));
        }
    }

    private static List<String> missingTags(EvidenceRecord r) {
        Map<String, String> present = new LinkedHashMap<>();
        for (EvidenceTag t : r.getTags()) {
            present.put(t.getTagKey(), t.getTagValue());
        }
        return EvidenceNaming.EXPECTED_TAG_KEYS.stream()
                .filter(k -> SummaryTemplates.blank(present.get(k)))
                .toList();
    }

    private static boolean isSimulated(EvidenceVersion v) {
        return v != null && "true".equalsIgnoreCase(v.getMetadata().get("simulated"));
    }
}
