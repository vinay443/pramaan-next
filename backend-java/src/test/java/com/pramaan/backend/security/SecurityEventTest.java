package com.pramaan.backend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pramaan.backend.application.OnboardingService;
import com.pramaan.backend.security.SecurityEvent.Type;
import com.pramaan.backend.security.SecurityEventService.EventView;
import java.util.List;
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

/**
 * UC01-FT012 - blocked requests are prevented AND recorded. Events are committed in their own transaction
 * (so they outlive the test's rollback); each test therefore matches only its own uniquely-addressed events.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SecurityEventTest {

    private static final String ROLE = "X-Pramaan-Role";

    @Autowired MockMvc mvc;
    @Autowired SecurityEventService security;
    @Autowired OnboardingService onboarding;

    @BeforeEach
    void onboardApps() {
        onboarding.apply(false);
    }

    private List<EventView> events() {
        return security.list(null, 500);
    }

    private long count(Type type, String needle) {
        return events().stream().filter(e -> e.eventType().equals(type.name()))
                .filter(e -> (e.path() != null && e.path().contains(needle)) || (e.detail() != null && e.detail().contains(needle)))
                .count();
    }

    private static String create(String name) {
        return "{\"name\":" + json(name) + ",\"sourceSystem\":\"MOCK_GITHUB\",\"application\":\"payments\","
                + "\"evidenceType\":\"CODE-REVIEW\",\"frequency\":\"DAILY\"}";
    }

    private static String json(String v) {
        return "\"" + v.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    @Test
    void deniedManageCallIsBlockedAndRecordedOnceWithRoleAndPath() throws Exception {
        String path = "/api/v1/schedules/" + UUID.randomUUID() + "/run-now";
        mvc.perform(post(path).header(ROLE, "AUDITOR").header("X-Pramaan-User", "Lead Auditor"))
                .andExpect(status().isForbidden());

        List<EventView> hits = events().stream().filter(e -> path.equals(e.path())).toList();
        assertThat(hits).singleElement().satisfies(e -> {
            assertThat(e.eventType()).isEqualTo("ACCESS_DENIED");
            assertThat(e.outcome()).isEqualTo("BLOCKED");
            assertThat(e.role()).isEqualTo("AUDITOR");
            assertThat(e.actor()).isEqualTo("Lead Auditor");
            assertThat(e.method()).isEqualTo("POST");
            assertThat(e.detail()).contains("manage the scheduler");
        });
    }

    @Test
    void unknownRoleIsRecordedWithTheReceivedValue() throws Exception {
        String probe = "hacker-" + UUID.randomUUID();
        mvc.perform(get("/api/v1/schedules").header(ROLE, probe)).andExpect(status().isForbidden());
        assertThat(count(Type.UNKNOWN_ROLE, probe)).isEqualTo(1);
    }

    @Test
    void scriptInANameIsRejectedWithNothingCreatedAndOneInputRejectedEvent() throws Exception {
        String name = "<script>alert(1)</script>";
        long before = count(Type.INPUT_REJECTED, name);

        mvc.perform(post("/api/v1/schedules").header(ROLE, "APP").contentType(MediaType.APPLICATION_JSON).content(create(name)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("name contains characters that are not allowed")));

        mvc.perform(get("/api/v1/schedules").header(ROLE, "APP")).andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        assertThat(count(Type.INPUT_REJECTED, name)).isEqualTo(before + 1);
        EventView e = events().stream().filter(x -> x.detail() != null && x.detail().contains(name)).findFirst().orElseThrow();
        assertThat(e.detail()).startsWith("name ").hasSizeLessThanOrEqualTo(300);
        assertThat(e.path()).isEqualTo("/api/v1/schedules");

        // too long / blank are rejected too, run bodies included
        mvc.perform(post("/api/v1/schedules").header(ROLE, "APP").contentType(MediaType.APPLICATION_JSON)
                        .content(create("x".repeat(101)))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/scheduler/runs").header(ROLE, "APP").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sources\":[\"MOCK_JIRA;DROP\"]}")).andExpect(status().isBadRequest());
    }

    @Test
    void markupInTheUserHeaderIsRejected() throws Exception {
        String path = "/api/v1/schedules/" + UUID.randomUUID() + "/run-now";
        mvc.perform(post(path).header(ROLE, "APP").header("X-Pramaan-User", "<b>x</b>"))
                .andExpect(status().isBadRequest());
        assertThat(events().stream().filter(e -> path.equals(e.path()) && e.eventType().equals("INPUT_REJECTED")))
                .hasSize(1);
        // the cleaned characters are the only ones allowed
        assertThat(security.validUser("App Owner")).isTrue();
        assertThat(security.validUser("a.b_c@d-e 1")).isTrue();
        assertThat(security.validUser("x".repeat(65))).isFalse();
    }

    @Test
    void malformedIdIsRecordedButAWellFormedUnknownIdIsAPlain404() throws Exception {
        String tampered = "<img src=x onerror=alert(1)>";
        long before = count(Type.INVALID_IDENTIFIER, "onerror=alert(1)");
        mvc.perform(get("/api/v1/scheduler/runs/{id}", tampered).header(ROLE, "APP")).andExpect(status().isBadRequest());
        assertThat(count(Type.INVALID_IDENTIFIER, "onerror=alert(1)")).isEqualTo(before + 1);

        String unknown = UUID.randomUUID().toString();
        mvc.perform(get("/api/v1/scheduler/runs/" + unknown).header(ROLE, "APP")).andExpect(status().isNotFound());
        assertThat(events().stream().filter(e -> e.path() != null && e.path().contains(unknown))).isEmpty();
    }

    @Test
    void securityEventsAreVisibleToAdminAndAuditorOnlyAndTheDenialIsLogged() throws Exception {
        mvc.perform(get("/api/v1/security/events").header(ROLE, "ADMIN")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/security/events?limit=5&type=ACCESS_DENIED").header(ROLE, "AUDITOR")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/security/events?type=NOPE").header(ROLE, "ADMIN")).andExpect(status().isBadRequest());

        long before = events().stream().filter(e -> "/api/v1/security/events".equals(e.path()) && "APP".equals(e.role())).count();
        mvc.perform(get("/api/v1/security/events").header(ROLE, "APP")).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("not permitted to view security events")));
        long after = events().stream().filter(e -> "/api/v1/security/events".equals(e.path()) && "APP".equals(e.role())
                && e.eventType().equals("ACCESS_DENIED")).count();
        assertThat(after).isEqualTo(before + 1);
    }

    @Test
    void storedDetailIsSanitizedAndTruncatedTo300() {
        String marker = "long-" + UUID.randomUUID();
        security.record(Type.INPUT_REJECTED, marker + "\u0007\n" + "a".repeat(500));

        EventView e = events().stream().filter(x -> x.detail() != null && x.detail().startsWith(marker)).findFirst().orElseThrow();
        assertThat(e.detail()).hasSize(300).doesNotContain("\u0007").doesNotContain("\n");
        assertThat(e.outcome()).isEqualTo("BLOCKED");
    }
}
