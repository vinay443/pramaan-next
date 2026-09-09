package com.pramaan.backend.rules;

import com.pramaan.backend.common.PageResponse;
import com.pramaan.backend.evidence.EvidenceQueryService;
import com.pramaan.backend.evidence.EvidenceQueryService.EvidenceFilter;
import com.pramaan.backend.evidence.domain.EvidenceRecord;
import com.pramaan.backend.evidence.domain.EvidenceVersion;
import com.pramaan.backend.rules.CheckDtos.CheckResultView;
import com.pramaan.backend.rules.CheckDtos.EvaluateRequest;
import com.pramaan.backend.rules.CheckDtos.EvaluationSummary;
import com.pramaan.backend.rules.CheckDtos.RuleDef;
import com.pramaan.backend.rules.domain.CheckResult;
import com.pramaan.backend.rules.repo.CheckResultRepository;
import com.pramaan.backend.storage.ObjectStore;
import jakarta.persistence.criteria.Predicate;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deterministic rule evaluation. For each matching evidence record the latest
 * version bytes are pulled from the object store and matched against the
 * declarative rule set; the verdict (PASS / WARNING / FAIL / NOT_APPLICABLE) is
 * persisted as a {@link CheckResult}, one row per (evidence, checkId).
 */
@Service
public class RuleEvaluationService {

    private final EvidenceQueryService evidence;
    private final CheckResultRepository results;
    private final RuleCatalog catalog;
    private final ObjectStore objectStore;
    private final Clock clock;

    public RuleEvaluationService(EvidenceQueryService evidence, CheckResultRepository results,
                                 RuleCatalog catalog, ObjectStore objectStore, Clock clock) {
        this.evidence = evidence;
        this.results = results;
        this.catalog = catalog;
        this.objectStore = objectStore;
        this.clock = clock;
    }

    @Transactional
    public EvaluationSummary evaluate(EvaluateRequest req) {
        EvaluateRequest r = req == null ? new EvaluateRequest(null, null, null, null) : req;
        Instant now = clock.instant();
        List<EvidenceRecord> records = evidence.recordsMatching(new EvidenceFilter(
                r.applicationSlug(), r.framework(), r.controlId(), r.sourceSystem(),
                null, null, null, 0, 100_000));

        Map<CheckStatus, Integer> byStatus = new EnumMap<>(CheckStatus.class);
        int evaluated = 0;
        int written = 0;

        for (EvidenceRecord record : records) {
            List<RuleDef> applicable = catalog.rulesFor(record.getFramework(), record.getControlId());
            if (applicable.isEmpty()) {
                continue;
            }
            evaluated++;
            String content = latestContent(record);
            for (RuleDef rule : applicable) {
                Verdict v = apply(rule, content);
                upsert(record, rule, v, now);
                written++;
                byStatus.merge(v.status(), 1, Integer::sum);
            }
        }
        Map<String, Integer> counts = new java.util.TreeMap<>();
        byStatus.forEach((k, val) -> counts.put(k.name(), val));
        return new EvaluationSummary(now, evaluated, written, counts);
    }

    @Transactional(readOnly = true)
    public PageResponse<CheckResultView> list(EvaluateRequest filter, CheckStatus status,
                                              int page, int size) {
        EvaluateRequest f = filter == null ? new EvaluateRequest(null, null, null, null) : filter;
        Specification<CheckResult> spec = (root, q, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            eq(ps, cb, root, "applicationSlug", lower(f.applicationSlug()));
            eq(ps, cb, root, "framework", upper(f.framework()));
            eq(ps, cb, root, "controlId", upper(f.controlId()));
            eq(ps, cb, root, "sourceSystem", upper(f.sourceSystem()));
            if (status != null) {
                ps.add(cb.equal(root.get("status"), status));
            }
            return cb.and(ps.toArray(Predicate[]::new));
        };
        var pageable = PageRequest.of(Math.max(page, 0), size <= 0 ? 20 : Math.min(size, 500),
                Sort.by(Sort.Order.asc("applicationSlug"), Sort.Order.asc("controlId"),
                        Sort.Order.asc("checkId")));
        return PageResponse.of(results.findAll(spec, pageable).map(CheckResultView::from));
    }

    // ---- internals ---------------------------------------------------------

    private record Verdict(CheckStatus status, String observed, String expected, String detail) {}

    private Verdict apply(RuleDef rule, String content) {
        if (content == null) {
            return new Verdict(CheckStatus.NOT_APPLICABLE, "no readable evidence content",
                    rule.expected(), "latest evidence object missing or not UTF-8 text");
        }
        // Normalise JSON spacing so rules match regardless of the producer's formatting
        // (e.g. `"status": "PASS"` vs `"status":"PASS"`).
        String haystack = content.toLowerCase().replace("\": \"", "\":\"").replace("\" :\"", "\":\"");
        for (RuleDef.Clause c : rule.clauses() == null ? List.<RuleDef.Clause>of() : rule.clauses()) {
            if (c.contains() == null) {
                continue;
            }
            String needle = c.contains().toLowerCase().replace("\": \"", "\":\"");
            if (haystack.contains(needle)) {
                return new Verdict(c.status(), "matched: " + c.contains(), rule.expected(),
                        c.detail() != null ? c.detail() : rule.title());
            }
        }
        CheckStatus def = rule.defaultStatus() == null ? CheckStatus.WARNING : rule.defaultStatus();
        return new Verdict(def, "no rule clause matched", rule.expected(),
                "fell through to default verdict " + def);
    }

    private void upsert(EvidenceRecord record, RuleDef rule, Verdict v, Instant now) {
        int version = record.getCurrentVersion();
        CheckResult existing = results
                .findByEvidenceRecordIdAndCheckId(record.getId(), rule.checkId())
                .orElse(null);
        if (existing == null) {
            results.save(new CheckResult(UUID.randomUUID(), record.getId(), rule.checkId(),
                    record.getApplicationSlug(), record.getControlId(), record.getFramework(),
                    record.getSourceSystem(), v.status(), v.observed(), v.expected(), v.detail(),
                    version, now));
        } else {
            existing.update(v.status(), v.observed(), v.expected(), v.detail(), version, now);
            results.save(existing);
        }
    }

    private String latestContent(EvidenceRecord record) {
        EvidenceVersion latest = record.latestVersion();
        if (latest == null) {
            return null;
        }
        return objectStore.get(latest.getObjectKey())
                .map(bytes -> new String(bytes, StandardCharsets.UTF_8))
                .orElse(null);
    }

    private static void eq(List<Predicate> ps, jakarta.persistence.criteria.CriteriaBuilder cb,
                           jakarta.persistence.criteria.Root<CheckResult> root, String field, String value) {
        if (value != null && !value.isBlank()) {
            ps.add(cb.equal(root.get(field), value));
        }
    }

    private static String lower(String s) { return s == null ? null : s.toLowerCase(); }

    private static String upper(String s) { return s == null ? null : s.toUpperCase(); }
}
