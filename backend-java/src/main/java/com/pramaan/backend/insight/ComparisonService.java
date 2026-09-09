package com.pramaan.backend.insight;

import com.pramaan.backend.application.ApplicationService;
import com.pramaan.backend.common.ApiException;
import com.pramaan.backend.insight.InsightDtos.ComparisonReport;
import com.pramaan.backend.insight.InsightDtos.ComplianceReport;
import com.pramaan.backend.insight.InsightDtos.ControlComparisonRow;
import com.pramaan.backend.insight.InsightDtos.ControlPosture;
import com.pramaan.backend.insight.InsightDtos.FrameworkComparisonRow;
import com.pramaan.backend.insight.InsightDtos.GapRow;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * UC14 — cross-application compliance comparison. Pure re-aggregation of the
 * per-application {@link ComplianceService} results into a side-by-side matrix and
 * a gap list. No new scoring pipeline.
 */
@Service
@Transactional(readOnly = true)
public class ComparisonService {

    private final ApplicationService applications;
    private final ComplianceService compliance;
    private final Clock clock;

    public ComparisonService(ApplicationService applications, ComplianceService compliance, Clock clock) {
        this.applications = applications;
        this.compliance = compliance;
        this.clock = clock;
    }

    public ComparisonReport compare(List<String> slugs, String framework) {
        List<String> apps = slugs == null || slugs.isEmpty()
                ? applications.list().stream().map(a -> a.slug()).toList()
                : slugs.stream().map(String::trim).filter(s -> !s.isBlank()).toList();
        if (apps.size() < 2) {
            throw ApiException.badRequest("comparison needs at least 2 applications");
        }

        Map<String, ComplianceReport> reports = new LinkedHashMap<>();
        for (String slug : apps) {
            reports.put(slug, compliance.forApplication(slug, framework));
        }

        // framework rows
        Map<String, Map<String, Double>> fwPct = new TreeMap<>();
        reports.forEach((slug, r) -> r.byFramework().forEach(f ->
                fwPct.computeIfAbsent(f.framework(), k -> new LinkedHashMap<>())
                        .put(slug, f.compliancePct())));
        List<FrameworkComparisonRow> frameworks = new ArrayList<>();
        fwPct.forEach((fw, byApp) -> {
            double min = byApp.values().stream().mapToDouble(Double::doubleValue).min().orElse(0);
            double max = byApp.values().stream().mapToDouble(Double::doubleValue).max().orElse(0);
            frameworks.add(new FrameworkComparisonRow(fw, byApp, round(min), round(max), round(max - min)));
        });

        // control rows + gaps
        Map<String, Map<String, String>> ctrlStatus = new TreeMap<>();
        Map<String, String> ctrlFramework = new LinkedHashMap<>();
        List<GapRow> gaps = new ArrayList<>();
        reports.forEach((slug, r) -> {
            for (ControlPosture c : r.controls()) {
                String key = c.framework() + "|" + c.controlId();
                ctrlFramework.put(key, c.framework());
                ctrlStatus.computeIfAbsent(key, k -> new LinkedHashMap<>()).put(slug, c.status().name());
                if (c.status() != InsightDtos.ControlStatus.COMPLIANT) {
                    gaps.add(new GapRow(slug, c.framework(), c.controlId(), c.status().name()));
                }
            }
        });
        List<ControlComparisonRow> controls = new ArrayList<>();
        ctrlStatus.forEach((key, byApp) -> {
            boolean consistent = byApp.values().stream().distinct().count() <= 1;
            String fw = ctrlFramework.get(key);
            controls.add(new ControlComparisonRow(fw, key.substring(key.indexOf('|') + 1), byApp, consistent));
        });

        return new ComparisonReport(clock.instant(), framework, apps, frameworks, controls, gaps);
    }

    private static double round(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
