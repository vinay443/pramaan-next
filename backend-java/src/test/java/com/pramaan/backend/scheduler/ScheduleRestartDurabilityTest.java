package com.pramaan.backend.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import com.pramaan.backend.BackendApplication;
import com.pramaan.backend.application.OnboardingService;
import com.pramaan.backend.integrations.SourceConnectionService;
import com.pramaan.backend.integrations.SourceConnectionService.SetConnectionRequest;
import com.pramaan.backend.scheduler.SchedulerDtos.CreateScheduleRequest;
import com.pramaan.backend.scheduler.SchedulerDtos.ScheduleView;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/** UC01-FT008: with the dev + dev-persist profiles, state survives closing and re-creating the context. */
class ScheduleRestartDurabilityTest {

    @TempDir Path dir;

    private ConfigurableApplicationContext start() {
        return new SpringApplicationBuilder(BackendApplication.class)
                .profiles("dev", "dev-persist")
                .web(WebApplicationType.NONE)
                .properties(
                        "spring.datasource.url=jdbc:h2:file:" + dir.resolve("pramaan").toString().replace(java.io.File.separatorChar, '/')
                                + ";MODE=PostgreSQL",
                        "pramaan.scheduler.jobs-tick=false",
                        "pramaan.scheduler.dispatch=false")
                .run();
    }

    @Test
    void scheduleRunHistoryAndSourceConnectionSurviveARestart() {
        ScheduleView before;
        String runId;
        try (ConfigurableApplicationContext ctx = start()) {
            ctx.getBean(OnboardingService.class).apply(false);
            before = ctx.getBean(ScheduleJobService.class).create(
                    new CreateScheduleRequest("Durable", "MOCK_GITHUB", "payments", "CODE-REVIEW", "MINUTELY", "tester"));
            runId = ctx.getBean(ScheduleJobService.class).runNow(UUID.fromString(before.id()), "tester").runId();
            ctx.getBean(SourceConnectionService.class).set("MOCK_JIRA", new SetConnectionRequest("INVALID", "tester"));
        }

        try (ConfigurableApplicationContext ctx = start()) {
            ScheduleView after = ctx.getBean(ScheduleJobService.class).list().stream()
                    .filter(s -> s.id().equals(before.id())).findFirst().orElseThrow();
            assertThat(after.active()).isTrue();
            assertThat(after.frequency()).isEqualTo("MINUTELY");
            assertThat(after.nextRunAt().truncatedTo(java.time.temporal.ChronoUnit.MILLIS))
                    .isEqualTo(before.nextRunAt().truncatedTo(java.time.temporal.ChronoUnit.MILLIS)); // DB stores micros
            assertThat(after.createdBy()).isEqualTo("tester");
            assertThat(ctx.getBean(SchedulerService.class).get(UUID.fromString(runId)).scheduleId()).isEqualTo(before.id());
            assertThat(ctx.getBean(SourceConnectionService.class).statusOf("MOCK_JIRA").name()).isEqualTo("INVALID");
        }
    }
}
