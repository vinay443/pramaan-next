package com.pramaan.backend.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import com.pramaan.backend.application.OnboardingService;
import com.pramaan.backend.evidence.EvidenceDtos.EvidenceView;
import com.pramaan.backend.evidence.EvidenceQueryService;
import com.pramaan.backend.evidence.EvidenceQueryService.EvidenceFilter;
import com.pramaan.backend.evidence.EvidenceQueryService.TagFacets;
import com.pramaan.backend.scheduler.SchedulerDtos.RunRequest;
import com.pramaan.backend.scheduler.SchedulerDtos.RunView;
import com.pramaan.backend.scheduler.domain.SchedulerRun.Trigger;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/** Use Case 1 — scheduler pulls infra-technology evidence in mock mode. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SimulatedTechnologyCollectionTest {

    @Autowired SchedulerService scheduler;
    @Autowired SchedulerRunExecutor executor;
    @Autowired EvidenceQueryService queries;
    @Autowired OnboardingService onboarding;

    @org.junit.jupiter.api.BeforeEach
    void onboardApps() {
        onboarding.apply(false);
    }

    @Test
    void schedulerCollectsPostgresMysqlNginxTomcatIntoTheEvidenceRepository() {
        RunView started = scheduler.trigger(new RunRequest(List.of("payments"), List.of(),
                List.of("SIM_POSTGRES", "SIM_MYSQL", "SIM_NGINX", "SIM_TOMCAT"), "tester"),
                Trigger.SCHEDULED);
        executor.execute(UUID.fromString(started.runId()));

        RunView done = scheduler.get(UUID.fromString(started.runId()));
        assertThat(done.status()).isEqualTo("COMPLETED");
        assertThat(done.ingested()).isGreaterThan(0);
        assertThat(done.perSource()).containsKeys("SIM_POSTGRES", "SIM_MYSQL", "SIM_NGINX", "SIM_TOMCAT");

        // technology facet is populated and filterable
        var pg = queries.search(new EvidenceFilter("payments", null, null, null, null, null, null, 0, 100),
                new TagFacets("postgresql", null));
        assertThat(pg.totalItems()).isGreaterThan(0);
        for (EvidenceView ev : pg.items()) {
            assertThat(ev.tags()).containsEntry("technology", "postgresql")
                    .containsEntry("collectionMethod", "scheduled");
            // UC03 canonical name: {app}_{controlCode}_{evidenceType}_{yyyyMMdd}_{seq}
            assertThat(ev.title()).matches("payments_[A-Z0-9._-]+_[A-Z0-9._-]+_\\d{8}_\\d{3}");
            // UC03 metadata tags present on the live scheduler path (not just the naming unit test)
            assertThat(ev.tags()).containsKeys("frameworks", "evidenceType", "name", "control");
            assertThat(ev.tags().get("name")).isEqualTo(ev.title());
            assertThat(ev.tags().get("evidenceType")).isNotBlank();
            assertThat(ev.tags().get("frameworks")).contains(ev.tags().get("framework"));
        }

        var mysql = queries.search(new EvidenceFilter("payments", null, null, null, null, null, null, 0, 100),
                new TagFacets("mysql", null));
        assertThat(mysql.totalItems()).isGreaterThan(0);
    }

    @Test
    void reRunSameDayDeduplicates() {
        RunRequest req = new RunRequest(List.of("net-banking"), List.of(), List.of("SIM_NGINX"), "t");
        RunView first = scheduler.trigger(req, Trigger.SCHEDULED);
        executor.execute(UUID.fromString(first.runId()));
        RunView second = scheduler.trigger(req, Trigger.SCHEDULED);
        executor.execute(UUID.fromString(second.runId()));

        RunView secondDone = scheduler.get(UUID.fromString(second.runId()));
        assertThat(secondDone.ingested()).isZero();
        assertThat(secondDone.duplicates()).isGreaterThan(0);
    }
}
