package com.pramaan.backend.evidence;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * RBAC for evidence approval — {@code X-User-Role} (+ optional {@code X-User-Framework})
 * gates {@code POST /api/v1/evidence/{id}/lifecycle}. One success-in-scope and one
 * rejected-out-of-scope test per role.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class EvidenceApprovalRbacTest {

    @Autowired MockMvc mvc;

    private String ingestAndSubmit(String controlId, String framework) throws Exception {
        String body = """
                {"applicationSlug":"payments","controlId":"%s","framework":"%s",
                 "sourceSystem":"MOCK_JIRA","contentText":"evidence body"}
                """.formatted(controlId, framework);
        String created = mvc.perform(post("/api/v1/evidence/ingest")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(created, "$.evidenceId");

        mvc.perform(post("/api/v1/evidence/{id}/lifecycle", id).contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Role", "APP_OWNER")
                        .content("{\"action\":\"SUBMIT\",\"actor\":\"owner\"}"))
                .andExpect(status().isOk());
        return id;
    }

    private org.springframework.test.web.servlet.ResultActions approve(String id, String role) throws Exception {
        return mvc.perform(post("/api/v1/evidence/{id}/lifecycle", id).contentType(MediaType.APPLICATION_JSON)
                .header("X-User-Role", role)
                .content("{\"action\":\"APPROVE\",\"actor\":\"x\"}"));
    }

    // ---- APP_OWNER — submit only, cannot approve/reject -----------------------

    @Test
    void appOwnerCanSubmitDraftToSubmitted() throws Exception {
        String body = """
                {"applicationSlug":"payments","controlId":"OS-SSH-ROOT-LOGIN","framework":"PCI_DSS",
                 "sourceSystem":"MOCK_JIRA","contentText":"evidence body"}
                """;
        String created = mvc.perform(post("/api/v1/evidence/ingest")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(created, "$.evidenceId");

        mvc.perform(post("/api/v1/evidence/{id}/lifecycle", id).contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Role", "APP_OWNER")
                        .content("{\"action\":\"SUBMIT\",\"actor\":\"owner\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("SUBMITTED"));
    }

    @Test
    void appOwnerCannotApprove() throws Exception {
        String id = ingestAndSubmit("OS-SSH-ROOT-LOGIN-2", "PCI_DSS");
        approve(id, "APP_OWNER").andExpect(status().isForbidden());
    }

    // ---- AUDITOR — generic, any framework, but action-limited (no submit) -----

    @Test
    void genericAuditorCanApproveAnyFramework() throws Exception {
        String id = ingestAndSubmit("DPSC-GOV-01", "DPSC");
        approve(id, "AUDITOR").andExpect(status().isOk()).andExpect(jsonPath("$.state").value("APPROVED"));
    }

    @Test
    void genericAuditorCannotSubmit() throws Exception {
        String body = """
                {"applicationSlug":"payments","controlId":"DPSC-GOV-02","framework":"DPSC",
                 "sourceSystem":"MOCK_JIRA","contentText":"evidence body"}
                """;
        String created = mvc.perform(post("/api/v1/evidence/ingest")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(created, "$.evidenceId");

        mvc.perform(post("/api/v1/evidence/{id}/lifecycle", id).contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Role", "AUDITOR")
                        .content("{\"action\":\"SUBMIT\",\"actor\":\"x\"}"))
                .andExpect(status().isForbidden());
    }

    // ---- ISG_OFFICER — framework=ISG, or control-family VAPT -------------------

    @Test
    void isgOfficerCanApproveIsgFramework() throws Exception {
        String id = ingestAndSubmit("ISG-DOC-01", "ISG");
        approve(id, "ISG_OFFICER").andExpect(status().isOk()).andExpect(jsonPath("$.state").value("APPROVED"));
    }

    @Test
    void isgOfficerCannotApprovePciDss() throws Exception {
        String id = ingestAndSubmit("PCI-DSS-6.2", "PCI_DSS");
        approve(id, "ISG_OFFICER").andExpect(status().isForbidden());
    }

    /** The "or control family VAPT" half of ISG_OFFICER's scope, independent of framework. */
    @Test
    void isgOfficerCanApproveVaptControlFamilyRegardlessOfFramework() throws Exception {
        String id = ingestAndSubmit("VAPT-001", "PCI_DSS");
        approve(id, "ISG_OFFICER").andExpect(status().isOk()).andExpect(jsonPath("$.state").value("APPROVED"));
    }

    // ---- PCIDSS_AUDITOR — framework=PCI_DSS only -------------------------------

    @Test
    void pciDssAuditorCanApprovePciDssFramework() throws Exception {
        String id = ingestAndSubmit("PCI-DSS-12.1", "PCI_DSS");
        approve(id, "PCIDSS_AUDITOR").andExpect(status().isOk()).andExpect(jsonPath("$.state").value("APPROVED"));
    }

    @Test
    void pciDssAuditorCannotApproveDpsc() throws Exception {
        String id = ingestAndSubmit("DPSC-GOV-03", "DPSC");
        approve(id, "PCIDSS_AUDITOR").andExpect(status().isForbidden());
    }

    // ---- DPSC_AUDITOR — framework=DPSC only ------------------------------------

    @Test
    void dpscAuditorCanApproveDpscFramework() throws Exception {
        String id = ingestAndSubmit("DPSC-GOV-04", "DPSC");
        approve(id, "DPSC_AUDITOR").andExpect(status().isOk()).andExpect(jsonPath("$.state").value("APPROVED"));
    }

    @Test
    void dpscAuditorCannotApproveIsg() throws Exception {
        String id = ingestAndSubmit("ISG-DOC-02", "ISG");
        approve(id, "DPSC_AUDITOR").andExpect(status().isForbidden());
    }

    // ---- header edge cases -----------------------------------------------------

    @Test
    void missingRoleHeaderIsBadRequestNotForbidden() throws Exception {
        String id = ingestAndSubmit("ISG-DOC-03", "ISG");
        mvc.perform(post("/api/v1/evidence/{id}/lifecycle", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"APPROVE\",\"actor\":\"x\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void mismatchedUserFrameworkHeaderIsRejected() throws Exception {
        String id = ingestAndSubmit("PCI-DSS-6.3", "PCI_DSS");
        mvc.perform(post("/api/v1/evidence/{id}/lifecycle", id).contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Role", "PCIDSS_AUDITOR")
                        .header("X-User-Framework", "DPSC")
                        .content("{\"action\":\"APPROVE\",\"actor\":\"x\"}"))
                .andExpect(status().isBadRequest());
    }
}
