package com.pramaan.backend.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pramaan.backend.application.ApplicationService;
import com.pramaan.backend.application.OnboardingService;
import com.pramaan.backend.evidence.EvidenceQueryService;
import com.pramaan.backend.evidence.EvidenceQueryService.EvidenceFilter;
import com.pramaan.backend.scheduler.SchedulerDtos.RunRequest;
import com.pramaan.backend.scheduler.SchedulerDtos.RunView;
import com.pramaan.backend.scheduler.domain.SchedulerRun.Trigger;
import java.util.List;
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
class SchedulerRunExecutorTest {

    @Autowired SchedulerService scheduler;
    @Autowired SchedulerRunExecutor executor;
    @Autowired EvidenceQueryService queries;
    @Autowired OnboardingService onboarding;
    @Autowired ApplicationService applications;

    @BeforeEach
    void onboardApps() {
        onboarding.apply(false); // scheduler now requires at least one onboarded application
    }

    private RunRequest oneApp() {
        return new RunRequest(List.of("payments"), List.of(), List.of("MOCK_JIRA", "MOCK_GITHUB"), "tester");
    }

    @Test
    void runCollectsFromMockIntegrationsThenDeduplicatesOnReRun() {
        RunView started = scheduler.trigger(oneApp(), Trigger.MANUAL);
        executor.execute(UUID.fromString(started.runId()));

        RunView done = scheduler.get(UUID.fromString(started.runId()));
        assertThat(done.status()).isEqualTo("COMPLETED");
        assertThat(done.received()).isGreaterThan(0);
        assertThat(done.ingested()).isGreaterThan(0);
        assertThat(done.perSource()).containsKey("MOCK_JIRA");

        long evidenceCount = queries.search(new EvidenceFilter("payments", null, null, null,
                null, null, null, 0, 100)).totalItems();
        assertThat(evidenceCount).isEqualTo(done.ingested());

        // Second run, same day -> identical synthetic content -> all duplicates, no new versions.
        RunView rerun = scheduler.trigger(oneApp(), Trigger.MANUAL);
        executor.execute(UUID.fromString(rerun.runId()));
        RunView rerunDone = scheduler.get(UUID.fromString(rerun.runId()));
        assertThat(rerunDone.ingested()).isZero();
        assertThat(rerunDone.duplicates()).isEqualTo(done.ingested());
    }

    @Test
    void schedulerCollectsSharePointAndServiceNowEvidenceThroughTheCanonicalPath() {
        RunView started = scheduler.trigger(new RunRequest(List.of("net-banking"), List.of(),
                List.of("SHAREPOINT", "SERVICENOW"), "tester"), Trigger.MANUAL);
        executor.execute(UUID.fromString(started.runId()));

        RunView done = scheduler.get(UUID.fromString(started.runId()));
        assertThat(done.status()).isEqualTo("COMPLETED");
        assertThat(done.perSource()).containsKeys("SHAREPOINT", "SERVICENOW");
        assertThat(done.ingested()).isGreaterThan(0);

        long sp = queries.search(new EvidenceFilter("net-banking", null, null, "SHAREPOINT",
                null, null, null, 0, 50)).totalItems();
        long sn = queries.search(new EvidenceFilter("net-banking", null, null, "SERVICENOW",
                null, null, null, 0, 50)).totalItems();
        assertThat(sp).isGreaterThan(0);
        assertThat(sn).isGreaterThan(0);
    }

    @Test
    void explicitlyNamingADeboardedApplicationIsRejectedAndIngestsNothing() {
        applications.setActive("payments", false); // deboard; net-banking + mobile-banking still onboarded

        assertThatThrownBy(() -> scheduler.trigger(oneApp(), Trigger.MANUAL))
                .hasMessageContaining("Application 'payments' is not onboarded");

        long paymentsEvidence = queries.search(new EvidenceFilter("payments", null, null, null,
                null, null, null, 0, 100)).totalItems();
        assertThat(paymentsEvidence).isZero();
    }

    @Test
    void unknownSourceIsRecordedNotThrown() {
        RunView started = scheduler.trigger(
                new RunRequest(List.of("payments"), List.of(), List.of("NOPE"), "tester"), Trigger.MANUAL);
        executor.execute(UUID.fromString(started.runId()));
        RunView done = scheduler.get(UUID.fromString(started.runId()));
        assertThat(done.status()).isEqualTo("FAILED");
        assertThat(done.perSource().get("NOPE")).startsWith("error:");
    }
}
