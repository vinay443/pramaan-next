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
    void seedIsIdempotentAndCoversEveryXlsxFramework() throws Exception {
        // POST works and returns the seed summary JSON
        mvc.perform(post("/api/v1/dev/seed-demo-evidence").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seeded").value(12))
                .andExpect(jsonPath("$.created").value(12))
                .andExpect(jsonPath("$.duplicates").value(0));

        // GET on the same (POST-only) path is a clean 405, not a 500
        mvc.perform(get("/api/v1/dev/seed-demo-evidence"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", org.hamcrest.Matchers.containsString("POST")));

        // a second call creates nothing new
        mvc.perform(post("/api/v1/dev/seed-demo-evidence"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(0))
                .andExpect(jsonPath("$.duplicates").value(12));

        // spread across the seeded applications
        mvc.perform(get("/api/v1/evidence").param("applicationSlug", "mobile-banking"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(org.hamcrest.Matchers.greaterThanOrEqualTo(2)));

        // every record is an xlsx bank-catalog control, e.g. payments/DBBL-C8 (DB Baselining)
        mvc.perform(get("/api/v1/evidence")
                        .param("applicationSlug", "payments").param("controlId", "DBBL-C8"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].tags.frameworks").value("DB_BASELINING"))
                .andExpect(jsonPath("$.items[0].tags.collectionMethod").value("demo-seed"));

        // a framework the old legacy-ID seed never touched (zero coverage before this change)
        // now has evidence too
        mvc.perform(get("/api/v1/evidence")
                        .param("applicationSlug", "net-banking").param("controlId", "VAPT-C1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].tags.frameworks").value("VAPT"));
    }
}
