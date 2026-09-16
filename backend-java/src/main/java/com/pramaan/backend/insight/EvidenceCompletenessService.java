package com.pramaan.backend.insight;

import com.pramaan.backend.config.PramaanProperties;
import com.pramaan.backend.evidence.ControlFrameworkCatalog;
import com.pramaan.backend.evidence.EvidenceDtos.IntegrityStatus;
import com.pramaan.backend.evidence.EvidenceQueryService;
import com.pramaan.backend.evidence.EvidenceQueryService.EvidenceFilter;
import com.pramaan.backend.evidence.domain.EvidenceRecord;
import com.pramaan.backend.evidence.domain.EvidenceTag;
import com.pramaan.backend.evidence.domain.EvidenceVersion;
import com.pramaan.backend.insight.InsightDtos.ControlCompletenessRow;
import com.pramaan.backend.insight.InsightDtos.EvidenceCompletenessFactor;
import com.pramaan.backend.insight.InsightDtos.EvidenceCompletenessItem;
import com.pramaan.backend.insight.InsightDtos.EvidenceCompletenessReport;
import com.pramaan.backend.insight.InsightDtos.FrameworkCompletenessRow;
import com.pramaan.backend.storage.ObjectStore;
import com.pramaan.backend.util.Hashing;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deterministic, per-evidence-item completeness: "is this specific piece of
 * evidence audit-ready?" (as opposed to {@link CompletenessService}, which asks
 * "does this control have any current evidence at all?"). Lists the exact same
 * records as the Evidence Repository ({@link EvidenceQueryService#recordsMatching}),
 * so item counts here can never drift from the repository's — and scores each one
 * against concrete, checkable factors. No LLM.
 *
 * <p>Weights (sum to 100), heaviest severity first:
 * <ul>
 *   <li>Integrity (25) — recorded SHA-256 must still match the stored object.
 *       A mismatch also forces the content factor to fail: unverified content
 *       cannot be trusted to be "complete" either.</li>
 *   <li>Evidence content (20) — a version exists and its content is non-empty
 *       and not a placeholder.</li>
 *   <li>Freshness (20) — reuses the app-wide stale-after-days threshold.</li>
 *   <li>Control mapping (15) — the control resolves in {@link ControlFrameworkCatalog},
 *       i.e. isn't orphaned.</li>
 *   <li>Collected-by (10) and Technology (10) — optional-but-expected metadata.</li>
 * </ul>
 * A record with no version at all scores at most 25 (control mapping + technology),
 * landing well into the &lt;60% "Incomplete" band.
 */
@Service
@Transactional(readOnly = true)
public class EvidenceCompletenessService {

    static final int WEIGHT_INTEGRITY = 25;
    static final int WEIGHT_CONTENT = 20;
    static final int WEIGHT_FRESHNESS = 20;
    static final int WEIGHT_CONTROL_MAPPING = 15;
    static final int WEIGHT_COLLECTED_BY = 10;
    static final int WEIGHT_TECHNOLOGY = 10;

    private static final int MIN_SUBSTANTIVE_BYTES = 10;
    private static final Set<String> PLACEHOLDER_CONTENT = Set.of(
            "", "n/a", "na", "none", "null", "-", "todo", "tbd", "placeholder", "test", "pending");

    private final EvidenceQueryService evidence;
    private final ObjectStore objectStore;
    private final ControlFrameworkCatalog controlFrameworks;
    private final ControlCatalog controlCatalog;
    private final Clock clock;
    private final int staleAfterDays;

    public EvidenceCompletenessService(EvidenceQueryService evidence, ObjectStore objectStore,
                                       ControlFrameworkCatalog controlFrameworks, ControlCatalog controlCatalog,
                                       Clock clock, PramaanProperties props) {
        this.evidence = evidence;
        this.objectStore = objectStore;
        this.controlFrameworks = controlFrameworks;
        this.controlCatalog = controlCatalog;
        this.clock = clock;
        this.staleAfterDays = props.scheduler() != null ? props.scheduler().staleAfterDays() : 90;
    }

    public EvidenceCompletenessReport forScope(String applicationSlug, String framework) {
        Instant now = clock.instant();
        List<EvidenceRecord> records = evidence.recordsMatching(new EvidenceFilter(
                blankToNull(applicationSlug), blankToNull(framework), null, null, null, null, null, 0, 100_000));

        List<EvidenceCompletenessItem> items = records.stream()
                .map(r -> score(r, now))
                .sorted(Comparator.comparing(EvidenceCompletenessItem::applicationSlug)
                        .thenComparing(EvidenceCompletenessItem::controlId)
                        .thenComparing(EvidenceCompletenessItem::evidenceId))
                .toList();

        int complete = (int) items.stream().filter(i -> "COMPLETE".equals(i.band())).count();
        int partial = (int) items.stream().filter(i -> "PARTIAL".equals(i.band())).count();
        int incomplete = items.size() - complete - partial;
        double avg = items.isEmpty() ? 0.0
                : round(items.stream().mapToInt(EvidenceCompletenessItem::completenessPct).average().orElse(0));

        return new EvidenceCompletenessReport(applicationSlug, framework, now, staleAfterDays,
                items.size(), avg, complete, partial, incomplete, items);
    }

    /** Framework -> control rollup: for every framework in the {@link ControlCatalog}, how many
     *  of its controls have >=1 mapped evidence item, and the mean completeness across those. */
    public List<FrameworkCompletenessRow> frameworkRollup(String applicationSlug) {
        Map<String, List<EvidenceCompletenessItem>> byControlKey = forScope(applicationSlug, null).items().stream()
                .collect(Collectors.groupingBy(i -> controlKey(i.framework(), i.controlId())));

        Map<String, List<ControlCatalog.Expectation>> byFramework = controlCatalog.forApplication(applicationSlug, null)
                .stream()
                .collect(Collectors.groupingBy(ControlCatalog.Expectation::framework, LinkedHashMap::new, Collectors.toList()));

        return byFramework.entrySet().stream()
                .map(e -> frameworkRow(e.getKey(), e.getValue(), byControlKey))
                .sorted(Comparator.comparing(FrameworkCompletenessRow::framework))
                .toList();
    }

    private FrameworkCompletenessRow frameworkRow(String framework, List<ControlCatalog.Expectation> controls,
                                                  Map<String, List<EvidenceCompletenessItem>> byControlKey) {
        int evaluated = 0;
        List<Double> perControlAvg = new ArrayList<>();
        for (ControlCatalog.Expectation c : controls) {
            List<EvidenceCompletenessItem> mapped = byControlKey.getOrDefault(
                    controlKey(framework, c.controlId()), List.of());
            if (!mapped.isEmpty()) {
                evaluated++;
                perControlAvg.add(mapped.stream().mapToInt(EvidenceCompletenessItem::completenessPct).average().orElse(0));
            }
        }
        Double avg = perControlAvg.isEmpty() ? null
                : round(perControlAvg.stream().mapToDouble(Double::doubleValue).average().orElse(0));
        return new FrameworkCompletenessRow(framework, controls.size(), evaluated, controls.size() - evaluated, avg);
    }

    /** Control rollup for one framework: every {@link ControlCatalog} control in it, evaluated
     *  or not, with its mean completeness (null, not 0%, when unevaluated). */
    public List<ControlCompletenessRow> controlRollup(String applicationSlug, String framework) {
        Map<String, List<EvidenceCompletenessItem>> byControl = forScope(applicationSlug, framework).items().stream()
                .collect(Collectors.groupingBy(i -> i.controlId().toUpperCase(Locale.ROOT)));

        return controlCatalog.forApplication(applicationSlug, framework).stream()
                .map(c -> {
                    List<EvidenceCompletenessItem> mapped = byControl.getOrDefault(
                            c.controlId().toUpperCase(Locale.ROOT), List.of());
                    boolean evaluated = !mapped.isEmpty();
                    Double avg = evaluated
                            ? round(mapped.stream().mapToInt(EvidenceCompletenessItem::completenessPct).average().orElse(0))
                            : null;
                    return new ControlCompletenessRow(c.controlId(), c.title(), evaluated, avg, mapped.size());
                })
                .sorted(Comparator.comparing(ControlCompletenessRow::controlId))
                .toList();
    }

    /** The evidence items backing one control's score, for the drill-down panel. */
    public List<EvidenceCompletenessItem> evidenceForControl(String applicationSlug, String framework, String controlId) {
        return forScope(applicationSlug, framework).items().stream()
                .filter(i -> i.controlId().equalsIgnoreCase(controlId))
                .toList();
    }

    private static String controlKey(String framework, String controlId) {
        return (framework == null ? "" : framework.toUpperCase(Locale.ROOT)) + "|"
                + (controlId == null ? "" : controlId.toUpperCase(Locale.ROOT));
    }

    private EvidenceCompletenessItem score(EvidenceRecord r, Instant now) {
        List<EvidenceCompletenessFactor> factors = new ArrayList<>();
        int score = 0;

        factors.add(ok("Application", "Application recorded: " + r.getApplicationSlug() + "."));
        factors.add(ok("Framework", "Framework recorded: " + r.getFramework() + "."));
        factors.add(ok("Source system", "Source system recorded: " + r.getSourceSystem() + "."));

        EvidenceVersion v = r.latestVersion();
        IntegrityStatus integrityStatus;

        if (v == null) {
            integrityStatus = IntegrityStatus.UNKNOWN;
            factors.add(fail("Integrity", "No version recorded — nothing to verify."));
            factors.add(fail("Evidence content", "No evidence content has ever been collected."));
        } else {
            byte[] bytes = objectStore.get(v.getObjectKey()).orElse(null);
            if (bytes == null) {
                integrityStatus = IntegrityStatus.UNKNOWN;
                factors.add(fail("Integrity",
                        "Stored object is missing from the object store (key " + v.getObjectKey() + ")."));
                factors.add(fail("Evidence content", "Content is unavailable — cannot confirm it is non-trivial."));
            } else {
                String actual = Hashing.sha256Hex(bytes);
                if (actual.equalsIgnoreCase(v.getSha256())) {
                    integrityStatus = IntegrityStatus.VERIFIED;
                    score += WEIGHT_INTEGRITY;
                    factors.add(ok("Integrity", "SHA-256 matches the recorded hash."));
                    if (isSubstantive(bytes)) {
                        score += WEIGHT_CONTENT;
                        factors.add(ok("Evidence content",
                                "Content is present and non-trivial (" + bytes.length + " bytes)."));
                    } else {
                        factors.add(fail("Evidence content",
                                "Content is empty or looks like a placeholder (" + bytes.length + " bytes)."));
                    }
                } else {
                    integrityStatus = IntegrityStatus.TAMPERED;
                    factors.add(fail("Integrity",
                            "Recorded hash does not match current content — integrity check failed."));
                    factors.add(fail("Evidence content",
                            "Content cannot be trusted: the integrity check on it failed."));
                }
            }
        }

        Integer ageDays = null;
        if (v != null && r.getLatestCollectedAt() != null) {
            ageDays = (int) ChronoUnit.DAYS.between(r.getLatestCollectedAt(), now);
            if (ageDays <= staleAfterDays) {
                score += WEIGHT_FRESHNESS;
                factors.add(ok("Freshness", "Last collected " + ageDays + " day(s) ago."));
            } else {
                factors.add(fail("Freshness", "Last collected " + ageDays + " days ago, exceeds the "
                        + staleAfterDays + "-day threshold."));
            }
        } else {
            factors.add(fail("Freshness", "No collection date recorded."));
        }

        String collectedBy = v == null ? null : v.getCollectedBy();
        if (collectedBy != null && !collectedBy.isBlank()) {
            score += WEIGHT_COLLECTED_BY;
            factors.add(ok("Collected by", "Recorded as \"" + collectedBy + "\"."));
        } else {
            factors.add(fail("Collected by", "Missing: collector identity not recorded."));
        }

        String technology = tagValue(r, "technology");
        boolean technologyKnown = technology != null && !technology.isBlank()
                && !technology.equalsIgnoreCase("unknown");
        if (technologyKnown) {
            score += WEIGHT_TECHNOLOGY;
            factors.add(ok("Technology", "Recorded as \"" + technology + "\"."));
        } else {
            factors.add(fail("Technology", "Missing: technology not recorded."));
        }

        boolean mapped = controlFrameworks.isMapped(r.getControlId());
        if (mapped) {
            score += WEIGHT_CONTROL_MAPPING;
            factors.add(ok("Control mapping",
                    "Control " + r.getControlId() + " is mapped to a known framework set."));
        } else {
            factors.add(fail("Control mapping", "Control " + r.getControlId()
                    + " is not in the control-framework catalogue (orphaned/unmapped)."));
        }

        return new EvidenceCompletenessItem(
                r.getId().toString(), r.getApplicationSlug(), r.getFramework(), r.getControlId(),
                r.getSourceSystem(), collectedBy, technologyKnown ? technology : null,
                r.getCurrentVersion(), r.getLatestCollectedAt(), ageDays, r.getLatestSha256(),
                integrityStatus, score, band(score), factors);
    }

    private boolean isSubstantive(byte[] bytes) {
        if (bytes.length == 0) {
            return false;
        }
        String text = new String(bytes, StandardCharsets.UTF_8).trim();
        if (PLACEHOLDER_CONTENT.contains(text.toLowerCase(Locale.ROOT))) {
            return false;
        }
        return bytes.length >= MIN_SUBSTANTIVE_BYTES;
    }

    private static String band(int score) {
        if (score >= 90) return "COMPLETE";
        if (score >= 60) return "PARTIAL";
        return "INCOMPLETE";
    }

    private static String tagValue(EvidenceRecord r, String key) {
        for (EvidenceTag t : r.getTags()) {
            if (t.getTagKey().equals(key)) {
                return t.getTagValue();
            }
        }
        return null;
    }

    private static EvidenceCompletenessFactor ok(String factor, String detail) {
        return new EvidenceCompletenessFactor(factor, "ok", detail);
    }

    private static EvidenceCompletenessFactor fail(String factor, String detail) {
        return new EvidenceCompletenessFactor(factor, "fail", detail);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    private static double round(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
