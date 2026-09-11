package com.pramaan.backend.dev;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** With demo mode on: the seed populates evidence and is safe to call repeatedly. */
@SpringBootTest(properties = "pramaan.demo-mode=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DemoSeedModeTest {

    @Autowired MockMvc mvc;

    @Test
    void seedIsIdempotentAndLeavesOneRecordShortOfItsFullFrameworkMapping() throws Exception {
        // POST works and returns the seed summary JSON
        mvc.perform(post("/api/v1/dev/seed-demo-evidence").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seeded").value(7))
                .andExpect(jsonPath("$.created").value(7))
                .andExpect(jsonPath("$.duplicates").value(0));

        // GET on the same (POST-only) path is a clean 405, not a 500
        mvc.perform(get("/api/v1/dev/seed-demo-evidence"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", org.hamcrest.Matchers.containsString("POST")));

        // a second call creates nothing new
        mvc.perform(post("/api/v1/dev/seed-demo-evidence"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(0))
                .andExpect(jsonPath("$.duplicates").value(7));

        // spread across the seeded applications
        mvc.perform(get("/api/v1/evidence").param("applicationSlug", "mobile-banking"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(org.hamcrest.Matchers.greaterThanOrEqualTo(2)));

        // the payments / DB-TLS-IN-TRANSIT record is tagged to PCI_DSS only (reuse has work to do)
        mvc.perform(get("/api/v1/evidence")
                        .param("applicationSlug", "payments").param("controlId", "DB-TLS-IN-TRANSIT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].tags.frameworks").value("PCI_DSS"))
                .andExpect(jsonPath("$.items[0].tags.collectionMethod").value("demo-seed"));

        // by-control reuse view now surfaces the frameworks still to add
        mvc.perform(get("/api/v1/insight/reuse/by-control").param("controlId", "DB-TLS-IN-TRANSIT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.frameworks").value(org.hamcrest.Matchers.hasItems("DPSC", "ISO27001")));
    }
}
