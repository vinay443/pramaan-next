package com.pramaan.backend.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import com.pramaan.backend.application.OnboardingService;
import com.pramaan.backend.evidence.EvidenceDtos.LifecycleEventView;
import com.pramaan.backend.evidence.EvidenceLifecycleService;
import com.pramaan.backend.evidence.domain.EvidenceRecord;
import com.pramaan.backend.evidence.domain.EvidenceVersion;
import com.pramaan.backend.evidence.repo.EvidenceRecordRepository;
import com.pramaan.backend.scheduler.SchedulerDtos.CreateScheduleRequest;
import com.pramaan.backend.scheduler.SchedulerDtos.RunRequest;
import com.pramaan.backend.scheduler.SchedulerDtos.RunView;
import com.pramaan.backend.scheduler.domain.SchedulerRun.Trigger;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/** UC01-FT003 (persisted evidence) and FT004 (unchanged re-collection is a duplicate + one audit event). */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DuplicateIngestionTest {

    @Autowired SchedulerService scheduler;
    @Autowired SchedulerRunExecutor executor;
    @Autowired ScheduleJobService jobs;
    @Autowired EvidenceRecordRepository records;
    @Autowired EvidenceLifecycleService lifecycle;
    @Autowired OnboardingService onboarding;

    @BeforeEach
    void onboardApps() {
        onboarding.apply(false);
    }

    private RunView run(RunView started) {
        executor.execute(UUID.fromString(started.runId()));
        return scheduler.get(UUID.fromString(started.runId()));
    }

    @Test
    void rerunOfUnchangedSourceDoesNotCreateVersionsAndAuditsOneDuplicateSkippedPerEvidence() {
        RunView first = run(scheduler.trigger(new RunRequest(List.of("payments"), List.of(),
                List.of("MOCK_JIRA"), "tester"), Trigger.MANUAL));
        assertThat(first.ingested()).isGreaterThan(0);

        List<EvidenceRecord> after1 = records.findByApplicationSlug("payments");
        // FT003: persisted object, SHA-256, source, application, collection timestamp
        assertThat(after1).isNotEmpty().allSatisfy(r -> {
            EvidenceVersion v = r.latestVersion();
            assertThat(v.getObjectKey()).isNotBlank();
            assertThat(v.getSha256()).hasSize(64);
            assertThat(v.getCollectedAt()).isNotNull();
            assertThat(r.getSourceSystem()).isEqualTo("MOCK_JIRA");
            assertThat(r.getApplicationSlug()).isEqualTo("payments");
        });
        Map<UUID, String> shaBefore = after1.stream()
                .collect(Collectors.toMap(EvidenceRecord::getId, r -> r.latestVersion().getSha256()));
        Map<UUID, String> objectBefore = after1.stream()
                .collect(Collectors.toMap(EvidenceRecord::getId, r -> r.latestVersion().getObjectKey()));

        // Second pull of the same unchanged items — via a schedule with its own evidence-type override.
        String scheduleName = "Payments Jira";
        jobs.create(new CreateScheduleRequest(scheduleName, "MOCK_JIRA", "payments", "TLS-SCAN", "DAILY", "tester"));
        RunView startedSecond = jobs.runNow(UUID.fromString(jobs.list().get(0).id()), "tester");
        RunView second = run(startedSecond);

        assertThat(second.ingested()).isZero();
        assertThat(second.duplicates()).isEqualTo(first.ingested());

        List<EvidenceRecord> after2 = records.findByApplicationSlug("payments");
        assertThat(after2).hasSameSizeAs(after1);
        for (EvidenceRecord r : after2) {
            assertThat(r.getVersions()).hasSize(1);
            assertThat(r.latestVersion().getSha256()).isEqualTo(shaBefore.get(r.getId()));
            assertThat(r.latestVersion().getObjectKey()).isEqualTo(objectBefore.get(r.getId()));

            List<LifecycleEventView> skipped = lifecycle.get(r.getId()).history().stream()
                    .filter(e -> "DUPLICATE_SKIPPED".equals(e.action())).toList();
            assertThat(skipped).hasSize(1);
            assertThat(skipped.get(0).note()).isEqualTo("unchanged in run " + second.runId() + " (SCHEDULED)");
            assertThat(skipped.get(0).actor()).isEqualTo(scheduleName);
        }

        // Re-executing the same run must not flood the trail.
        executor.execute(UUID.fromString(second.runId()));
        for (EvidenceRecord r : records.findByApplicationSlug("payments")) {
            assertThat(lifecycle.get(r.getId()).history().stream()
                    .filter(e -> "DUPLICATE_SKIPPED".equals(e.action()))).hasSize(1);
        }
    }
}
