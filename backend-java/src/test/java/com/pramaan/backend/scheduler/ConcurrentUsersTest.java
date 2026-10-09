package com.pramaan.backend.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pramaan.backend.application.OnboardingService;
import com.pramaan.backend.notification.NotificationService;
import com.pramaan.backend.notification.NotificationService.NotificationView;
import com.pramaan.backend.observation.ObservationService;
import com.pramaan.backend.evidence.repo.EvidenceRecordRepository;
import com.pramaan.backend.scheduler.SchedulerDtos.RunRequest;
import com.pramaan.backend.scheduler.SchedulerDtos.RunView;
import com.pramaan.backend.scheduler.domain.SchedulerRun.Trigger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * UC01-FT015 - simultaneous users. Real threads against a private database (not @Transactional, so every
 * thread sees committed data). Runs are not dispatched (test profile), so a run stays PENDING until the test
 * executes it - that makes "a run is in progress" deterministic.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:concurrent;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ConcurrentUsersTest {

    private static final String ROLE = "X-Pramaan-Role";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired OnboardingService onboarding;
    @Autowired ScheduleJobService jobs;
    @Autowired SchedulerService scheduler;
    @Autowired SchedulerRunExecutor executor;
    @Autowired NotificationService notifications;
    @Autowired ObservationService observationService;
    @Autowired EvidenceRecordRepository records;

    @BeforeEach
    void onboardApps() {
        onboarding.apply(false);
    }

    private String createSchedule(String name) throws Exception {
        String body = "{\"name\":\"" + name + "\",\"sourceSystem\":\"MOCK_GITHUB\",\"application\":\"payments\","
                + "\"evidenceType\":\"CODE-REVIEW\",\"frequency\":\"DAILY\"}";
        return json.readTree(mvc.perform(post("/api/v1/schedules").header(ROLE, "APP")
                        .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse()
                .getContentAsString()).get("id").asText();
    }

    /** Releases all callables at once and returns their results. */
    private <T> List<T> simultaneously(List<Callable<T>> calls) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(calls.size());
        CountDownLatch go = new CountDownLatch(1);
        List<Future<T>> futures = new ArrayList<>();
        for (Callable<T> c : calls) {
            futures.add(pool.submit(() -> {
                go.await();
                return c.call();
            }));
        }
        go.countDown();
        List<T> out = new ArrayList<>();
        for (Future<T> f : futures) out.add(f.get());
        pool.shutdown();
        return out;
    }

    private MvcResult runNow(String id, String role) throws Exception {
        return mvc.perform(post("/api/v1/schedules/" + id + "/run-now").header(ROLE, role)
                .header("X-Pramaan-User", role)).andReturn();
    }

    @Test
    void twoUsersRunningTheSameScheduleAtOnceStartExactlyOneRunPerRound() throws Exception {
        String id = createSchedule("Contended");
        for (int round = 1; round <= 10; round++) {
            List<MvcResult> results = simultaneously(List.<Callable<MvcResult>>of(
                    () -> runNow(id, "APP"), () -> runNow(id, "ADMIN")));
            List<MvcResult> started = results.stream().filter(r -> r.getResponse().getStatus() == 202).toList();
            List<MvcResult> rejected = results.stream().filter(r -> r.getResponse().getStatus() == 409).toList();
            assertThat(results).allSatisfy(r -> assertThat(r.getResponse().getStatus()).isLessThan(500));
            assertThat(started).as("round %d started", round).hasSize(1);
            assertThat(rejected).as("round %d rejected", round).hasSize(1);

            String runId = json.readTree(started.get(0).getResponse().getContentAsString()).get("runId").asText();
            JsonNode conflict = json.readTree(rejected.get(0).getResponse().getContentAsString());
            assertThat(conflict.get("error").asText()).isEqualTo("RUN_IN_PROGRESS");
            assertThat(conflict.get("runId").asText()).isEqualTo(runId);
            assertThat(conflict.get("message").asText()).isEqualTo("Schedule Contended already has a run in progress");

            executor.execute(UUID.fromString(runId)); // finish it so the next round may start a new one
            RunView done = scheduler.get(UUID.fromString(runId));
            assertThat(done.status()).isEqualTo("COMPLETED");
            assertThat(done.received()).isEqualTo(done.ingested() + done.duplicates() + done.failed());
        }
        assertThat(scheduler.list(0, 200).items().stream().filter(r -> "Contended".equals(r.scheduleName()))).hasSize(10);
    }

    @Test
    void theTickAndRunNowAtTheSameTimeStillStartOnlyOneRun() throws Exception {
        String id = createSchedule("Tick vs now");
        Instant farFuture = Instant.now().plusSeconds(3 * 86_400);
        simultaneously(List.<Callable<Object>>of(() -> jobs.runDue(farFuture), () -> runNow(id, "APP")));

        assertThat(scheduler.list(0, 200).items().stream().filter(r -> "Tick vs now".equals(r.scheduleName())))
                .as("one active run, never two").hasSize(1);
    }

    @Test
    void concurrentTogglesAndSettingsSavesNeverFailAndAStaleVersionIsAConflict() throws Exception {
        String id = createSchedule("Toggled");
        // two users deactivate the same schedule together, then both activate it: all succeed
        for (boolean active : new boolean[] {false, true}) {
            List<Integer> codes = simultaneously(List.<Callable<Integer>>of(
                    () -> mvc.perform(patch("/api/v1/schedules/" + id).header(ROLE, "APP").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"active\":" + active + "}")).andReturn().getResponse().getStatus(),
                    () -> mvc.perform(patch("/api/v1/schedules/" + id).header(ROLE, "ADMIN").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"active\":" + active + "}")).andReturn().getResponse().getStatus()));
            assertThat(codes).containsOnly(200);
        }
        assertThat(jobs.list().stream().filter(s -> s.id().equals(id)).findFirst().orElseThrow().active()).isTrue();

        // stale update: a client holding an old version loses
        long stale = jobs.list().stream().filter(s -> s.id().equals(id)).findFirst().orElseThrow().version();
        mvc.perform(patch("/api/v1/schedules/" + id).header(ROLE, "APP").contentType(MediaType.APPLICATION_JSON)
                .content("{\"active\":false}")).andReturn();
        MvcResult conflict = mvc.perform(patch("/api/v1/schedules/" + id).header(ROLE, "ADMIN").contentType(MediaType.APPLICATION_JSON)
                .content("{\"active\":true,\"version\":" + stale + "}")).andReturn();
        assertThat(conflict.getResponse().getStatus()).isEqualTo(409);
        JsonNode body = json.readTree(conflict.getResponse().getContentAsString());
        assertThat(body.get("error").asText()).isEqualTo("CONFLICT");
        assertThat(body.get("message").asText()).isEqualTo("Changed by another user, reload and retry");

        // notification settings saved by two users at once
        String a = "{\"enabled\":true,\"notifyOnSuccess\":true,\"notifyOnFailure\":true,\"recipientRoles\":[\"APP\"],\"emailSimulated\":true}";
        String b = "{\"enabled\":true,\"notifyOnSuccess\":false,\"notifyOnFailure\":true,\"recipientRoles\":[\"APP\",\"ADMIN\"],\"emailSimulated\":false}";
        List<Integer> codes = simultaneously(List.<Callable<Integer>>of(
                () -> mvc.perform(put("/api/v1/notifications/settings").header(ROLE, "APP").contentType(MediaType.APPLICATION_JSON)
                        .content(a)).andReturn().getResponse().getStatus(),
                () -> mvc.perform(put("/api/v1/notifications/settings").header(ROLE, "ADMIN").contentType(MediaType.APPLICATION_JSON)
                        .content(b)).andReturn().getResponse().getStatus()));
        assertThat(codes).containsOnly(200);
        var finalSettings = notifications.getSettings();
        assertThat(finalSettings.recipientRoles()).isIn(List.of("APP"), List.of("APP", "ADMIN")); // one whole save, never a mix
        assertThat(finalSettings.notifyOnSuccess()).isEqualTo(finalSettings.recipientRoles().size() == 1);

        long v = finalSettings.version();
        mvc.perform(put("/api/v1/notifications/settings").header(ROLE, "APP").contentType(MediaType.APPLICATION_JSON)
                .content(a)).andReturn();
        MvcResult staleSave = mvc.perform(put("/api/v1/notifications/settings").header(ROLE, "ADMIN")
                .contentType(MediaType.APPLICATION_JSON)
                .content(b.replace("}", ",\"version\":" + v + "}"))).andReturn();
        assertThat(staleSave.getResponse().getStatus()).isEqualTo(409);
    }

    @Test
    void observationsAndNotificationsStayConsistentUnderConcurrentWrites() throws Exception {
        // one real evidence to observe, plus five runs that will notify while others read/mark
        String sid = createSchedule("Observed");
        String firstRun = json.readTree(runNow(sid, "APP").getResponse().getContentAsString()).get("runId").asText();
        executor.execute(UUID.fromString(firstRun));
        UUID evidenceId = records.findByApplicationSlug("payments").get(0).getId();

        List<RunView> pending = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            pending.add(scheduler.trigger(new RunRequest(List.of("payments"), List.of(), List.of("MOCK_GITHUB"), "t"),
                    Trigger.MANUAL));
        }

        List<Callable<Object>> calls = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            int n = i;
            calls.add(() -> mvc.perform(post("/api/v1/evidence/" + evidenceId + "/observations").header(ROLE, "AUDITOR")
                    .contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"observation " + n + "\"}"))
                    .andReturn().getResponse().getStatus());
        }
        for (RunView r : pending) calls.add(() -> { executor.execute(UUID.fromString(r.runId())); return 0; });
        for (int i = 0; i < 3; i++) {
            calls.add(() -> mvc.perform(post("/api/v1/notifications/read-all").header(ROLE, "APP"))
                    .andReturn().getResponse().getStatus());
        }
        List<Object> results = simultaneously(calls);
        assertThat(results.stream().filter(o -> o instanceof Integer i && i >= 500)).isEmpty();

        var obs = observationService.forEvidence(evidenceId);
        assertThat(obs).hasSize(10);
        assertThat(obs.stream().map(o -> o.text()).collect(Collectors.toSet())).hasSize(10);

        List<NotificationView> mine = notifications.list("APP", false).items();
        var perRunChannel = mine.stream().collect(Collectors.groupingBy(n -> n.runId() + "|" + n.channel(), Collectors.counting()));
        assertThat(perRunChannel.values()).containsOnly(1L); // never duplicated
        for (RunView r : pending) {
            assertThat(perRunChannel).containsKeys(r.runId() + "|IN_APP", r.runId() + "|EMAIL_SIMULATED"); // never lost
        }
    }
}
