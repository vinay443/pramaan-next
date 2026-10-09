package com.pramaan.backend.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pramaan.backend.application.OnboardingService;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * UC01-FT010 — a burst of simultaneous runs through the real async executor (dispatch=true, own database,
 * not @Transactional so the worker threads see committed data). The SLA verdict is computed, not assumed:
 * the default SLA must report WITHIN_SLA, and an SLA of 0 seconds must report BREACHED.
 */
class RunBurstSlaTest {

    private static final int BURST = 10;
    private static final String BODY = "{\"applications\":[\"payments\"],\"sources\":[\"MOCK_JIRA\",\"MOCK_GITHUB\"]}";

    abstract static class Base {
        @Autowired MockMvc mvc;
        @Autowired ObjectMapper json;
        @Autowired OnboardingService onboarding;

        @BeforeEach
        void onboardApps() {
            onboarding.apply(false);
        }

        JsonNode submit() throws Exception {
            return json.readTree(mvc.perform(post("/api/v1/scheduler/runs").header("X-Pramaan-Role", "APP")
                            .contentType(MediaType.APPLICATION_JSON).content(BODY))
                    .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString());
        }

        JsonNode awaitTerminal(String runId) throws Exception {
            Instant deadline = Instant.now().plus(Duration.ofSeconds(90));
            while (Instant.now().isBefore(deadline)) {
                JsonNode run = json.readTree(mvc.perform(get("/api/v1/scheduler/runs/" + runId)
                                .header("X-Pramaan-Role", "APP")).andReturn().getResponse().getContentAsString());
                String st = run.get("status").asText();
                if (!st.equals("PENDING") && !st.equals("RUNNING")) return run;
                Thread.sleep(100);
            }
            throw new AssertionError("run " + runId + " did not finish in 90s");
        }
    }

    @SpringBootTest(properties = {"pramaan.scheduler.dispatch=true", "spring.datasource.url=jdbc:h2:mem:burst;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"})
    @AutoConfigureMockMvc
    @ActiveProfiles("test")
    static class DefaultSla extends Base {

        @Test
        void tenSimultaneousRunsAreAcceptedFinishConsistentlyAndStayWithinTheSla() throws Exception {
            ExecutorService clients = Executors.newFixedThreadPool(BURST);
            List<Future<JsonNode>> submitted = new ArrayList<>();
            for (int i = 0; i < BURST; i++) {
                Callable<JsonNode> c = this::submit; // any 5xx / 429 / rejection would fail isAccepted()
                submitted.add(clients.submit(c));
            }
            List<JsonNode> done = new ArrayList<>();
            for (Future<JsonNode> f : submitted) done.add(awaitTerminal(f.get().get("runId").asText()));
            clients.shutdown();

            assertThat(done).hasSize(BURST);
            long slowest = 0, total = 0;
            for (JsonNode run : done) {
                assertThat(run.get("status").asText()).isEqualTo("COMPLETED");
                int received = run.get("received").asInt();
                assertThat(received).isEqualTo(run.get("ingested").asInt() + run.get("duplicates").asInt()
                        + run.get("failed").asInt());
                assertThat(received).isGreaterThan(0);
                assertThat(run.get("slaSeconds").asInt()).isEqualTo(60);
                assertThat(run.get("slaStatus").asText()).isEqualTo("WITHIN_SLA");
                assertThat(run.get("queuedMs").asLong()).isGreaterThanOrEqualTo(0);
                long elapsed = Duration.between(Instant.parse(run.get("createdAt").asText()),
                        Instant.parse(run.get("finishedAt").asText())).toMillis();
                slowest = Math.max(slowest, elapsed);
                total += elapsed;
            }
            System.out.println("FT010 burst of " + BURST + ": slowest request-to-finish " + slowest
                    + " ms, average " + (total / BURST) + " ms");
            // evidence is deduplicated across the burst: one canonical record per source item
            assertThat(done.stream().mapToInt(r -> r.get("ingested").asInt()).sum())
                    .isLessThanOrEqualTo(done.get(0).get("received").asInt());
            mvc.perform(get("/api/v1/system/status").header("X-Pramaan-Role", "APP")).andExpect(status().isOk())
                    .andExpect(jsonPath("$.slaSeconds").value(60));
        }
    }

    @SpringBootTest(properties = {"pramaan.scheduler.dispatch=true", "pramaan.scheduler.sla-seconds=0",
            "spring.datasource.url=jdbc:h2:mem:burst0;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"})
    @AutoConfigureMockMvc
    @ActiveProfiles("test")
    static class ZeroSla extends Base {

        @Test
        void aZeroSecondSlaIsReportedAsBreached() throws Exception {
            JsonNode run = awaitTerminal(submit().get("runId").asText());
            assertThat(run.get("slaSeconds").asInt()).isZero();
            assertThat(run.get("slaStatus").asText()).isEqualTo("BREACHED");
        }
    }
}
