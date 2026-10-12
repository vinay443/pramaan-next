package com.pramaan.backend.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;

import com.pramaan.backend.application.OnboardingService;
import com.pramaan.backend.integrations.SourceConnectionService;
import com.pramaan.backend.scheduler.SchedulerDtos.CreateScheduleRequest;
import com.pramaan.backend.scheduler.SchedulerDtos.RunRequest;
import com.pramaan.backend.scheduler.SchedulerDtos.RunView;
import com.pramaan.backend.scheduler.SchedulerDtos.ScheduleView;
import com.pramaan.backend.scheduler.domain.SchedulerRun.Trigger;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/** UC01-FT006 — a run that dies mid-way must not stay RUNNING and block the schedule's next slot. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class StuckRunRecoveryTest {

    @Autowired SchedulerService scheduler;
    @Autowired SchedulerRunExecutor executor;
    @Autowired ScheduleJobService jobs;
    @Autowired OnboardingService onboarding;
    @SpyBean SourceConnectionService connections;

    @BeforeEach
    void onboardApps() {
        onboarding.apply(false);
    }

    private ScheduleView schedule() {
        return jobs.create(new CreateScheduleRequest("Stuck?", "MOCK_JIRA", "payments", "CHANGE-TICKET",
                "HOURLY", "tester"));
    }

    private RunView find(String runId) {
        return scheduler.get(UUID.fromString(runId));
    }

    @Test
    void anExecutorExceptionEndsTheRunFailedAndTheNextTickStartsANewRun() {
        ScheduleView s = schedule();
        Instant firstTick = s.nextRunAt().plusSeconds(1);
        assertThat(jobs.runDue(firstTick)).isEqualTo(1);
        RunView first = scheduler.list(0, 20).items().get(0);
        assertThat(first.status()).isEqualTo("PENDING"); // test profile does not dispatch

        // the executor blows up after the run was marked RUNNING
        doThrow(new IllegalStateException("db connection lost")).when(connections).statusOf(anyString());
        executor.executeGuarded(UUID.fromString(first.runId()));
        doCallRealMethod().when(connections).statusOf(anyString());

        RunView failed = find(first.runId());
        assertThat(failed.status()).isEqualTo("FAILED");
        assertThat(failed.message()).contains("run aborted").contains("db connection lost");
        assertThat(failed.finishedAt()).isNotNull();

        // next due tick is not blocked by the dead run
        assertThat(jobs.runDue(firstTick.plus(Duration.ofHours(1)).plusSeconds(1))).isEqualTo(1);
        List<RunView> history = scheduler.list(0, 20).items();
        assertThat(history).hasSize(2);
        assertThat(history).extracting(RunView::runId).contains(first.runId());
        assertThat(history).extracting(RunView::status).containsExactlyInAnyOrder("FAILED", "PENDING");
    }

    @Test
    void aTickSkippedBecauseARunIsStillActiveStaysDueAndFiresOnceTheRunFinishes() {
        ScheduleView s = schedule();
        Instant now = Instant.now();
        RunView manual = jobs.runNow(UUID.fromString(s.id()), "tester"); // PENDING, linked to the schedule
        Instant due = s.nextRunAt().plusSeconds(1);

        assertThat(jobs.runDue(due)).isZero(); // skipped: run in progress
        assertThat(jobs.list().get(0).nextRunAt()).isBeforeOrEqualTo(due); // slot not lost

        executor.execute(UUID.fromString(manual.runId())); // finishes
        assertThat(jobs.runDue(due.plusSeconds(30))).isEqualTo(1); // retried on the next tick
        assertThat(now).isNotNull();
    }

    @Test
    void runsLeftPendingOrRunningByARestartAreFailedAtStartup() {
        RunView stuck = scheduler.trigger(new RunRequest(List.of("payments"), List.of(), List.of("MOCK_JIRA"), "t"),
                Trigger.MANUAL);
        assertThat(stuck.status()).isEqualTo("PENDING");

        jobs.failRunsOrphanedByRestart();

        RunView after = find(stuck.runId());
        assertThat(after.status()).isEqualTo("FAILED");
        assertThat(after.message()).contains("restarted");
        assertThat(scheduler.list(0, 20).items()).extracting(RunView::runId).contains(stuck.runId());
    }
}
