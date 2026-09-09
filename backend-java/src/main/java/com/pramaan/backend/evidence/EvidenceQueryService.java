package com.pramaan.backend.evidence;

import com.pramaan.backend.common.ApiException;
import com.pramaan.backend.common.PageResponse;
import com.pramaan.backend.config.PramaanProperties;
import com.pramaan.backend.evidence.EvidenceDtos.DeterministicQueryResult;
import com.pramaan.backend.evidence.EvidenceDtos.EvidenceDashboard;
import com.pramaan.backend.evidence.EvidenceDtos.EvidenceVersionView;
import com.pramaan.backend.evidence.EvidenceDtos.EvidenceView;
import com.pramaan.backend.evidence.EvidenceDtos.IntegrityReport;
import com.pramaan.backend.evidence.domain.EvidenceRecord;
import com.pramaan.backend.evidence.domain.EvidenceVersion;
import com.pramaan.backend.evidence.repo.EvidenceRecordRepository;
import com.pramaan.backend.evidence.repo.EvidenceVersionRepository;
import com.pramaan.backend.storage.ObjectStore;
import com.pramaan.backend.util.Hashing;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only, deterministic evidence queries over the repository. No LLM. */
@Service
@Transactional(readOnly = true)
public class EvidenceQueryService {

    private final EvidenceRecordRepository records;
    private final EvidenceVersionRepository versions;
    private final ObjectStore objectStore;
    private final Clock clock;
    private final int staleAfterDays;

    public EvidenceQueryService(EvidenceRecordRepository records, EvidenceVersionRepository versions,
                                ObjectStore objectStore, Clock clock, PramaanProperties props) {
        this.records = records;
        this.versions = versions;
        this.objectStore = objectStore;
        this.clock = clock;
        this.staleAfterDays = props.scheduler() != null ? props.scheduler().staleAfterDays() : 90;
    }

    public record EvidenceFilter(String applicationSlug, String framework, String controlId,
                                 String sourceSystem, Instant collectedAfter, Instant collectedBefore,
                                 String tag, int page, int size) {}

    /** Use Case 3 — repository facets backed by the canonical evidence tags. */
    public record TagFacets(String technology, String collectionMethod) {
        public static final TagFacets NONE = new TagFacets(null, null);
    }

    private Specification<EvidenceRecord> specOf(EvidenceFilter f) {
        List<Specification<EvidenceRecord>> parts = new java.util.ArrayList<>(List.of(
                EvidenceSpecs.applicationSlug(f.applicationSlug()),
                EvidenceSpecs.framework(f.framework()),
                EvidenceSpecs.controlId(f.controlId()),
                EvidenceSpecs.sourceSystem(f.sourceSystem()),
                EvidenceSpecs.collectedAfter(f.collectedAfter()),
                EvidenceSpecs.collectedBefore(f.collectedBefore()),
                EvidenceSpecs.tag(f.tag())));
        Specification<EvidenceRecord> combined = (root, q, cb) -> cb.conjunction();
        for (Specification<EvidenceRecord> p : parts) {
            if (p != null) {
                combined = combined.and(p);
            }
        }
        return combined;
    }

    public PageResponse<EvidenceView> search(EvidenceFilter f) {
        return search(f, TagFacets.NONE);
    }

    public PageResponse<EvidenceView> search(EvidenceFilter f, TagFacets facets) {
        var pageable = PageRequest.of(Math.max(f.page(), 0), clampSize(f.size()),
                Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.asc("id")));
        Specification<EvidenceRecord> spec = specOf(f);
        if (facets != null && facets.technology() != null && !facets.technology().isBlank()) {
            spec = spec.and(EvidenceSpecs.tagPair("technology", facets.technology()));
        }
        if (facets != null && facets.collectionMethod() != null && !facets.collectionMethod().isBlank()) {
            spec = spec.and(EvidenceSpecs.tagPair("collectionMethod", facets.collectionMethod()));
        }
        return PageResponse.of(records.findAll(spec, pageable).map(EvidenceView::from));
    }

    public EvidenceView get(UUID id) {
        return EvidenceView.from(require(id));
    }

    public List<EvidenceVersionView> versions(UUID id) {
        require(id);
        return versions.findByRecordIdOrderByVersionNumberAsc(id).stream()
                .map(EvidenceVersionView::from).toList();
    }

    public EvidenceVersionView version(UUID id, int versionNumber) {
        return versions.findByRecordIdOrderByVersionNumberAsc(id).stream()
                .filter(v -> v.getVersionNumber() == versionNumber)
                .findFirst()
                .map(EvidenceVersionView::from)
                .orElseThrow(() -> ApiException.notFound(
                        "Evidence %s has no version %d".formatted(id, versionNumber)));
    }

    public IntegrityReport verify(UUID id, Integer versionNumber) {
        EvidenceRecord r = require(id);
        EvidenceVersion v = versionNumber == null ? r.latestVersion()
                : r.getVersions().stream().filter(x -> x.getVersionNumber() == versionNumber)
                        .findFirst().orElseThrow(() -> ApiException.notFound("no such version"));
        if (v == null) {
            throw ApiException.notFound("Evidence has no versions");
        }
        byte[] bytes = objectStore.get(v.getObjectKey())
                .orElse(null);
        if (bytes == null) {
            return new IntegrityReport(id.toString(), v.getVersionNumber(), v.getSha256(), null, false,
                    "object missing from store: " + v.getObjectKey());
        }
        String actual = Hashing.sha256Hex(bytes);
        boolean intact = actual.equalsIgnoreCase(v.getSha256());
        return new IntegrityReport(id.toString(), v.getVersionNumber(), v.getSha256(), actual, intact,
                intact ? "sha-256 matches" : "sha-256 mismatch");
    }

    // ---- Use Case 4 — consolidated dashboard + integrity ---------------------

    /**
     * One authoritative snapshot of the evidence repository: totals, freshness
     * buckets, duplicate-hash count, and a repository-wide hash-integrity check
     * (SHA-256 recomputed from the object store for the current version of every
     * record and compared to the persisted value). Reads only persisted evidence.
     */
    public EvidenceDashboard dashboard() {
        Instant now = clock.instant();
        Instant d30 = now.minus(30, ChronoUnit.DAYS);
        Instant d90 = now.minus(90, ChronoUnit.DAYS);

        List<EvidenceRecord> all = records.findAll(Sort.by(Sort.Order.asc("id")));
        java.util.Set<String> apps = new java.util.HashSet<>();
        java.util.Set<String> frameworks = new java.util.HashSet<>();
        java.util.Set<String> sources = new java.util.HashSet<>();
        Map<String, Object> bySource = new TreeMap<>();
        int fresh = 0, aging = 0, stale = 0, unknown = 0;
        int checked = 0, intact = 0, mismatch = 0, missingObject = 0;

        for (EvidenceRecord r : all) {
            apps.add(r.getApplicationSlug());
            frameworks.add(r.getFramework());
            sources.add(r.getSourceSystem());
            bySource.merge(r.getSourceSystem(), 1, (a, b) -> ((int) a) + 1);

            Instant c = r.getLatestCollectedAt();
            if (c == null) {
                unknown++;
            } else if (c.isAfter(d30)) {
                fresh++;
            } else if (c.isAfter(d90)) {
                aging++;
            } else {
                stale++;
            }

            EvidenceVersion v = r.latestVersion();
            if (v == null) {
                continue;
            }
            checked++;
            byte[] bytes = objectStore.get(v.getObjectKey()).orElse(null);
            if (bytes == null) {
                missingObject++;
            } else if (Hashing.sha256Hex(bytes).equalsIgnoreCase(v.getSha256())) {
                intact++;
            } else {
                mismatch++;
            }
        }

        List<Map<String, Object>> bySourceRows = bySource.entrySet().stream()
                .map(e -> row("sourceSystem", e.getKey(), "count", e.getValue())).toList();

        return new EvidenceDashboard(now, all.size(), versions.count(), apps.size(),
                frameworks.size(), sources.size(), staleAfterDays,
                new EvidenceDashboard.Freshness(fresh, aging, stale, unknown),
                new EvidenceDashboard.Integrity(checked, intact, mismatch, missingObject),
                versions.findDuplicateHashes().size(), bySourceRows);
    }

    // ---- deterministic named queries -----------------------------------------

    public DeterministicQueryResult run(String name, EvidenceFilter f) {
        Instant now = clock.instant();
        return switch (name == null ? "" : name.toLowerCase()) {
            case "source-breakdown" -> sourceBreakdown(f, now);
            case "stale-evidence" -> staleEvidence(f, now);
            case "latest-per-control" -> latestPerControl(f, now);
            case "duplicates" -> duplicates(now);
            case "freshness" -> freshness(f, now);
            default -> throw ApiException.badRequest(
                    "unknown query '" + name + "'. Available: source-breakdown, stale-evidence, "
                            + "latest-per-control, duplicates, freshness");
        };
    }

    private DeterministicQueryResult sourceBreakdown(EvidenceFilter f, Instant now) {
        var page = pageAll(f);
        Map<String, Object> counts = new TreeMap<>();
        page.forEach(r -> counts.merge(r.getSourceSystem(), 1, (a, b) -> ((int) a) + 1));
        List<Map<String, Object>> rows = counts.entrySet().stream()
                .map(e -> row("sourceSystem", e.getKey(), "count", e.getValue())).toList();
        return new DeterministicQueryResult("source-breakdown",
                "Evidence is sourced from %d system(s): %s.".formatted(counts.size(), counts),
                now, counts, rows);
    }

    private DeterministicQueryResult staleEvidence(EvidenceFilter f, Instant now) {
        Instant cutoff = now.minus(staleAfterDays, ChronoUnit.DAYS);
        List<Map<String, Object>> rows = pageAll(f).stream()
                .filter(r -> r.getLatestCollectedAt() != null && r.getLatestCollectedAt().isBefore(cutoff))
                .sorted((a, b) -> a.getLatestCollectedAt().compareTo(b.getLatestCollectedAt()))
                .map(r -> row("evidenceId", r.getId().toString(),
                        "applicationSlug", r.getApplicationSlug(),
                        "framework", r.getFramework(),
                        "controlId", r.getControlId(),
                        "collectedAt", r.getLatestCollectedAt().toString()))
                .toList();
        Map<String, Object> counts = Map.of("staleAfterDays", staleAfterDays, "stale", rows.size());
        return new DeterministicQueryResult("stale-evidence",
                "%d evidence item(s) are older than %d days.".formatted(rows.size(), staleAfterDays),
                now, counts, rows);
    }

    private DeterministicQueryResult latestPerControl(EvidenceFilter f, Instant now) {
        Map<String, EvidenceRecord> latest = new TreeMap<>();
        for (EvidenceRecord r : pageAll(f)) {
            String key = r.getApplicationSlug() + " / " + r.getFramework() + " / " + r.getControlId();
            EvidenceRecord cur = latest.get(key);
            if (cur == null || after(r.getLatestCollectedAt(), cur.getLatestCollectedAt())) {
                latest.put(key, r);
            }
        }
        List<Map<String, Object>> rows = latest.entrySet().stream()
                .map(e -> row("control", e.getKey(),
                        "evidenceId", e.getValue().getId().toString(),
                        "version", e.getValue().getCurrentVersion(),
                        "sha256", e.getValue().getLatestSha256(),
                        "collectedAt", String.valueOf(e.getValue().getLatestCollectedAt())))
                .toList();
        return new DeterministicQueryResult("latest-per-control",
                "%d control(s) have current evidence.".formatted(rows.size()),
                now, Map.of("controls", rows.size()), rows);
    }

    private DeterministicQueryResult duplicates(Instant now) {
        List<Map<String, Object>> rows = versions.findDuplicateHashes().stream()
                .map(o -> row("sha256", o[0], "occurrences", ((Number) o[1]).intValue()))
                .toList();
        return new DeterministicQueryResult("duplicates",
                "%d content hash(es) appear on more than one evidence version.".formatted(rows.size()),
                now, Map.of("duplicateHashes", rows.size()), rows);
    }

    private DeterministicQueryResult freshness(EvidenceFilter f, Instant now) {
        int fresh = 0, aging = 0, stale = 0, unknown = 0;
        Instant d30 = now.minus(30, ChronoUnit.DAYS);
        Instant d90 = now.minus(90, ChronoUnit.DAYS);
        for (EvidenceRecord r : pageAll(f)) {
            Instant c = r.getLatestCollectedAt();
            if (c == null) {
                unknown++;
            } else if (c.isAfter(d30)) {
                fresh++;
            } else if (c.isAfter(d90)) {
                aging++;
            } else {
                stale++;
            }
        }
        Map<String, Object> counts = new TreeMap<>(Map.of(
                "fresh", fresh, "aging", aging, "stale", stale, "unknown", unknown));
        return new DeterministicQueryResult("freshness",
                "Freshness: %d fresh (<=30d), %d aging (<=90d), %d stale (>90d).".formatted(fresh, aging, stale),
                now, counts, List.of(row("fresh", fresh, "aging", aging, "stale", stale, "unknown", unknown)));
    }

    private List<EvidenceRecord> pageAll(EvidenceFilter f) {
        return records.findAll(specOf(f), Sort.by(Sort.Order.asc("id")));
    }

    /** All evidence records matching the filter (stable order). Used by rule evaluation. */
    public List<EvidenceRecord> recordsMatching(EvidenceFilter f) {
        return pageAll(f);
    }

    /** Total persisted evidence records. Cheap; used to detect a stale embedding index. */
    public long count() {
        return records.count();
    }

    /** Latest-version content of one evidence record as UTF-8 text, if the object is present. */
    public java.util.Optional<String> latestContentText(UUID evidenceId) {
        EvidenceRecord r = require(evidenceId);
        EvidenceVersion v = r.latestVersion();
        if (v == null) {
            return java.util.Optional.empty();
        }
        return objectStore.get(v.getObjectKey())
                .map(b -> new String(b, java.nio.charset.StandardCharsets.UTF_8));
    }

    private EvidenceRecord require(UUID id) {
        return records.findById(id).orElseThrow(() -> ApiException.notFound("Unknown evidence: " + id));
    }

    private static boolean after(Instant a, Instant b) {
        if (a == null) return false;
        if (b == null) return true;
        return a.isAfter(b);
    }

    private static Map<String, Object> row(Object... kv) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return m;
    }

    private static int clampSize(int size) {
        if (size <= 0) return 20;
        return Math.min(size, 500);
    }
}
