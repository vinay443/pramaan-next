package com.pramaan.backend.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pramaan.backend.application.OnboardingService;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/** UC01-FT009 — scheduler role policy (persona sent as X-Pramaan-Role; demo mode, no real authentication). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SchedulerAccessTest {

    private static final String ROLE = "X-Pramaan-Role";
    private static final String BODY = """
            {"name":"Access job","sourceSystem":"MOCK_GITHUB","application":"payments","evidenceType":"CODE-REVIEW","frequency":"DAILY"}""";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired OnboardingService onboarding;
    @Autowired ScheduleJobService jobs;
    @Autowired SchedulerService scheduler;
    @Autowired SchedulerRunExecutor executor;

    @BeforeEach
    void onboardApps() {
        onboarding.apply(false);
    }

    private static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder b, String role) {
        return role == null ? b : b.header(ROLE, role);
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder b) {
        return b.contentType(MediaType.APPLICATION_JSON);
    }

    private String createdScheduleId() throws Exception {
        JsonNode created = json.readTree(mvc.perform(as(json(post("/api/v1/schedules")).content(BODY), "APP"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        return created.get("id").asText();
    }

    @Test
    void appOwnerAndAdminCanManageTheScheduler() throws Exception {
        for (String role : new String[] {"APP", "ADMIN"}) {
            String id = json.readTree(mvc.perform(as(json(post("/api/v1/schedules")).content(BODY), role))
                    .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asText();
            mvc.perform(as(patch("/api/v1/schedules/" + id).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"active\":false}"), role)).andExpect(status().isOk());
            JsonNode run = json.readTree(mvc.perform(as(post("/api/v1/schedules/" + id + "/run-now"), role))
                    .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString());
            executor.execute(UUID.fromString(run.get("runId").asText())); // retry needs a finished run
            mvc.perform(as(post("/api/v1/scheduler/runs/" + run.get("runId").asText() + "/retry"), role))
                    .andExpect(status().isAccepted());
            mvc.perform(as(json(post("/api/v1/scheduler/runs")).content("{\"sources\":[\"MOCK_GITHUB\"]}"), role))
                    .andExpect(status().isAccepted());
            mvc.perform(as(json(put("/api/v1/sources/MOCK_JIRA/connection")).content("{\"status\":\"VALID\"}"), role))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void otherRolesAreBlockedFromManagingButCanView() throws Exception {
        String id = createdScheduleId();
        for (String role : new String[] {"AUDITOR", "FH", "VH", "CIO"}) {
            String expected = "Role " + role + " is not permitted to manage the scheduler";
            for (MockHttpServletRequestBuilder b : new MockHttpServletRequestBuilder[] {
                    json(post("/api/v1/schedules")).content(BODY),
                    json(patch("/api/v1/schedules/" + id)).content("{\"active\":false}"),
                    post("/api/v1/schedules/" + id + "/run-now"),
                    json(post("/api/v1/scheduler/runs")).content("{}"),
                    post("/api/v1/scheduler/runs/" + UUID.randomUUID() + "/retry"),
                    json(put("/api/v1/sources/MOCK_JIRA/connection")).content("{\"status\":\"INVALID\"}")}) {
                mvc.perform(as(b, role)).andExpect(status().isForbidden())
                        .andExpect(jsonPath("$.error").value("FORBIDDEN"))
                        .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith(expected)));
            }
            for (String path : new String[] {"/api/v1/schedules", "/api/v1/schedules/options", "/api/v1/scheduler/runs",
                    "/api/v1/scheduler/sources", "/api/v1/sources/connections", "/api/v1/system/status"}) {
                mvc.perform(as(get(path), role)).andExpect(status().isOk());
            }
        }
    }

    @Test
    void missingOrUnknownRoleIsForbiddenOnManageAndViewEndpoints() throws Exception {
        for (String role : new String[] {null, "hacker", "app", ""}) {
            mvc.perform(as(json(post("/api/v1/schedules")).content(BODY), role)).andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error").value("FORBIDDEN"))
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("unknown or missing role")));
            mvc.perform(as(get("/api/v1/schedules"), role)).andExpect(status().isForbidden());
            mvc.perform(as(get("/api/v1/scheduler/runs"), role)).andExpect(status().isForbidden());
            mvc.perform(as(get("/api/v1/sources/connections"), role)).andExpect(status().isForbidden());
        }
    }

    @Test
    void deniedRunNowCreatesNoRunAndLeavesLastRunAtAlone() throws Exception {
        String id = createdScheduleId();
        long runsBefore = scheduler.list(0, 50).totalItems();

        mvc.perform(as(post("/api/v1/schedules/" + id + "/run-now"), "AUDITOR")).andExpect(status().isForbidden());

        assertThat(scheduler.list(0, 50).totalItems()).isEqualTo(runsBefore);
        assertThat(jobs.list().get(0).lastRunAt()).isNull();
    }
}
