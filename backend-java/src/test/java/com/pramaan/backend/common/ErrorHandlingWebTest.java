package com.pramaan.backend.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pramaan.backend.ai.AiUnavailableException;
import com.pramaan.backend.common.GlobalExceptionHandler.AiErrorBody;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

    /**
     * An unreachable configured AI/embedding endpoint (e.g. Ollama down) must surface
     * as a distinct 503 with {@code aiUnavailable: true}, not a generic 500 — so the
     * frontend can tell "AI service down" apart from "backend down" (see api/endpoints.ts).
     */
    @Test
    void aiUnavailableExceptionMapsToA503DistinctFromAGenericServerError() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        ResponseEntity<AiErrorBody> resp = handler.handleAiUnavailable(
                new AiUnavailableException("chat model unreachable", new RuntimeException("Connection refused")));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().aiUnavailable()).isTrue();
        assertThat(resp.getBody().status()).isEqualTo(503);
        assertThat(resp.getBody().message()).contains("AI/embedding service is unreachable");
    }
}
