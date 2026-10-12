package com.pramaan.backend.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pramaan.backend.application.OnboardingService;
import com.pramaan.backend.integrations.SourceConnectionService;
import com.pramaan.backend.integrations.SourceConnectionService.SetConnectionRequest;
import com.pramaan.backend.notification.NotificationService.NotificationList;
import com.pramaan.backend.notification.NotificationService.NotificationView;
import com.pramaan.backend.notification.NotificationService.SettingsView;
import com.pramaan.backend.scheduler.SchedulerDtos.RunRequest;
import com.pramaan.backend.scheduler.SchedulerDtos.RunView;
import com.pramaan.backend.scheduler.SchedulerRunExecutor;
import com.pramaan.backend.scheduler.SchedulerService;
import com.pramaan.backend.scheduler.domain.SchedulerRun.Trigger;
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

/** UC01-FT011 - run notifications (in-app + simulated email), settings and role scoping. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class NotificationTest {

    @Autowired MockMvc mvc;
    @Autowired SchedulerService scheduler;
    @Autowired SchedulerRunExecutor executor;
    @Autowired NotificationService notifications;
    @Autowired SourceConnectionService connections;
    @Autowired OnboardingService onboarding;

    @BeforeEach
    void onboardApps() {
        onboarding.apply(false);
    }

    private RunView run(String... sources) {
        RunView started = scheduler.trigger(new RunRequest(List.of("payments"), List.of(), List.of(sources), "tester"),
                Trigger.MANUAL);
        executor.execute(UUID.fromString(started.runId()));
        return scheduler.get(UUID.fromString(started.runId()));
    }

    private void invalidateJira() {
        connections.set("MOCK_JIRA", new SetConnectionRequest("INVALID", "tester"));
    }

    private static SettingsView settings(boolean enabled, boolean onSuccess, boolean onFailure) {
        return new SettingsView(enabled, onSuccess, onFailure, List.of("APP", "ADMIN"), true);
    }

    private List<NotificationView> inApp(String role) {
        return notifications.list(role, false).items().stream().filter(n -> n.channel().equals("IN_APP")).toList();
    }

    @Test
    void failedRunNotifiesEveryRecipientRoleWithRunIdAndErrorDetail() {
        invalidateJira();
        RunView failed = run("MOCK_JIRA");

        for (String role : new String[] {"APP", "ADMIN"}) {
            assertThat(inApp(role)).singleElement().satisfies(n -> {
                assertThat(n.type()).isEqualTo("RUN_FAILED");
                assertThat(n.severity()).isEqualTo("ERROR");
                assertThat(n.runId()).isEqualTo(failed.runId());
                assertThat(n.message()).contains(failed.runId(), "MANUAL", "MOCK_JIRA: AUTH_FAILED - Authentication failed");
            });
            // simulated email: a delivery record, already "read", so it never inflates the unread badge
            assertThat(notifications.list(role, false).items()).extracting(NotificationView::channel)
                    .containsExactlyInAnyOrder("IN_APP", "EMAIL_SIMULATED");
            assertThat(notifications.list(role, false).unreadCount()).isEqualTo(1);
        }
    }

    @Test
    void successfulRunNotifiesWithCountsAndPartialRunIsAWarning() {
        RunView ok = run("MOCK_GITHUB");
        assertThat(inApp("APP")).singleElement().satisfies(n -> {
            assertThat(n.type()).isEqualTo("RUN_COMPLETED");
            assertThat(n.severity()).isEqualTo("INFO");
            assertThat(n.message()).contains("received=" + ok.received(), "ingested=" + ok.ingested(),
                    "duplicates=" + ok.duplicates(), "failed=" + ok.failed());
        });

        invalidateJira();
        RunView partial = run("MOCK_JIRA", "MOCK_GITHUB");
        assertThat(partial.status()).isEqualTo("PARTIAL");
        assertThat(inApp("APP")).extracting(NotificationView::type).contains("RUN_PARTIAL");
        assertThat(inApp("APP").get(0).severity()).isEqualTo("WARNING");
    }

    @Test
    void settingsGateNotifications() {
        notifications.saveSettings(settings(false, true, true));
        invalidateJira();
        run("MOCK_JIRA");
        run("MOCK_GITHUB");
        assertThat(notifications.list("APP", false).items()).isEmpty();

        notifications.saveSettings(settings(true, false, true));
        run("MOCK_GITHUB");
        assertThat(notifications.list("APP", false).items()).isEmpty(); // success muted
        invalidateJira(); // INVALID recovers after one failed run, so fail it again
        run("MOCK_JIRA");
        assertThat(inApp("APP")).singleElement().satisfies(n -> assertThat(n.type()).isEqualTo("RUN_FAILED"));
    }

    @Test
    void notificationsAreScopedToTheCallersRoleAndSettingsAreManageOnly() throws Exception {
        invalidateJira();
        run("MOCK_JIRA");

        mvc.perform(get("/api/v1/notifications").header("X-Pramaan-Role", "CIO")).andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty()).andExpect(jsonPath("$.unreadCount").value(0));
        mvc.perform(get("/api/v1/notifications").header("X-Pramaan-Role", "AUDITOR")).andExpect(status().isOk());

        NotificationList mine = notifications.list("APP", true);
        assertThat(mine.unreadCount()).isEqualTo(1);
        String id = mine.items().get(0).id();
        // another role cannot touch it
        mvc.perform(post("/api/v1/notifications/" + id + "/read").header("X-Pramaan-Role", "CIO"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/notifications/" + id + "/read").header("X-Pramaan-Role", "APP"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/notifications").header("X-Pramaan-Role", "APP"))
                .andExpect(jsonPath("$.unreadCount").value(0));
        mvc.perform(post("/api/v1/notifications/read-all").header("X-Pramaan-Role", "ADMIN")).andExpect(status().isOk());

        String body = "{\"enabled\":true,\"notifyOnSuccess\":true,\"notifyOnFailure\":true,"
                + "\"recipientRoles\":[\"APP\"],\"emailSimulated\":false}";
        mvc.perform(put("/api/v1/notifications/settings").header("X-Pramaan-Role", "AUDITOR")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error").value("FORBIDDEN"));
        mvc.perform(put("/api/v1/notifications/settings").header("X-Pramaan-Role", "APP")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.recipientRoles[0]").value("APP"));
        mvc.perform(get("/api/v1/notifications")).andExpect(status().isForbidden()); // no role header
    }

    @Test
    void finalisingTheSameRunTwiceCreatesNoDuplicates() {
        invalidateJira();
        RunView failed = run("MOCK_JIRA");
        int before = notifications.list("APP", false).items().size();

        executor.execute(UUID.fromString(failed.runId())); // finalised a second time

        assertThat(notifications.list("APP", false).items()).hasSize(before);
        assertThat(notifications.list("ADMIN", false).items()).hasSize(before);
    }
}
