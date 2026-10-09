package com.pramaan.backend.insight;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pramaan.backend.application.OnboardingService;
import com.pramaan.backend.evidence.EvidenceQueryService;
import com.pramaan.backend.evidence.EvidenceQueryService.EvidenceFilter;
import com.pramaan.backend.evidence.EvidenceQueryService.TagFacets;
import com.pramaan.backend.insight.DashboardKpiService.DashboardKpis;
import com.pramaan.backend.insight.DashboardKpiService.Filters;
import com.pramaan.backend.integrations.SourceConnectionService;
import com.pramaan.backend.integrations.SourceConnectionService.SetConnectionRequest;
import com.pramaan.backend.scheduler.ScheduleJobService;
import com.pramaan.backend.scheduler.SchedulerDtos.CreateScheduleRequest;
import com.pramaan.backend.scheduler.SchedulerDtos.RunRequest;
import com.pramaan.backend.scheduler.SchedulerRunExecutor;
import com.pramaan.backend.scheduler.SchedulerService;
import com.pramaan.backend.scheduler.domain.SchedulerRun.Trigger;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** UC01-FT016 - dashboard KPIs are computed from stored data and reconcile with the repository search. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DashboardKpiTest {

    @Autowired MockMvc mvc;
    @Autowired DashboardKpiService dashboard;
    @Autowired EvidenceQueryService queries;
    @Autowired OnboardingService onboarding;
    @Autowired ScheduleJobService jobs;
    @Autowired SchedulerService scheduler;
    @Autowired SchedulerRunExecutor executor;
    @Autowired SourceConnectionService connections;

    private static final Filters ALL = new Filters(null, null, null, null, null);

    @BeforeEach
    void onboardApps() {
        onboarding.apply(false);
    }

    private void scheduledRun(String name, String app, String source) {
        var job = jobs.create(new CreateScheduleRequest(name, source, app, "GENERAL", "DAILY", "tester"));
        executor.execute(UUID.fromString(jobs.runNow(UUID.fromString(job.id()), "tester").runId()));
    }

    private void manualRun(String app, String source) {
        var run = scheduler.trigger(new RunRequest(List.of(app), List.of(), List.of(source), "tester"), Trigger.MANUAL);
        executor.execute(UUID.fromString(run.runId()));
    }

    private long repoTotal(String app, String framework, String method, Instant from, Instant to) {
        return queries.search(new EvidenceFilter(app, framework, null, null, from, to, null, 0, 10),
                new TagFacets(null, method)).totalItems();
    }

    private void assertReconciles(DashboardKpis k, long repository) {
        var c = k.evidence().counts();
        assertThat(k.evidence().total()).isEqualTo((int) repository);
        assertThat(c.draft() + c.submitted() + c.approved() + c.rejected() + c.expired() + c.superseded())
                .isEqualTo(k.evidence().total());
    }

    @Test
    void totalsAndPerStateCountsReconcileWithTheRepositoryAndFiltersChangeTheNumbers() {
        scheduledRun("Pay pull", "payments", "MOCK_JIRA");
        manualRun("net-banking", "MOCK_GITHUB");

        DashboardKpis all = dashboard.kpis(ALL, "APP");
        assertThat(all.evidence().total()).isGreaterThan(0);
        assertReconciles(all, repoTotal(null, null, null, null, null));
        assertThat(all.evidence().closureRatePct()).isNotNull();

        DashboardKpis payments = dashboard.kpis(new Filters("payments", null, null, null, null), "APP");
        assertReconciles(payments, repoTotal("payments", null, null, null, null));
        assertThat(payments.evidence().total()).isLessThan(all.evidence().total());
        assertThat(payments.automated().totalRuns()).isEqualTo(1); // the net-banking run does not count

        for (String method : new String[] {"scheduled", "manual"}) {
            DashboardKpis byMethod = dashboard.kpis(new Filters(null, null, method, null, null), "APP");
            assertReconciles(byMethod, repoTotal(null, null, method, null, null));
            DashboardKpis both = dashboard.kpis(new Filters("payments", null, method, null, null), "APP");
            assertReconciles(both, repoTotal("payments", null, method, null, null));
        }
        // collectionMethod also narrows the run side: only SCHEDULED-trigger runs for "scheduled"
        DashboardKpis scheduledOnly = dashboard.kpis(new Filters(null, null, "scheduled", null, null), "APP");
        assertThat(scheduledOnly.automated().totalRuns()).isEqualTo(1);
        assertThat(scheduledOnly.automated().scheduledRuns()).isEqualTo(1);
        assertThat(dashboard.kpis(new Filters(null, null, "on-demand", null, null), "APP").automated().totalRuns()).isZero();

        // framework filter
        String framework = queries.search(new EvidenceFilter("payments", null, null, null, null, null, null, 0, 1), TagFacets.NONE)
                .items().get(0).framework();
        assertReconciles(dashboard.kpis(new Filters(null, framework, null, null, null), "APP"),
                repoTotal(null, framework, null, null, null));
    }

    @Test
    void failedAndDuplicateOnlyRunsMoveTheAutomatedKpisAsExpected() {
        manualRun("payments", "MOCK_JIRA");
        DashboardKpis base = dashboard.kpis(ALL, "APP");
        assertThat(base.automated().failed()).isZero();
        assertThat(base.automated().successRatePct()).isEqualTo(100.0);

        // duplicate-only: same unchanged source again -> a run and duplicates, but no new evidence
        manualRun("payments", "MOCK_JIRA");
        DashboardKpis dup = dashboard.kpis(ALL, "APP");
        assertThat(dup.automated().totalRuns()).isEqualTo(base.automated().totalRuns() + 1);
        assertThat(dup.automated().duplicatesSkipped()).isGreaterThan(base.automated().duplicatesSkipped());
        assertThat(dup.automated().evidenceIngested()).isEqualTo(base.automated().evidenceIngested());
        assertThat(dup.evidence().total()).isEqualTo(base.evidence().total());

        // failed run (source credential invalid)
        connections.set("MOCK_GITHUB", new SetConnectionRequest("INVALID", "tester"));
        manualRun("payments", "MOCK_GITHUB");
        DashboardKpis failed = dashboard.kpis(ALL, "APP");
        assertThat(failed.automated().failed()).isEqualTo(1);
        assertThat(failed.automated().successRatePct()).isLessThan(100.0);
        assertThat(failed.automated().lastRun().status()).isEqualTo("FAILED");
        assertThat(failed.evidence().total()).isEqualTo(dup.evidence().total()); // nothing ingested
        assertThat(failed.automated().runsWithinSla() + failed.automated().runsBreached())
                .isEqualTo(failed.automated().completed() + failed.automated().partial() + failed.automated().failed());
    }

    @Test
    void aDateRangeWithNoDataReturnsZerosAndEndpointValidatesInput() throws Exception {
        scheduledRun("Pay pull", "payments", "MOCK_JIRA");
        Instant from = DashboardKpiService.parseInstant("from", "2000-01-01", false);
        Instant to = DashboardKpiService.parseInstant("to", "2000-01-02", true);

        DashboardKpis none = dashboard.kpis(new Filters(null, null, null, from, to), "APP");
        assertThat(none.evidence().total()).isZero();
        assertThat(none.evidence().counts().draft() + none.evidence().counts().submitted()).isZero();
        assertThat(none.evidence().closureRatePct()).isNull();
        assertThat(none.automated().totalRuns()).isZero();
        assertThat(none.automated().successRatePct()).isNull();
        assertThat(none.automated().lastRun()).isNull();
        assertThat(none.automated().evidenceIngested()).isZero();
        assertReconciles(none, repoTotal(null, null, null, from, to));

        mvc.perform(get("/api/v1/dashboard/kpis").param("application", "payments").param("collectionMethod", "scheduled")
                        .header("X-Pramaan-Role", "APP"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.automated.totalRuns").value(1))
                .andExpect(jsonPath("$.automated.activeSchedules").value(1))
                .andExpect(jsonPath("$.filters.collectionMethod").value("scheduled"));
        mvc.perform(get("/api/v1/dashboard/kpis").param("collectionMethod", "carrier-pigeon").header("X-Pramaan-Role", "APP"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/dashboard/kpis").param("from", "yesterday").header("X-Pramaan-Role", "APP"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/dashboard/kpis")).andExpect(status().isForbidden()); // no role header
    }
}
