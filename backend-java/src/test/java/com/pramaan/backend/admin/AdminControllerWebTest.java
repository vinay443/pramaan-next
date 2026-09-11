package com.pramaan.backend.admin;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminControllerWebTest {

    @Autowired MockMvc mvc;

    @Test
    void rolesCatalogueIsCanonicalAndReadOnly() throws Exception {
        mvc.perform(get("/api/v1/admin/roles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(8))
                .andExpect(jsonPath("$[?(@.id=='ADMIN')].description").exists());
    }

    /**
     * The evidence-approval RBAC roles are reused into this same catalogue — not a
     * second endpoint. Exactly 3 new ids (ISG_OFFICER, PCIDSS_AUDITOR, DPSC_AUDITOR)
     * are appended; AUDITOR / APP_OWNER already exist in the configured catalogue and
     * are not duplicated — proven by the total staying at 8 (5 configured + 3 new),
     * asserted in {@link #rolesCatalogueIsCanonicalAndReadOnly()}.
     */
    @Test
    void evidenceApprovalRolesAreMergedIntoTheSameCatalogue() throws Exception {
        mvc.perform(get("/api/v1/admin/roles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(8))
                .andExpect(jsonPath("$[?(@.id=='ISG_OFFICER')].description").exists())
                .andExpect(jsonPath("$[?(@.id=='PCIDSS_AUDITOR')].description").exists())
                .andExpect(jsonPath("$[?(@.id=='DPSC_AUDITOR')].description").exists());
    }

    @Test
    void seededUsersAreListed() throws Exception {
        mvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.username=='admin')].roles[0]").value("ADMIN"));
    }

    /** The 5 evidence-approval RBAC demo users are reused into this same roster. */
    @Test
    void evidenceApprovalDemoUsersAreMergedIntoTheSameRoster() throws Exception {
        mvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.username=='app.owner.demo')].roles[0]").value("APP_OWNER"))
                .andExpect(jsonPath("$[?(@.username=='auditor.demo')].roles[0]").value("AUDITOR"))
                .andExpect(jsonPath("$[?(@.username=='isg.officer.demo')].roles[0]").value("ISG_OFFICER"))
                .andExpect(jsonPath("$[?(@.username=='pcidss.auditor.demo')].roles[0]").value("PCIDSS_AUDITOR"))
                .andExpect(jsonPath("$[?(@.username=='dpsc.auditor.demo')].roles[0]").value("DPSC_AUDITOR"));

        mvc.perform(get("/api/v1/admin/users/isg.officer.demo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Demo ISG Officer"));
    }

    @Test
    void createUpdateDeactivateAndDeleteUser() throws Exception {
        mvc.perform(post("/api/v1/admin/users").contentType(MediaType.APPLICATION_JSON).content("""
                {"username":"jane.doe","displayName":"Jane Doe","email":"jane@pramaan.local",
                 "roles":["auditor","viewer","auditor"]}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.roles.length()").value(2))
                .andExpect(jsonPath("$.roles[0]").value("AUDITOR"));

        mvc.perform(put("/api/v1/admin/users/jane.doe").contentType(MediaType.APPLICATION_JSON).content("""
                {"username":"jane.doe","displayName":"Jane D.","roles":["APP_OWNER"]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Jane D."))
                .andExpect(jsonPath("$.roles[0]").value("APP_OWNER"));

        mvc.perform(put("/api/v1/admin/users/jane.doe/active").param("value", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        mvc.perform(delete("/api/v1/admin/users/jane.doe")).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/admin/users/jane.doe")).andExpect(status().isNotFound());
    }

    @Test
    void unknownRoleIsRejected() throws Exception {
        mvc.perform(post("/api/v1/admin/users").contentType(MediaType.APPLICATION_JSON).content("""
                {"username":"bad.user","displayName":"Bad User","roles":["SUPERUSER"]}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidUsernameIsRejected() throws Exception {
        mvc.perform(post("/api/v1/admin/users").contentType(MediaType.APPLICATION_JSON).content("""
                {"username":"Jane Doe","displayName":"Jane","roles":["VIEWER"]}"""))
                .andExpect(status().isBadRequest());
    }
}
