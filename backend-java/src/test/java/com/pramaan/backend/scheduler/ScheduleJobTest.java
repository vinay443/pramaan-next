package com.pramaan.backend.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pramaan.backend.application.OnboardingService;
import com.pramaan.backend.scheduler.SchedulerDtos.RunView;
import com.pramaan.backend.evidence.EvidenceQueryService;
import com.pramaan.backend.evidence.EvidenceQueryService.EvidenceFilter;
import com.pramaan.backend.scheduler.domain.SchedulerRun.Trigger;
import java.util.List;
import java.util.UUID;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ScheduleJobTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired OnboardingService onboarding;
    @Autowired ScheduleJobService jobs;
    @Autowired SchedulerService scheduler;
    @Autowired SchedulerRunExecutor executor;
    @Autowired EvidenceQueryService queries;

    @BeforeEach
    void onboardApps() {
        onboarding.apply(false);
    }

    private static String body(String name, String source, String app, String type, String freq) {
        return """
                {"name":%s,"sourceSystem":%s,"application":%s,"evidenceType":%s,"frequency":%s}"""
                .formatted(q(name), q(source), q(app), q(type), q(freq));
    }

    private static String q(String v) {
        return v == null ? "null" : "\"" + v + "\"";
    }

    private JsonNode create(String body) throws Exception {
        return json.readTree(mvc.perform(post("/api/v1/schedules").header("X-Pramaan-Role", "APP").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    @Test
    void createValidatesInputAndComputesNextRunFromFrequency() throws Exception {
        Instant before = Instant.now();
        JsonNode created = create(body("Nightly Jira", "MOCK_JIRA", "payments", "CHANGE-TICKET", "DAILY"));

        assertThat(created.get("active").asBoolean()).isTrue();
        assertThat(created.get("frequency").asText()).isEqualTo("DAILY");
        assertThat(Instant.parse(created.get("nextRunAt").asText()))
                .isBetween(before.plus(Duration.ofHours(23)), Instant.now().plus(Duration.ofHours(25)));
        assertThat(created.get("lastRunAt").isNull()).isTrue();

        mvc.perform(get("/api/v1/schedules").header("X-Pramaan-Role", "APP")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Nightly Jira"));
        mvc.perform(get("/api/v1/schedules/options").header("X-Pramaan-Role", "APP")).andExpect(status().isOk())
                .andExpect(jsonPath("$.sources").isNotEmpty())
                .andExpect(jsonPath("$.frequencies[0]").value("HOURLY"));

        // validation -> 400 with a clear message
        mvc.perform(post("/api/v1/schedules").header("X-Pramaan-Role", "APP").contentType(MediaType.APPLICATION_JSON)
                        .content(body("", "MOCK_JIRA", "payments", "CHANGE-TICKET", "DAILY")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("name is required"));
        mvc.perform(post("/api/v1/schedules").header("X-Pramaan-Role", "APP").contentType(MediaType.APPLICATION_JSON)
                        .content(body("x", "MOCK_JIRA", "payments", "CHANGE-TICKET", "MONTHLY")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("frequency must be one of [HOURLY, DAILY, WEEKLY, MINUTELY]"));
        mvc.perform(post("/api/v1/schedules").header("X-Pramaan-Role", "APP").contentType(MediaType.APPLICATION_JSON)
                        .content(body("x", null, "payments", "CHANGE-TICKET", "DAILY")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("sourceSystem is required"));

        // toggle: deactivate clears nextRunAt, reactivate recomputes it
        String id = created.get("id").asText();
        mvc.perform(patch("/api/v1/schedules/" + id).header("X-Pramaan-Role", "APP").contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}"))
                .andExpect(jsonPath("$.active").value(false)).andExpect(jsonPath("$.nextRunAt").doesNotExist());
        mvc.perform(patch("/api/v1/schedules/" + id).header("X-Pramaan-Role", "APP").contentType(MediaType.APPLICATION_JSON).content("{\"active\":true}"))
                .andExpect(jsonPath("$.active").value(true)).andExpect(jsonPath("$.nextRunAt").exists());
    }

    @Test
    void tickTriggersScheduledRunForDueJobAndAdvancesSchedule() throws Exception {
        JsonNode created = create(body("Hourly GitHub", "MOCK_GITHUB", "payments", "CODE-REVIEW", "HOURLY"));
        Instant firstDue = Instant.parse(created.get("nextRunAt").asText());

        assertThat(jobs.runDue(Instant.now())).isZero(); // not due yet
        assertThat(scheduler.list(0, 20).items()).isEmpty();

        Instant tickAt = firstDue.plusSeconds(1);
        assertThat(jobs.runDue(tickAt)).isEqualTo(1);

        RunView run = scheduler.list(0, 20).items().get(0);
        assertThat(run.trigger()).isEqualTo("SCHEDULED");
        assertThat(run.applications()).containsExactly("payments");
        assertThat(run.sources()).containsExactly("MOCK_GITHUB");

        var job = jobs.list().get(0);
        assertThat(job.lastRunAt()).isEqualTo(tickAt);
        assertThat(job.nextRunAt()).isEqualTo(tickAt.plus(Duration.ofHours(1)));
    }

    @Test
    void runNowStartsScheduledRunRestrictedToTheSchedulesSourceAndApplication() throws Exception {
        JsonNode created = create(body("Payments TLS", "MOCK_GITHUB", "payments", "TLS-SCAN", "DAILY"));
        String id = created.get("id").asText();

        JsonNode started = json.readTree(mvc.perform(post("/api/v1/schedules/" + id + "/run-now").header("X-Pramaan-Role", "APP"))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString());
        assertThat(started.get("trigger").asText()).isEqualTo("SCHEDULED");
        assertThat(started.get("scheduleId").asText()).isEqualTo(id);
        assertThat(started.get("scheduleName").asText()).isEqualTo("Payments TLS");

        executor.execute(UUID.fromString(started.get("runId").asText()));
        RunView done = scheduler.get(UUID.fromString(started.get("runId").asText()));
        assertThat(done.status()).isEqualTo("COMPLETED");
        assertThat(done.applications()).containsExactly("payments");
        assertThat(done.sources()).containsExactly("MOCK_GITHUB");
        assertThat(done.perSource()).containsOnlyKeys("MOCK_GITHUB");
        assertThat(done.sourceStatus()).containsEntry("MOCK_GITHUB", "CONNECTED");
        assertThat(done.ingested()).isGreaterThan(0);

        var evidence = queries.search(new EvidenceFilter("payments", null, null, "MOCK_GITHUB",
                null, null, null, 0, 50)).items();
        assertThat(evidence).isNotEmpty().allSatisfy(e -> assertThat(e.tags()).containsEntry("evidenceType", "TLS-SCAN"));

        // run-now records lastRunAt but leaves the schedule's nextRunAt alone
        var job = jobs.list().get(0);
        assertThat(job.lastRunAt()).isNotNull();
        assertThat(job.nextRunAt()).isEqualTo(Instant.parse(created.get("nextRunAt").asText()));

        mvc.perform(post("/api/v1/schedules/" + UUID.randomUUID() + "/run-now").header("X-Pramaan-Role", "APP")).andExpect(status().isNotFound());
    }

    @Test
    void manualRunIsUnchangedAndReportsPerSourceConnectivity() {
        RunView started = scheduler.trigger(new SchedulerDtos.RunRequest(List.of("payments"), List.of(),
                List.of("MOCK_JIRA", "NOPE"), "tester"), Trigger.MANUAL);
        assertThat(started.scheduleId()).isNull();
        assertThat(started.scheduleName()).isNull();

        executor.execute(UUID.fromString(started.runId()));
        RunView done = scheduler.get(UUID.fromString(started.runId()));
        assertThat(done.trigger()).isEqualTo("MANUAL");
        assertThat(done.status()).isEqualTo("PARTIAL");
        assertThat(done.perSource().get("NOPE")).startsWith("error:");
        assertThat(done.sourceStatus()).containsEntry("MOCK_JIRA", "CONNECTED").containsEntry("NOPE", "FAILED");
        var jira = queries.search(new EvidenceFilter("payments", null, null, "MOCK_JIRA",
                null, null, null, 0, 50)).items();
        assertThat(jira).isNotEmpty().allSatisfy(e -> assertThat(e.tags()).containsEntry("evidenceType", "CHANGE-TICKET"));
    }

    @Test
    void runNowRecordsWhoTriggeredItAndTheScheduleAuditFields() throws Exception {
        JsonNode created = create(body("Audit job", "MOCK_GITHUB", "payments", "CODE-REVIEW", "WEEKLY"));
        String id = created.get("id").asText();

        JsonNode started = json.readTree(mvc.perform(post("/api/v1/schedules/" + id + "/run-now").header("X-Pramaan-Role", "APP")
                        .header("X-Pramaan-User", "App Owner"))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString());
        assertThat(started.get("triggeredBy").asText()).isEqualTo("App Owner");

        executor.execute(UUID.fromString(started.get("runId").asText()));
        RunView done = scheduler.get(UUID.fromString(started.get("runId").asText()));
        assertThat(done.triggeredBy()).isEqualTo("App Owner");
        assertThat(done.scheduleName()).isEqualTo("Audit job");
        assertThat(done.scheduleFrequency()).isEqualTo("WEEKLY");
        assertThat(done.scheduleCreatedBy()).isEqualTo("ui");
        assertThat(done.startedAt()).isNotNull().isBeforeOrEqualTo(done.finishedAt());
        assertThat(done.durationMs()).isNotNull().isGreaterThanOrEqualTo(0);
        assertThat(done.sourceResults()).singleElement().satisfies(r -> {
            assertThat(r.source()).isEqualTo("MOCK_GITHUB");
            assertThat(r.status()).isEqualTo("CONNECTED");
            assertThat(r.evidenceCount()).isEqualTo(done.ingested());
            assertThat(r.errorCode()).isNull();
        });

        // no header -> anonymous
        JsonNode anon = json.readTree(mvc.perform(post("/api/v1/schedules/" + id + "/run-now").header("X-Pramaan-Role", "APP"))
                .andReturn().getResponse().getContentAsString());
        assertThat(anon.get("triggeredBy").asText()).isEqualTo("anonymous");
    }

    @Test
    void tickInitiatedRunIsAttributedToTheScheduler() throws Exception {
        JsonNode created = create(body("Tick job", "MOCK_GITHUB", "payments", "CODE-REVIEW", "HOURLY"));
        jobs.runDue(Instant.parse(created.get("nextRunAt").asText()).plusSeconds(1));
        RunView run = scheduler.list(0, 20).items().get(0);
        assertThat(run.trigger()).isEqualTo("SCHEDULED");
        assertThat(run.triggeredBy()).isEqualTo("system:scheduler");
        assertThat(run.scheduleName()).isEqualTo("Tick job");
    }

    @Test
    void overdueJobFiresExactlyOnceOnTheFirstTickAfterRestartAndMovesToTheFuture() throws Exception {
        JsonNode created = create(body("Missed", "MOCK_GITHUB", "payments", "CODE-REVIEW", "HOURLY"));
        // backend was "down" for 3 days: nextRunAt is far in the past relative to the first tick
        Instant firstTick = Instant.parse(created.get("nextRunAt").asText()).plus(Duration.ofDays(3));

        assertThat(jobs.runDue(firstTick)).isEqualTo(1);
        assertThat(jobs.runDue(firstTick.plusSeconds(30))).isZero(); // no catch-up flood
        assertThat(scheduler.list(0, 20).items()).hasSize(1);
        assertThat(jobs.list().get(0).nextRunAt()).isEqualTo(firstTick.plus(Duration.ofHours(1)));
    }

    @Test
    void minutelyScheduleIsDueAboutOneMinuteAhead() throws Exception {
        Instant before = Instant.now();
        JsonNode created = create(body("Every minute", "MOCK_GITHUB", "payments", "CODE-REVIEW", "MINUTELY"));
        assertThat(Instant.parse(created.get("nextRunAt").asText()))
                .isBetween(before.plusSeconds(59), Instant.now().plusSeconds(61));
        mvc.perform(get("/api/v1/system/status").header("X-Pramaan-Role", "APP")).andExpect(status().isOk())
                .andExpect(jsonPath("$.activeSchedules").value(1))
                .andExpect(jsonPath("$.serverStartedAt").exists());
    }
}
