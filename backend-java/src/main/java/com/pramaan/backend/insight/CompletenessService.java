package com.pramaan.backend.insight;

import com.pramaan.backend.application.ApplicationService;
import com.pramaan.backend.config.PramaanProperties;
import com.pramaan.backend.evidence.EvidenceQueryService;
import com.pramaan.backend.evidence.EvidenceQueryService.EvidenceFilter;
import com.pramaan.backend.evidence.domain.EvidenceRecord;
import com.pramaan.backend.insight.InsightDtos.CompletenessReport;
import com.pramaan.backend.insight.InsightDtos.ControlCoverage;
import com.pramaan.backend.insight.InsightDtos.Coverage;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deterministic evidence-completeness: for every expected (application, control)
 * in the {@link ControlCatalog}, is there current evidence? No LLM.
 */
@Service
@Transactional(readOnly = true)
public class CompletenessService {

    private final ControlCatalog catalog;
    private final EvidenceQueryService evidence;
    private final ApplicationService applications;
    private final Clock clock;
    private final int staleAfterDays;

    public CompletenessService(ControlCatalog catalog, EvidenceQueryService evidence,
                               ApplicationService applications, Clock clock, PramaanProperties props) {
        this.catalog = catalog;
        this.evidence = evidence;
        this.applications = applications;
        this.clock = clock;
        this.staleAfterDays = props.scheduler() != null ? props.scheduler().staleAfterDays() : 90;
    }

    public CompletenessReport forApplication(String slug, String framework) {
        applications.require(slug); // 404 if unknown
        Instant now = clock.instant();

        Map<String, EvidenceRecord> latestByControl = evidence.recordsMatching(
                        new EvidenceFilter(slug, framework, null, null, null, null, null, 0, 100_000)).stream()
                .collect(Collectors.toMap(
                        r -> key(r.getFramework(), r.getControlId()),
                        Function.identity(),
                        (a, b) -> after(a.getLatestCollectedAt(), b.getLatestCollectedAt()) ? a : b));

        List<ControlCoverage> controls = catalog.forApplication(slug, framework).stream()
                .map(e -> coverage(e, latestByControl.get(key(e.framework(), e.controlId())), now))
                .sorted(Comparator.comparing(ControlCoverage::framework).thenComparing(ControlCoverage::controlId))
                .toList();

        int covered = (int) controls.stream().filter(c -> c.coverage() == Coverage.COVERED).count();
        int stale = (int) controls.stream().filter(c -> c.coverage() == Coverage.STALE).count();
        int missing = (int) controls.stream().filter(c -> c.coverage() == Coverage.MISSING).count();
        int expected = controls.size();
        double pct = expected == 0 ? 0.0 : round(100.0 * covered / expected);

        return new CompletenessReport(slug, framework, now, staleAfterDays,
                expected, covered, stale, missing, pct, controls);
    }

    private ControlCoverage coverage(ControlCatalog.Expectation e, EvidenceRecord rec, Instant now) {
        if (rec == null || rec.getLatestCollectedAt() == null) {
            return new ControlCoverage(e.framework(), e.controlId(), e.title(),
                    Coverage.MISSING, null, null, null, null);
        }
        int age = (int) ChronoUnit.DAYS.between(rec.getLatestCollectedAt(), now);
        Coverage cov = age > staleAfterDays ? Coverage.STALE : Coverage.COVERED;
        return new ControlCoverage(e.framework(), e.controlId(), e.title(), cov,
                rec.getId().toString(), rec.getCurrentVersion(), rec.getLatestCollectedAt(), age);
    }

    private static String key(String framework, String control) {
        return (framework == null ? "" : framework.toUpperCase()) + "|"
                + (control == null ? "" : control.toUpperCase());
    }

    private static boolean after(Instant a, Instant b) {
        if (a == null) return false;
        if (b == null) return true;
        return a.isAfter(b);
    }

    static double round(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
