package com.pramaan.backend.insight;

import static org.assertj.core.api.Assertions.assertThat;

import com.pramaan.backend.evidence.EvidenceDtos.IngestRequest;
import com.pramaan.backend.evidence.EvidenceDtos.LifecycleAction;
import com.pramaan.backend.evidence.EvidenceDtos.LifecycleTransitionRequest;
import com.pramaan.backend.evidence.EvidenceIngestionService;
import com.pramaan.backend.evidence.EvidenceLifecycleService;
import com.pramaan.backend.insight.InsightDtos.AuditPrepReport;
import com.pramaan.backend.insight.InsightDtos.ComparisonReport;
import com.pramaan.backend.insight.InsightDtos.ComplianceReport;
import com.pramaan.backend.insight.InsightDtos.ControlStatus;
import com.pramaan.backend.insight.InsightDtos.EnterpriseDashboard;
import com.pramaan.backend.insight.InsightDtos.LeadershipDashboard;
import com.pramaan.backend.insight.InsightDtos.NationalDashboard;
import com.pramaan.backend.insight.InsightDtos.TrendReport;
import com.pramaan.backend.rules.CheckDtos.EvaluateRequest;
import com.pramaan.backend.rules.RuleEvaluationService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PortfolioInsightTest {

    @Autowired EvidenceIngestionService ingestion;
    @Autowired EvidenceLifecycleService lifecycle;
    @Autowired RuleEvaluationService rules;
    @Autowired ComparisonService comparison;
    @Autowired EnterpriseDashboardService enterprise;
    @Autowired AuditPrepService auditPrep;
    @Autowired TrendService trend;
    @Autowired com.pramaan.backend.application.ApplicationService applications;
    @Autowired ComplianceService compliance;
    @Autowired LeadershipService leadership;

    private UUID ingest(String app, String control, String framework, String body) {
        return UUID.fromString(ingestion.ingest(new IngestRequest(app, control, framework, "SHAREPOINT",
                app + "/" + control, control, "application/json", null, body,
                Instant.now().minus(2, ChronoUnit.DAYS), "agent", Map.of(), Map.of())).evidenceId());
    }

    @BeforeEach
    void seed() {
        ingest("net-banking", "OS-SSH-ROOT-LOGIN", "C-SITE",
                "{\"control\":\"OS-SSH-ROOT-LOGIN\",\"findings\":[{\"checkId\":\"OS-LNX-02\",\"status\":\"PASS\"}]}");
        ingest("net-banking", "MW-HSTS", "DPSC",
                "{\"control\":\"MW-HSTS\",\"findings\":[{\"checkId\":\"MW-03\",\"status\":\"FAIL\"}]}");
        ingest("payments", "OS-SSH-ROOT-LOGIN", "C-SITE",
                "{\"control\":\"OS-SSH-ROOT-LOGIN\",\"findings\":[{\"checkId\":\"OS-LNX-02\",\"status\":\"PASS\"}]}");
        rules.evaluate(new EvaluateRequest(null, null, null, null));
    }

    @Test
    void uc14_comparisonBuildsAMatrixAndGapListAcrossApplications() {
        ComparisonReport r = comparison.compare(List.of("net-banking", "payments"), null);
        assertThat(r.applications()).containsExactly("net-banking", "payments");
        assertThat(r.frameworks()).isNotEmpty();
        assertThat(r.controls()).anyMatch(c -> c.controlId().equals("OS-SSH-ROOT-LOGIN"));
        assertThat(r.gaps()).anyMatch(g -> g.controlId().equals("MW-HSTS")
                && g.applicationSlug().equals("net-banking"));
    }

    @Test
    void uc16_enterpriseDashboardDecoratesThePortfolioRollup() {
        EnterpriseDashboard d = enterprise.enterprise();
        assertThat(d.portfolio().applications()).isGreaterThanOrEqualTo(2);
        assertThat(d.byBusinessUnit()).isNotEmpty();
        assertThat(d.byCriticality()).isNotEmpty();
        assertThat(d.topRisks()).isNotEmpty();
    }

    @Test
    void uc20_nationalDashboardRollsRegionsUpToANationalScore() {
        NationalDashboard d = enterprise.national();
        assertThat(d.regions()).anyMatch(rp -> rp.region().equals("North"));
        assertThat(d.regions()).allSatisfy(rp -> assertThat(rp.rag()).isIn("GREEN", "AMBER", "RED"));
        assertThat(d.nationalCompliancePct()).isBetween(0.0, 100.0);
    }

    @Test
    void nationalRollupBreaksDownByRegionAndFrameworkAndRanksLaggingRegions() {
        var rollup = enterprise.nationalRollup();
        assertThat(rollup.byRegionFramework()).isNotEmpty();
        assertThat(rollup.byRegionFramework()).allSatisfy(row -> {
            assertThat(row.region()).isNotBlank();
            assertThat(row.framework()).isNotBlank();
            assertThat(row.compliancePct()).isBetween(0.0, 100.0);
        });
        assertThat(rollup.laggingRegions()).isNotEmpty();
        // sorted so the furthest-behind region (most negative gap) comes first
        assertThat(rollup.laggingRegions().get(0).gapVsNationalPct())
                .isLessThanOrEqualTo(rollup.laggingRegions().get(rollup.laggingRegions().size() - 1).gapVsNationalPct());
    }

    @Test
    void mergedRollupCarriesEnterpriseCutsAndRespectsBusinessUnitScope() {
        applications.upsert(new com.pramaan.backend.application.ApplicationDtos.UpsertRequest(
                "net-banking", "Net Banking", "Retail Banking", "HIGH", null, null));
        applications.upsert(new com.pramaan.backend.application.ApplicationDtos.UpsertRequest(
                "payments", "Payments", "Payments", "CRITICAL", null, null));

        var all = enterprise.nationalRollup();
        assertThat(all.byBusinessUnit()).extracting(g -> g.key()).contains("Payments", "Retail Banking");
        assertThat(all.byCriticality()).isNotEmpty();
        assertThat(all.regions()).extracting(r -> r.rag()).allMatch(r -> List.of("GREEN", "AMBER", "RED").contains(r));
        assertThat(all.topRisks().get(0).criticality()).isEqualTo("CRITICAL"); // critical apps lead the risk list
        // the deprecated /enterprise view is exactly the merged data
        EnterpriseDashboard ent = enterprise.enterprise();
        assertThat(ent.byBusinessUnit()).isEqualTo(all.byBusinessUnit());
        assertThat(ent.topRisks()).isEqualTo(all.topRisks());

        var scoped = enterprise.nationalRollup(List.of("Payments"));
        assertThat(scoped.byBusinessUnit()).extracting(g -> g.key()).containsExactly("Payments");
        assertThat(scoped.portfolio().applications()).isEqualTo(1);
    }

    @Test
    void uc18_auditPrepChecklistIsDeterministicAndGrounded() {
        AuditPrepReport r = auditPrep.prepare("net-banking", null);
        assertThat(r.simulated()).isTrue();
        assertThat(r.totalFindings()).isGreaterThan(0);
        assertThat(r.findings()).anyMatch(f -> f.category().equals("NON_COMPLIANT")
                && f.controlId().equals("MW-HSTS"));
        // every finding names a real control (no fabrication)
        assertThat(r.findings()).allSatisfy(f -> assertThat(f.controlId()).isNotBlank());
        assertThat(r.narrative()).contains("[mock-ai]");
    }

    @Test
    void defect2_ingestAutoTriggersRuleEvaluationNoManualStep() {
        // fresh control on a fresh app — the @BeforeEach evaluate() ran before this ingest
        ingestion.ingest(new IngestRequest("mobile-banking", "OS-AUDIT-LOGGING", "PCI_DSS", "AGENT_OS_LINUX",
                "mb/audit", "audit", "application/json", null,
                "{\"control\":\"OS-AUDIT-LOGGING\",\"findings\":[{\"checkId\":\"OS-LNX-04\",\"status\":\"PASS\"}]}",
                Instant.now(), "agent", Map.of(), Map.of()));

        ComplianceReport r = compliance.forApplication("mobile-banking", null);
        assertThat(r.controls()).anyMatch(c -> c.controlId().equals("OS-AUDIT-LOGGING")
                && c.status() == ControlStatus.COMPLIANT);
        assertThat(r.compliant()).isGreaterThan(0);
    }

    @Test
    void defect3_trendHeadlineIsTheLiveRollupNotTheLastSnapshot() {
        rules.evaluate(new com.pramaan.backend.rules.CheckDtos.EvaluateRequest(null, null, null, null));
        LeadershipDashboard live = leadership.dashboard();
        TrendReport t = trend.trend();
        assertThat(t.current()).isNotNull();
        assertThat(t.current().compliancePct()).isEqualTo(live.compliancePct());
        assertThat(t.current().expected()).isEqualTo(live.expected());
    }

    @Test
    void uc19_trendScopedToBusinessUnit() {
        applications.upsert(new com.pramaan.backend.application.ApplicationDtos.UpsertRequest(
                "payments", "Payments", "Payments", "CRITICAL", null, null));
        trend.snapshot();
        TrendReport scoped = trend.trend("Payments");
        int paymentsExpected = leadership.dashboard().byApplication().stream()
                .filter(a -> a.applicationSlug().equals("payments")).mapToInt(a -> a.expected()).sum();
        assertThat(paymentsExpected).isGreaterThan(0);
        assertThat(scoped.current().expected()).isEqualTo(paymentsExpected);
        assertThat(scoped.points()).isNotEmpty();
        assertThat(scoped.points()).allMatch(p -> p.expected() <= trend.trend().current().expected());

        TrendReport none = trend.trend("No Such Unit");
        assertThat(none.points()).isEmpty();
        assertThat(none.current().expected()).isZero();
    }

    @Test
    void businessUnitScopeRestrictsLeadershipEnterpriseComparisonAndTrend() {
        applications.upsert(new com.pramaan.backend.application.ApplicationDtos.UpsertRequest(
                "net-banking", "Net Banking", "Retail Banking", "HIGH", null, null));
        applications.upsert(new com.pramaan.backend.application.ApplicationDtos.UpsertRequest(
                "payments", "Payments", "Payments", "CRITICAL", null, null));
        trend.snapshot();

        LeadershipDashboard all = leadership.dashboard();
        LeadershipDashboard one = leadership.dashboard(List.of("payments")); // case-insensitive
        assertThat(one.byApplication()).extracting(a -> a.applicationSlug()).containsExactly("payments");
        assertThat(one.applications()).isEqualTo(1);
        assertThat(one.expected()).isLessThan(all.expected());
        assertThat(leadership.dashboard(List.of("No Such Unit")).byApplication()).isEmpty();
        assertThat(leadership.dashboard(List.of()).byApplication()).hasSameSizeAs(all.byApplication());

        EnterpriseDashboard ent = enterprise.enterprise(List.of("Payments", "Retail Banking"));
        assertThat(ent.byBusinessUnit()).extracting(g -> g.key())
                .containsExactlyInAnyOrder("Payments", "Retail Banking");
        assertThat(enterprise.enterprise(List.of("Payments")).byBusinessUnit()).extracting(g -> g.key())
                .containsExactly("Payments");

        ComparisonReport cmp = comparison.compare(null, List.of("Payments", "Retail Banking"), null);
        assertThat(cmp.applications()).containsExactlyInAnyOrder("payments", "net-banking");

        TrendReport t = trend.trend(List.of("Payments", "Retail Banking"));
        assertThat(t.current().expected()).isEqualTo(
                leadership.dashboard(List.of("Payments", "Retail Banking")).expected());
        assertThat(trend.trend(List.of("Payments")).current().expected()).isEqualTo(one.expected());
    }

    @Test
    void uc19_trendSnapshotsAndClosureMetrics() {
        UUID id = ingest("payments", "TLS-CERT-EXPIRY", "C-SITE", "{\"v\":1}");
        lifecycle.transition(id, new LifecycleTransitionRequest(LifecycleAction.SUBMIT, "o", null),
                "APP_OWNER", null);
        lifecycle.transition(id, new LifecycleTransitionRequest(LifecycleAction.APPROVE, "a", null),
                "AUDITOR", null);

        trend.snapshot();
        trend.snapshot();
        TrendReport t = trend.trend();
        assertThat(t.points()).hasSizeGreaterThanOrEqualTo(2);
        assertThat(t.points().get(0).expected()).isGreaterThan(0);
        // dashboard-trend metrics captured in the snapshot
        assertThat(t.current().evidenceCount()).isGreaterThan(0);
        assertThat(t.current().integrityChecked()).isGreaterThan(0);
        assertThat(t.current().integrityPct()).isBetween(0.0, 100.0);
        assertThat(t.closure().approvals()).isGreaterThanOrEqualTo(1);
        assertThat(t.closure().avgDaysToApprove()).isNotNull();
    }
}
