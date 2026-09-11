package com.pramaan.backend.integrations.grc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class GrcSyncControllerWebTest {

    @Autowired MockMvc mvc;

    @Test
    void statusStartsUnsyncedThenSyncReportsAMockedPush() throws Exception {
        mvc.perform(get("/api/v1/grc/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.everSynced").value(false))
                .andExpect(jsonPath("$.mock").value(true));

        mvc.perform(post("/api/v1/grc/sync"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.everSynced").value(true))
                .andExpect(jsonPath("$.lastOutcome").value("SUCCESS"))
                .andExpect(jsonPath("$.mock").value(true))
                .andExpect(jsonPath("$.externalReference").isNotEmpty());

        mvc.perform(get("/api/v1/grc/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.everSynced").value(true));
    }
}
