package com.pramaan.backend.insight;

import com.pramaan.backend.application.ApplicationDtos.ApplicationView;
import com.pramaan.backend.application.ApplicationService;
import com.pramaan.backend.insight.InsightDtos.AppPosture;
import com.pramaan.backend.insight.InsightDtos.ComplianceReport;
import com.pramaan.backend.insight.InsightDtos.CompletenessReport;
import com.pramaan.backend.insight.InsightDtos.ControlStatus;
import com.pramaan.backend.insight.InsightDtos.FrameworkPosture;
import com.pramaan.backend.insight.InsightDtos.LeadershipDashboard;
import com.pramaan.backend.rules.CheckStatus;
import com.pramaan.backend.rules.domain.CheckResult;
import com.pramaan.backend.rules.repo.CheckResultRepository;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Leadership compliance dashboard — a portfolio rollup across every onboarded
 * application. Aggregates the <b>actual</b> deterministic results already computed
 * by {@link ComplianceService} / {@link CompletenessService} (which read persisted
 * evidence + persisted {@link CheckResult} verdicts). No new scoring, no LLM.
 */
@Service
@Transactional(readOnly = true)
public class LeadershipService {

    private final ApplicationService applications;
    private final ComplianceService compliance;
    private final CompletenessService completeness;
    private final CheckResultRepository checkResults;
    private final Clock clock;

    public LeadershipService(ApplicationService applications, ComplianceService compliance,
                             CompletenessService completeness, CheckResultRepository checkResults,
                             Clock clock) {
        this.applications = applications;
        this.compliance = compliance;
        this.completeness = completeness;
        this.checkResults = checkResults;
        this.clock = clock;
    }

    public LeadershipDashboard dashboard() {
        List<AppPosture> byApp = new ArrayList<>();
        Map<String, int[]> fw = new TreeMap<>(); // framework -> [expected,compliant,partial,non,notAssessed,missingEv]
        int expected = 0, compliant = 0, covered = 0, stale = 0, missing = 0;

        for (ApplicationView app : applications.list()) {
            ComplianceReport cr = compliance.forApplication(app.slug(), null);
            CompletenessReport cp = completeness.forApplication(app.slug(), null);

            int nonCompliant = (int) cr.controls().stream()
                    .filter(c -> c.status() == ControlStatus.NON_COMPLIANT).count();
            int missingEvidence = (int) cr.controls().stream()
                    .filter(c -> c.status() == ControlStatus.MISSING_EVIDENCE).count();

            byApp.add(new AppPosture(app.slug(), app.name(), app.criticality(),
                    cr.expected(), cr.compliant(), cr.compliancePct(),
                    cp.covered(), cp.missing(), cp.completenessPct(),
                    nonCompliant, missingEvidence));

            expected += cr.expected();
            compliant += cr.compliant();
            covered += cp.covered();
            stale += cp.stale();
            missing += cp.missing();

            for (FrameworkPosture f : cr.byFramework()) {
                int[] a = fw.computeIfAbsent(f.framework(), k -> new int[6]);
                a[0] += f.expected();
                a[1] += f.compliant();
                a[2] += f.partiallyCompliant();
                a[3] += f.nonCompliant();
                a[4] += f.notAssessed();
                a[5] += f.missingEvidence();
            }
        }

        List<FrameworkPosture> byFramework = new ArrayList<>();
        fw.forEach((name, a) -> byFramework.add(new FrameworkPosture(name, a[0], a[1], a[2], a[3], a[4], a[5],
                a[0] == 0 ? 0.0 : CompletenessService.round(100.0 * a[1] / a[0]))));

        double compliancePct = expected == 0 ? 0.0 : CompletenessService.round(100.0 * compliant / expected);
        double completenessPct = expected == 0 ? 0.0 : CompletenessService.round(100.0 * covered / expected);

        return new LeadershipDashboard(clock.instant(), byApp.size(), expected, compliant, compliancePct,
                covered, stale, missing, completenessPct, verdictCounts(), byApp, byFramework);
    }

    /** Actual persisted check-verdict tally across the whole estate. */
    private Map<String, Integer> verdictCounts() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (CheckStatus s : CheckStatus.values()) {
            counts.put(s.name(), 0);
        }
        for (CheckResult r : checkResults.findAll()) {
            counts.merge(r.getStatus().name(), 1, Integer::sum);
        }
        return counts;
    }
}
