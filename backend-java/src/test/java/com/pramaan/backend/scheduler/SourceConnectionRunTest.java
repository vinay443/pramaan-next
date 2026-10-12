package com.pramaan.backend.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pramaan.backend.application.OnboardingService;
import com.pramaan.backend.evidence.EvidenceQueryService;
import com.pramaan.backend.evidence.EvidenceQueryService.EvidenceFilter;
import com.pramaan.backend.integrations.SourceConnection.Status;
import com.pramaan.backend.integrations.SourceConnectionService;
import com.pramaan.backend.integrations.SourceConnectionService.SetConnectionRequest;
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

/** UC01-FT005 (source auth failure) and FT006 (recovery on next run, history preserved). */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SourceConnectionRunTest {

    @Autowired SchedulerService scheduler;
    @Autowired SchedulerRunExecutor executor;
    @Autowired SourceConnectionService connections;
    @Autowired EvidenceQueryService queries;
    @Autowired OnboardingService onboarding;

    @BeforeEach
    void onboardApps() {
        onboarding.apply(false);
    }

    private void set(String source, String status) {
        connections.set(source, new SetConnectionRequest(status, "tester"));
    }

    private RunView run(List<String> sources) {
        RunView started = scheduler.trigger(new RunRequest(List.of("payments"), List.of(), sources, "tester"),
                Trigger.MANUAL);
        executor.execute(UUID.fromString(started.runId()));
        return scheduler.get(UUID.fromString(started.runId()));
    }

    private long evidenceFrom(String source) {
        return queries.search(new EvidenceFilter("payments", null, null, source, null, null, null, 0, 100))
                .totalItems();
    }

    @Test
    void invalidSourceFailsTheRunWithActionableErrorAndCreatesNoEvidence() {
        set("MOCK_JIRA", "INVALID");
        RunView failed = run(List.of("MOCK_JIRA"));

        assertThat(failed.status()).isEqualTo("FAILED");
        assertThat(failed.ingested()).isZero();
        assertThat(failed.sourceStatus()).containsEntry("MOCK_JIRA", "FAILED");
        assertThat(failed.sourceErrors().get("MOCK_JIRA").errorCode()).isEqualTo("AUTH_FAILED");
        assertThat(failed.sourceErrors().get("MOCK_JIRA").errorMessage())
                .startsWith("Authentication failed for MOCK_JIRA: credential invalid or expired (HTTP 401)")
                .contains("Connectivity restored automatically for the next run");
        assertThat(evidenceFrom("MOCK_JIRA")).isZero();
        assertThat(connections.statusOf("MOCK_JIRA")).isEqualTo(Status.VALID); // INVALID is transient

        set("MOCK_JIRA", "DISABLED");
        RunView disabled = run(List.of("MOCK_JIRA"));
        assertThat(disabled.status()).isEqualTo("FAILED");
        assertThat(disabled.sourceErrors().get("MOCK_JIRA").errorCode()).isEqualTo("CONNECTION_DISABLED");
        assertThat(disabled.sourceErrors().get("MOCK_JIRA").errorMessage())
                .isEqualTo("Connection for MOCK_JIRA is disabled");
        assertThat(evidenceFrom("MOCK_JIRA")).isZero();
    }

    @Test
    void mixedRunIsPartialAndOnlyTheHealthySourceIngests() {
        set("MOCK_JIRA", "INVALID");
        RunView mixed = run(List.of("MOCK_JIRA", "MOCK_GITHUB"));

        assertThat(mixed.status()).isEqualTo("PARTIAL");
        assertThat(mixed.sourceStatus()).containsEntry("MOCK_JIRA", "FAILED").containsEntry("MOCK_GITHUB", "CONNECTED");
        assertThat(mixed.ingested()).isGreaterThan(0);
        assertThat(evidenceFrom("MOCK_JIRA")).isZero();
        assertThat(evidenceFrom("MOCK_GITHUB")).isGreaterThan(0);
    }

    @Test
    void restoringTheConnectionRecoversOnTheNextRunAndBothRunsRemainInHistory() {
        set("MOCK_JIRA", "INVALID");
        RunView failed = run(List.of("MOCK_JIRA"));
        assertThat(failed.status()).isEqualTo("FAILED");
        assertThat(failed.sourceErrors().get("MOCK_JIRA").errorCode()).isEqualTo("AUTH_FAILED");
        assertThat(evidenceFrom("MOCK_JIRA")).isZero(); // no false-success evidence

        // no manual set(VALID): connectivity is restored automatically after the failure was recorded
        RunView recovered = run(List.of("MOCK_JIRA"));

        assertThat(recovered.status()).isEqualTo("COMPLETED");
        assertThat(recovered.ingested()).isGreaterThan(0);
        assertThat(evidenceFrom("MOCK_JIRA")).isEqualTo(recovered.ingested());

        assertThat(scheduler.get(UUID.fromString(failed.runId())).status()).isEqualTo("FAILED");
        assertThat(scheduler.get(UUID.fromString(recovered.runId())).status()).isEqualTo("COMPLETED");
        assertThat(scheduler.list(0, 20).items()).extracting(RunView::runId)
                .contains(failed.runId(), recovered.runId());
    }

    @Test
    void retryOfAFailedRunStartsALinkedRunThatSucceedsOnceValid() {
        set("MOCK_JIRA", "INVALID");
        RunView failed = run(List.of("MOCK_JIRA"));
        assertThat(failed.retryOfRunId()).isNull();

        RunView retry = scheduler.retry(UUID.fromString(failed.runId()));
        assertThat(retry.runId()).isNotEqualTo(failed.runId());
        assertThat(retry.retryOfRunId()).isEqualTo(failed.runId());
        assertThat(retry.sources()).containsExactly("MOCK_JIRA");

        executor.execute(UUID.fromString(retry.runId()));
        assertThat(scheduler.get(UUID.fromString(retry.runId())).status()).isEqualTo("COMPLETED");
    }

    @Test
    void disabledStaysManualAndKeepsFailingUntilReEnabled() {
        set("MOCK_JIRA", "DISABLED");
        assertThat(run(List.of("MOCK_JIRA")).status()).isEqualTo("FAILED");
        RunView again = run(List.of("MOCK_JIRA"));
        assertThat(again.status()).isEqualTo("FAILED");
        assertThat(again.sourceErrors().get("MOCK_JIRA").errorCode()).isEqualTo("CONNECTION_DISABLED");
        assertThat(connections.statusOf("MOCK_JIRA")).isEqualTo(Status.DISABLED);

        set("MOCK_JIRA", "VALID");
        assertThat(run(List.of("MOCK_JIRA")).status()).isEqualTo("COMPLETED");
    }

    @Test
    void aRunThatNeverPollsTheInvalidSourceLeavesItInvalid() {
        set("MOCK_JIRA", "INVALID");
        RunView other = run(List.of("MOCK_GITHUB"));

        assertThat(other.status()).isEqualTo("COMPLETED");
        assertThat(connections.statusOf("MOCK_JIRA")).isEqualTo(Status.INVALID);
        assertThat(run(List.of("MOCK_JIRA")).status()).isEqualTo("FAILED"); // first poll still fails once
        assertThat(connections.statusOf("MOCK_JIRA")).isEqualTo(Status.VALID);
    }

    @Test
    void connectionUpdatesValidateStatusAndSource() {
        assertThatThrownBy(() -> set("MOCK_JIRA", "BROKEN")).hasMessageContaining("status must be one of");
        assertThatThrownBy(() -> set("NOPE", "VALID")).hasMessageContaining("unknown source");
        assertThat(connections.list()).anySatisfy(c -> {
            assertThat(c.sourceSystem()).isEqualTo("MOCK_JIRA");
            assertThat(c.status()).isEqualTo("VALID");
        });
    }

    @Test
    void failedSourceShowsErrorCodeAndMessageInThePerSourceAuditList() {
        set("MOCK_JIRA", "INVALID");
        RunView mixed = run(List.of("MOCK_JIRA", "MOCK_GITHUB"));

        assertThat(mixed.sourceResults()).hasSize(2);
        var jira = mixed.sourceResults().stream().filter(r -> r.source().equals("MOCK_JIRA")).findFirst().orElseThrow();
        assertThat(jira.status()).isEqualTo("FAILED");
        assertThat(jira.evidenceCount()).isZero();
        assertThat(jira.errorCode()).isEqualTo("AUTH_FAILED");
        assertThat(jira.errorMessage()).startsWith("Authentication failed for MOCK_JIRA");
        var github = mixed.sourceResults().stream().filter(r -> r.source().equals("MOCK_GITHUB")).findFirst().orElseThrow();
        assertThat(github.status()).isEqualTo("CONNECTED");
        assertThat(github.evidenceCount()).isGreaterThan(0);
        assertThat(mixed.triggeredBy()).isEqualTo("anonymous");
        assertThat(mixed.durationMs()).isNotNull();
    }
}
