package com.pramaan.backend.observation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pramaan.backend.application.OnboardingService;
import com.pramaan.backend.evidence.EvidenceLifecycleService;
import com.pramaan.backend.evidence.EvidenceQueryService;
import com.pramaan.backend.evidence.domain.EvidenceRecord;
import com.pramaan.backend.evidence.repo.EvidenceRecordRepository;
import com.pramaan.backend.insight.ComplianceService;
import com.pramaan.backend.scheduler.ScheduleJobService;
import com.pramaan.backend.scheduler.SchedulerDtos.CreateScheduleRequest;
import com.pramaan.backend.scheduler.SchedulerRunExecutor;
import com.pramaan.backend.security.SecurityEventService;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** UC01-FT014 follow-up - first-class audit observations on scheduled-run evidence. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ObservationTest {

    private static final String ROLE = "X-Pramaan-Role";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired OnboardingService onboarding;
    @Autowired ScheduleJobService jobs;
    @Autowired SchedulerRunExecutor executor;
    @Autowired EvidenceRecordRepository records;
    @Autowired EvidenceQueryService queries;
    @Autowired EvidenceLifecycleService lifecycle;
    @Autowired ComplianceService compliance;
    @Autowired SecurityEventService security;

    EvidenceRecord evidence;

    @BeforeEach
    void scheduledEvidence() {
        onboarding.apply(false);
        var job = jobs.create(new CreateScheduleRequest("Obs job", "MOCK_JIRA", "payments", "CODE-REVIEW", "DAILY", "tester"));
        var run = jobs.runNow(UUID.fromString(job.id()), "tester");
        executor.execute(UUID.fromString(run.runId()));
        evidence = records.findByApplicationSlug("payments").get(0);
    }

    private String path() {
        return "/api/v1/evidence/" + evidence.getId() + "/observations";
    }

    private JsonNode raise(String role, String text, int expectedStatus) throws Exception {
        var res = mvc.perform(post(path()).header(ROLE, role).header("X-Pramaan-User", "Lead Auditor")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"text\":" + json.writeValueAsString(text) + "}"))
                .andExpect(status().is(expectedStatus)).andReturn().getResponse().getContentAsString();
        return json.readTree(res);
    }

    private int openObs() {
        return compliance.forApplication("payments", null).controls().stream()
                .filter(c -> c.framework().equalsIgnoreCase(evidence.getFramework())
                        && c.controlId().equalsIgnoreCase(evidence.getControlId()))
                .findFirst().orElseThrow().openObservations();
    }

    @Test
    void auditorRaisesAndClosesAnObservationCopiedFromTheEvidenceAndTheComplianceCountFollows() throws Exception {
        int historyBefore = lifecycle.get(evidence.getId()).history().size();
        String shaBefore = evidence.latestVersion().getSha256();
        assertThat(openObs()).isZero();

        JsonNode o = raise("AUDITOR", "Retention evidence is missing the approver sign-off", 201);
        assertThat(o.get("status").asText()).isEqualTo("OPEN");
        assertThat(o.get("controlId").asText()).isEqualTo(evidence.getControlId());
        assertThat(o.get("framework").asText()).isEqualTo(evidence.getFramework());
        assertThat(o.get("application").asText()).isEqualTo("payments");
        assertThat(o.get("raisedByRole").asText()).isEqualTo("AUDITOR");
        assertThat(o.get("raisedBy").asText()).isEqualTo("Lead Auditor");
        assertThat(o.get("closedAt").isNull()).isTrue();

        mvc.perform(get(path()).header(ROLE, "CIO")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(o.get("id").asText()));
        mvc.perform(get("/api/v1/observations").param("control", evidence.getControlId())
                        .param("framework", evidence.getFramework()).param("status", "OPEN").header(ROLE, "APP"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(o.get("id").asText()));
        assertThat(openObs()).isEqualTo(1);

        mvc.perform(post("/api/v1/observations/" + o.get("id").asText() + "/close").header(ROLE, "AUDITOR"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.closedAt").isNotEmpty());
        assertThat(openObs()).isZero();
        mvc.perform(get("/api/v1/observations").param("status", "OPEN").param("control", evidence.getControlId())
                        .header(ROLE, "ADMIN")).andExpect(jsonPath("$").isEmpty());

        // evidence integrity and lifecycle are untouched
        assertThat(queries.verify(evidence.getId(), null).intact()).isTrue();
        assertThat(evidence.latestVersion().getSha256()).isEqualTo(shaBefore);
        assertThat(lifecycle.get(evidence.getId()).history()).hasSize(historyBefore);
    }

    @Test
    void appOwnerCanReadButNotRaiseOrCloseAndTheDenialIsRecorded() throws Exception {
        JsonNode o = raise("ADMIN", "Observation by admin", 201);
        long deniedBefore = security.list("ACCESS_DENIED", 500).stream()
                .filter(e -> path().equals(e.path()) && "APP".equals(e.role())).count();

        raise("APP", "Not allowed", 403);
        mvc.perform(post("/api/v1/observations/" + o.get("id").asText() + "/close").header(ROLE, "APP"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error").value("FORBIDDEN"));
        mvc.perform(get(path()).header(ROLE, "APP")).andExpect(status().isOk());

        long denied = security.list("ACCESS_DENIED", 500).stream()
                .filter(e -> path().equals(e.path()) && "APP".equals(e.role())).count();
        assertThat(denied).isEqualTo(deniedBefore + 1);
        mvc.perform(post(path()).contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"x\"}"))
                .andExpect(status().isForbidden()); // missing role
    }

    @Test
    void unsafeTextAndBadIdentifiersAreRejected() throws Exception {
        long before = security.list("INPUT_REJECTED", 500).stream()
                .filter(e -> e.detail() != null && e.detail().contains("<script>")).count();
        raise("AUDITOR", "<script>alert(1)</script>", 400);
        assertThat(security.list("INPUT_REJECTED", 500).stream()
                .filter(e -> e.detail() != null && e.detail().contains("<script>")).count()).isEqualTo(before + 1);
        raise("AUDITOR", "x".repeat(1001), 400);
        raise("AUDITOR", "  ", 400);
        mvc.perform(get(path()).header(ROLE, "APP")).andExpect(jsonPath("$").isEmpty()); // nothing was stored

        mvc.perform(post("/api/v1/evidence/{id}/observations", "<img src=x onerror=alert(1)>").header(ROLE, "AUDITOR")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"ok\"}"))
                .andExpect(status().isBadRequest());
        assertThat(security.list("INVALID_IDENTIFIER", 500).stream()
                .anyMatch(e -> e.detail() != null && e.detail().contains("onerror=alert(1)"))).isTrue();
        mvc.perform(post("/api/v1/evidence/" + UUID.randomUUID() + "/observations").header(ROLE, "AUDITOR")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"ok\"}")).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/observations/" + UUID.randomUUID() + "/close").header(ROLE, "AUDITOR"))
                .andExpect(status().isNotFound());
    }
}
