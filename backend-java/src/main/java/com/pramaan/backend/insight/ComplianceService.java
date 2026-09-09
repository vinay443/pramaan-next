package com.pramaan.backend.insight;

import com.pramaan.backend.application.ApplicationService;
import com.pramaan.backend.evidence.EvidenceQueryService;
import com.pramaan.backend.evidence.EvidenceQueryService.EvidenceFilter;
import com.pramaan.backend.evidence.domain.EvidenceRecord;
import com.pramaan.backend.insight.InsightDtos.ComplianceReport;
import com.pramaan.backend.insight.InsightDtos.ControlPosture;
import com.pramaan.backend.insight.InsightDtos.ControlStatus;
import com.pramaan.backend.insight.InsightDtos.FrameworkPosture;
import com.pramaan.backend.rules.CheckStatus;
import com.pramaan.backend.rules.domain.CheckResult;
import com.pramaan.backend.rules.repo.CheckResultRepository;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Compliance aggregation: rolls the deterministic check verdicts up against the
 * expected-control catalog into per-control, per-framework and per-application
 * posture. Pure aggregation — no LLM.
 */
@Service
@Transactional(readOnly = true)
public class ComplianceService {

    private final ControlCatalog catalog;
    private final EvidenceQueryService evidence;
    private final CheckResultRepository checkResults;
    private final ApplicationService applications;
    private final Clock clock;

    public ComplianceService(ControlCatalog catalog, EvidenceQueryService evidence,
                             CheckResultRepository checkResults, ApplicationService applications, Clock clock) {
        this.catalog = catalog;
        this.evidence = evidence;
        this.checkResults = checkResults;
        this.applications = applications;
        this.clock = clock;
    }

    public ComplianceReport forApplication(String slug, String framework) {
        applications.require(slug);

        Map<String, List<EvidenceRecord>> evidenceByControl = evidence.recordsMatching(
                        new EvidenceFilter(slug, framework, null, null, null, null, null, 0, 100_000)).stream()
                .collect(Collectors.groupingBy(r -> key(r.getFramework(), r.getControlId())));

        List<ControlPosture> controls = catalog.forApplication(slug, framework).stream()
                .map(e -> posture(e, evidenceByControl.getOrDefault(key(e.framework(), e.controlId()), List.of())))
                .sorted(Comparator.comparing(ControlPosture::framework).thenComparing(ControlPosture::controlId))
                .toList();

        List<FrameworkPosture> byFramework = rollupByFramework(controls);
        int expected = controls.size();
        int compliant = (int) controls.stream().filter(c -> c.status() == ControlStatus.COMPLIANT).count();
        double pct = expected == 0 ? 0.0 : CompletenessService.round(100.0 * compliant / expected);

        return new ComplianceReport(slug, clock.instant(), expected, compliant, pct, byFramework, controls);
    }

    private ControlPosture posture(ControlCatalog.Expectation e, List<EvidenceRecord> records) {
        if (records.isEmpty()) {
            return new ControlPosture(e.framework(), e.controlId(), ControlStatus.MISSING_EVIDENCE,
                    "no evidence held for this control");
        }
        List<CheckResult> verdicts = new ArrayList<>();
        for (EvidenceRecord r : records) {
            verdicts.addAll(checkResults.findByEvidenceRecordId(r.getId()));
        }
        if (verdicts.isEmpty()) {
            return new ControlPosture(e.framework(), e.controlId(), ControlStatus.NOT_ASSESSED,
                    records.size() + " evidence item(s) present but no rule has evaluated them");
        }
        boolean anyFail = verdicts.stream().anyMatch(v -> v.getStatus() == CheckStatus.FAIL);
        boolean anyWarn = verdicts.stream().anyMatch(v -> v.getStatus() == CheckStatus.WARNING);
        boolean anyPass = verdicts.stream().anyMatch(v -> v.getStatus() == CheckStatus.PASS);
        if (anyFail) {
            return new ControlPosture(e.framework(), e.controlId(), ControlStatus.NON_COMPLIANT,
                    "at least one check failed");
        }
        if (anyWarn) {
            return new ControlPosture(e.framework(), e.controlId(), ControlStatus.PARTIALLY_COMPLIANT,
                    "checks passed with warnings");
        }
        if (anyPass) {
            return new ControlPosture(e.framework(), e.controlId(), ControlStatus.COMPLIANT,
                    "all checks passed");
        }
        return new ControlPosture(e.framework(), e.controlId(), ControlStatus.NOT_ASSESSED,
                "checks are not applicable");
    }

    private List<FrameworkPosture> rollupByFramework(List<ControlPosture> controls) {
        Map<String, List<ControlPosture>> byFw = new TreeMap<>();
        controls.forEach(c -> byFw.computeIfAbsent(c.framework(), k -> new ArrayList<>()).add(c));
        List<FrameworkPosture> out = new ArrayList<>();
        byFw.forEach((fw, list) -> {
            int expected = list.size();
            int compliant = count(list, ControlStatus.COMPLIANT);
            out.add(new FrameworkPosture(fw, expected, compliant,
                    count(list, ControlStatus.PARTIALLY_COMPLIANT),
                    count(list, ControlStatus.NON_COMPLIANT),
                    count(list, ControlStatus.NOT_ASSESSED),
                    count(list, ControlStatus.MISSING_EVIDENCE),
                    expected == 0 ? 0.0 : CompletenessService.round(100.0 * compliant / expected)));
        });
        return out;
    }

    private static int count(List<ControlPosture> list, ControlStatus s) {
        return (int) list.stream().filter(c -> c.status() == s).count();
    }

    private static String key(String framework, String control) {
        return (framework == null ? "" : framework.toUpperCase()) + "|"
                + (control == null ? "" : control.toUpperCase());
    }
}
