package com.pramaan.backend.common;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ErrorHandlingWebTest {

    @Autowired MockMvc mvc;

    @Test
    void unmappedPathIs404NotServerError() throws Exception {
        // A frontend probing an endpoint a Phase 1 backend does not implement (e.g. /api/v1/agents)
        // must get a 404, so it can fall back to shared mock data instead of surfacing an error.
        mvc.perform(get("/api/v1/agents"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }
}
