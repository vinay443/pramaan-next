package com.pramaan.backend.insight;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pramaan.backend.application.ApplicationDtos.ApplicationView;
import com.pramaan.backend.application.ApplicationService;
import com.pramaan.backend.insight.InsightDtos.AppPosture;
import com.pramaan.backend.insight.InsightDtos.ComplianceReport;
import com.pramaan.backend.insight.InsightDtos.EnterpriseDashboard;
import com.pramaan.backend.insight.InsightDtos.FrameworkPosture;
import com.pramaan.backend.insight.InsightDtos.GroupPosture;
import com.pramaan.backend.insight.InsightDtos.LeadershipDashboard;
import com.pramaan.backend.insight.InsightDtos.NationalDashboard;
import com.pramaan.backend.insight.InsightDtos.NationalRollup;
import com.pramaan.backend.insight.InsightDtos.RegionFrameworkRow;
import com.pramaan.backend.insight.InsightDtos.RegionGap;
import com.pramaan.backend.insight.InsightDtos.RegionPosture;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * UC16 (enterprise) + UC20 (national) dashboards. Both are decorations of the
 * existing {@link LeadershipService} portfolio rollup — enterprise adds
 * business-unit / criticality cuts, national maps applications to (mock) regions.
 * No second dashboard pipeline.
 */
@Service
@Transactional(readOnly = true)
public class EnterpriseDashboardService {

    private static final Map<String, Integer> CRITICALITY_WEIGHT =
            Map.of("CRITICAL", 4, "HIGH", 3, "MEDIUM", 2, "LOW", 1);

    private final LeadershipService leadership;
    private final ApplicationService applications;
    private final ComplianceService compliance;
    private final Clock clock;
    private final List<Region> regions;

    private record Region(String name, List<String> applications) {}
    private record RegionsFile(List<Region> regions) {}

    public EnterpriseDashboardService(LeadershipService leadership, ApplicationService applications,
                                      ComplianceService compliance, Clock clock, ObjectMapper mapper,
                                      @Value("${pramaan.national.regions:classpath:phase2/national-regions.json}")
                                      Resource regionsResource) {
        this.leadership = leadership;
        this.applications = applications;
        this.compliance = compliance;
        this.clock = clock;
        try (InputStream in = regionsResource.getInputStream()) {
            this.regions = List.copyOf(mapper.readValue(in, RegionsFile.class).regions());
        } catch (IOException e) {
            throw new UncheckedIOException("cannot load national regions: " + regionsResource, e);
        }
    }

    public EnterpriseDashboard enterprise() {
        return enterprise(null);
    }

    /** Enterprise dashboard restricted to the given business units (null/empty = all). */
    public EnterpriseDashboard enterprise(List<String> businessUnits) {
        NationalRollup r = nationalRollup(businessUnits);
        return new EnterpriseDashboard(r.generatedAt(), r.portfolio(), r.byBusinessUnit(), r.byCriticality(),
                r.topRisks());
    }

    public NationalDashboard national() {
        return national(leadership.dashboard());
    }

    private NationalDashboard national(LeadershipDashboard portfolio) {
        Map<String, AppPosture> byApp = portfolio.byApplication().stream()
                .collect(Collectors.toMap(AppPosture::applicationSlug, Function.identity()));

        List<RegionPosture> out = new ArrayList<>();
        int totalExpected = 0;
        int totalCompliant = 0;
        int totalCovered = 0;
        for (Region r : regions) {
            int expected = 0;
            int compliant = 0;
            int covered = 0;
            List<String> apps = new ArrayList<>();
            for (String slug : r.applications()) {
                AppPosture p = byApp.get(slug);
                if (p == null) {
                    continue;
                }
                apps.add(slug);
                expected += p.expected();
                compliant += p.compliant();
                covered += p.covered();
            }
            double pct = expected == 0 ? 0.0 : round(100.0 * compliant / expected);
            double cov = expected == 0 ? 0.0 : round(100.0 * covered / expected);
            out.add(new RegionPosture(r.name(), apps, expected, compliant, pct, cov, rag(pct)));
            totalExpected += expected;
            totalCompliant += compliant;
            totalCovered += covered;
        }
        double natPct = totalExpected == 0 ? 0.0 : round(100.0 * totalCompliant / totalExpected);
        double natCov = totalExpected == 0 ? 0.0 : round(100.0 * totalCovered / totalExpected);
        int appCount = (int) out.stream().flatMap(rp -> rp.applications().stream()).distinct().count();
        return new NationalDashboard(clock.instant(), natPct, natCov, appCount, out);
    }

    public NationalRollup nationalRollup() {
        return nationalRollup(null);
    }

    /**
     * The single national + enterprise dashboard (source of truth; {@code /enterprise} and
     * {@code /national} are deprecated views over it): region x framework compliance
     * breakdown, regions ranked by how far they trail the national average (furthest-behind
     * first) plus the per-region RAG table, and the business-unit / criticality cuts and top
     * risks. {@code businessUnits} restricts every part to applications in those units
     * (null/empty = all). Built over the same region mapping, {@link LeadershipService}
     * rollup and {@link ComplianceService} posture used elsewhere — no new scoring.
     */
    public NationalRollup nationalRollup(List<String> businessUnits) {
        LeadershipDashboard portfolio = leadership.dashboard(businessUnits);
        NationalDashboard nationalDash = national(portfolio);
        Set<String> knownSlugs = portfolio.byApplication().stream()
                .map(AppPosture::applicationSlug).collect(Collectors.toSet());

        Map<String, ApplicationView> meta = applications.list().stream()
                .collect(Collectors.toMap(ApplicationView::slug, Function.identity()));
        List<GroupPosture> byUnit = group(portfolio.byApplication(),
                p -> orDefault(meta.get(p.applicationSlug()) == null ? null
                        : meta.get(p.applicationSlug()).businessUnit(), "Unassigned"));
        List<GroupPosture> byCriticality = group(portfolio.byApplication(),
                p -> orDefault(p.criticality(), "MEDIUM"));
        // most critical first, then lowest compliance — so critical apps with low compliance lead
        List<AppPosture> topRisks = portfolio.byApplication().stream()
                .sorted(Comparator
                        .comparingInt((AppPosture p) -> -CRITICALITY_WEIGHT.getOrDefault(
                                orDefault(p.criticality(), "MEDIUM"), 2))
                        .thenComparingDouble(AppPosture::compliancePct))
                .limit(5)
                .toList();

        Map<String, int[]> byRegionFramework = new LinkedHashMap<>(); // "region|framework" -> [expected, compliant]
        for (Region r : regions) {
            for (String slug : r.applications()) {
                if (!knownSlugs.contains(slug)) {
                    continue;
                }
                ComplianceReport cr = compliance.forApplication(slug, null);
                for (FrameworkPosture f : cr.byFramework()) {
                    int[] a = byRegionFramework.computeIfAbsent(r.name() + "|" + f.framework(), k -> new int[2]);
                    a[0] += f.expected();
                    a[1] += f.compliant();
                }
            }
        }
        List<RegionFrameworkRow> rows = byRegionFramework.entrySet().stream()
                .map(e -> {
                    String[] parts = e.getKey().split("\\|", 2);
                    int expected = e.getValue()[0];
                    int compliant = e.getValue()[1];
                    double pct = expected == 0 ? 0.0 : round(100.0 * compliant / expected);
                    return new RegionFrameworkRow(parts[0], parts[1], expected, compliant, pct);
                })
                .sorted(Comparator.comparing(RegionFrameworkRow::region).thenComparing(RegionFrameworkRow::framework))
                .toList();

        double nationalPct = nationalDash.nationalCompliancePct();
        List<RegionGap> laggingRegions = nationalDash.regions().stream()
                .map(r -> new RegionGap(r.region(), r.compliancePct(), round(r.compliancePct() - nationalPct), r.rag()))
                .sorted(Comparator.comparingDouble(RegionGap::gapVsNationalPct))
                .toList();

        return new NationalRollup(clock.instant(), nationalPct, nationalDash.nationalCompletenessPct(),
                nationalDash.applications(), rows, laggingRegions, portfolio, nationalDash.regions(),
                byUnit, byCriticality, topRisks);
    }

    private static List<GroupPosture> group(List<AppPosture> apps, Function<AppPosture, String> key) {
        Map<String, int[]> acc = new LinkedHashMap<>(); // key -> [apps, expected, compliant, covered]
        for (AppPosture p : apps) {
            int[] a = acc.computeIfAbsent(key.apply(p), k -> new int[4]);
            a[0]++;
            a[1] += p.expected();
            a[2] += p.compliant();
            a[3] += p.covered();
        }
        List<GroupPosture> out = new ArrayList<>();
        acc.forEach((k, a) -> out.add(new GroupPosture(k, a[0], a[1], a[2],
                a[1] == 0 ? 0.0 : round(100.0 * a[2] / a[1]),
                a[1] == 0 ? 0.0 : round(100.0 * a[3] / a[1]))));
        out.sort(Comparator.comparing(GroupPosture::key));
        return out;
    }

    private static String rag(double pct) {
        return pct >= 85 ? "GREEN" : pct >= 60 ? "AMBER" : "RED";
    }

    private static String orDefault(String v, String d) {
        return v == null || v.isBlank() ? d : v;
    }

    private static double round(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
