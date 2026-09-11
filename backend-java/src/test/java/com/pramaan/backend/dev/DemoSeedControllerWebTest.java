package com.pramaan.backend.dev;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Demo mode off (the default): the controller is not registered, so the path is
 * a genuine 404 for every verb — no fake routing-404 from inside a handler.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DemoSeedControllerWebTest {

    @Autowired MockMvc mvc;

    @Test
    void seedPathIsGenuinelyUnmappedWhenDemoModeIsOff() throws Exception {
        mvc.perform(post("/api/v1/dev/seed-demo-evidence")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/dev/seed-demo-evidence")).andExpect(status().isNotFound());
    }
}
