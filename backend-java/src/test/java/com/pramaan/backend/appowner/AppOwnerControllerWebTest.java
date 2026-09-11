package com.pramaan.backend.appowner;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.pramaan.backend.appowner.domain.ApplicationOwner;
import com.pramaan.backend.appowner.repo.ApplicationOwnerRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
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
class AppOwnerControllerWebTest {

    @Autowired MockMvc mvc;
    @Autowired ApplicationOwnerRepository owners;
    @Autowired Clock clock;

    private static final String OWNER_USER = "app.owner.demo";

    /** app.owner.demo -> net-banking only, for this test (seed data is off in the test profile). */
    private void ownNetBanking() {
        owners.save(new ApplicationOwner(UUID.randomUUID(), "net-banking", OWNER_USER, clock.instant()));
    }

    private String ingest(String applicationSlug, String controlId, String framework, String content) throws Exception {
        String body = """
                {"applicationSlug":"%s","controlId":"%s","framework":"%s",
                 "sourceSystem":"MOCK_JIRA","contentText":"%s"}
                """.formatted(applicationSlug, controlId, framework, content);
        String created = mvc.perform(post("/api/v1/evidence/ingest")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(created, "$.evidenceId");
    }

    @Test
    void dashboardIsScopedToOwnedApplicationsOnly() throws Exception {
        ownNetBanking();
        ingest("net-banking", "PCI-DSS-6.2", "PCI_DSS", "owned evidence");
        ingest("payments", "PCI-DSS-6.2", "PCI_DSS", "unowned evidence");

        mvc.perform(get("/api/v1/app-owner/dashboard")
                        .header("X-User-Role", "APP_OWNER").header("X-User-Username", OWNER_USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownedApplications", org.hamcrest.Matchers.contains("net-banking")))
                .andExpect(jsonPath("$.ownedApplications", org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.hasItem("payments"))));
    }

    @Test
    void evidenceDetailRejectsCrossApplicationAccessEvenWithAValidEvidenceId() throws Exception {
        ownNetBanking();
        String unownedId = ingest("payments", "ITPP-DOC-03", "ITPP", "not app.owner.demo's app");

        mvc.perform(get("/api/v1/app-owner/evidence/{id}", unownedId)
                        .header("X-User-Role", "APP_OWNER").header("X-User-Username", OWNER_USER))
                .andExpect(status().isForbidden());
    }

    @Test
    void missingIdentityHeadersAreRejected() throws Exception {
        mvc.perform(get("/api/v1/app-owner/dashboard"))
                .andExpect(status().isBadRequest());

        mvc.perform(get("/api/v1/app-owner/dashboard").header("X-User-Role", "AUDITOR")
                        .header("X-User-Username", "auditor.demo"))
                .andExpect(status().isForbidden());
    }

    @Test
    void resubmitAfterRejectionPreservesHistoryAndIsBlockedFromNonRejectedState() throws Exception {
        ownNetBanking();
        String id = ingest("net-banking", "ITPP-DOC-03", "ITPP", "policy v1");

        mvc.perform(post("/api/v1/evidence/{id}/lifecycle", id).contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Role", "APP_OWNER")
                        .content("{\"action\":\"SUBMIT\",\"actor\":\"" + OWNER_USER + "\"}"))
                .andExpect(status().isOk());

        // App Owner cannot approve/reject — already blocked by the existing evidence-approval RBAC.
        mvc.perform(post("/api/v1/app-owner/evidence/{id}/resubmit", id)
                        .header("X-User-Role", "APP_OWNER").header("X-User-Username", OWNER_USER)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isConflict()); // still SUBMITTED, not REJECTED yet

        mvc.perform(post("/api/v1/evidence/{id}/lifecycle", id).contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Role", "AUDITOR")
                        .content("{\"action\":\"REJECT\",\"actor\":\"auditor\",\"note\":\"missing signature\"}"))
                .andExpect(status().isOk());

        mvc.perform(post("/api/v1/app-owner/evidence/{id}/resubmit", id)
                        .header("X-User-Role", "APP_OWNER").header("X-User-Username", OWNER_USER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"fixed, re-signed\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("SUBMITTED"));

        // full history preserved — nothing overwritten
        mvc.perform(get("/api/v1/evidence/{id}/lifecycle", id))
                .andExpect(jsonPath("$.history[?(@.action=='REJECT')]").exists())
                .andExpect(jsonPath("$.history[?(@.action=='SUBMIT')]").exists());
    }

    @Test
    void appOwnerCannotApproveOrRejectViaTheSharedLifecycleEndpoint() throws Exception {
        ownNetBanking();
        String id = ingest("net-banking", "ITPP-DOC-04", "ITPP", "doc");
        mvc.perform(post("/api/v1/evidence/{id}/lifecycle", id).contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Role", "APP_OWNER")
                        .content("{\"action\":\"SUBMIT\",\"actor\":\"" + OWNER_USER + "\"}"))
                .andExpect(status().isOk());

        mvc.perform(post("/api/v1/evidence/{id}/lifecycle", id).contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Role", "APP_OWNER")
                        .content("{\"action\":\"APPROVE\",\"actor\":\"" + OWNER_USER + "\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void targetDateValidatesRulesAndRecordsHistory() throws Exception {
        ownNetBanking();
        String id = ingest("net-banking", "ITPP-DOC-05", "ITPP", "doc");

        // too soon (today) — rejected
        String today = LocalDate.now().toString();
        mvc.perform(post("/api/v1/app-owner/evidence/{id}/target-date", id)
                        .header("X-User-Role", "APP_OWNER").header("X-User-Username", OWNER_USER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetDate\":\"" + today + "\",\"comment\":\"c\"}"))
                .andExpect(status().isBadRequest());

        String future = LocalDate.now().plusDays(10).toString();
        mvc.perform(post("/api/v1/app-owner/evidence/{id}/target-date", id)
                        .header("X-User-Role", "APP_OWNER").header("X-User-Username", OWNER_USER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetDate\":\"" + future + "\",\"comment\":\"initial commitment\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evidenceId").value(id));

        mvc.perform(get("/api/v1/app-owner/evidence/{id}/target-date/history", id)
                        .header("X-User-Role", "APP_OWNER").header("X-User-Username", OWNER_USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].newTargetDate").value(future))
                .andExpect(jsonPath("$[0].actorUsername").value(OWNER_USER));
    }

    @Test
    void reportsAreScopedAndRejectUnownedApplication() throws Exception {
        ownNetBanking();
        ingest("payments", "PCI-DSS-6.2", "PCI_DSS", "unowned");

        mvc.perform(get("/api/v1/app-owner/reports/evidence-register")
                        .header("X-User-Role", "APP_OWNER").header("X-User-Username", OWNER_USER)
                        .param("applicationSlug", "payments"))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/v1/app-owner/reports/evidence-register")
                        .header("X-User-Role", "APP_OWNER").header("X-User-Username", OWNER_USER)
                        .param("applicationSlug", "net-banking"))
                .andExpect(status().isOk());
    }

    @Test
    void adminEndpointsAreBlockedForAppOwnerRoleHeaderButOpenOtherwise() throws Exception {
        mvc.perform(get("/api/v1/admin/roles").header("X-User-Role", "APP_OWNER"))
                .andExpect(status().isForbidden());

        // no role header at all (the existing, unauthenticated default) is unaffected
        mvc.perform(get("/api/v1/admin/roles"))
                .andExpect(status().isOk());
    }
}
