package com.pramaan.backend.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.pramaan.backend.ai.AiProperties.Embedding;
import java.util.Map;
import org.springframework.web.client.RestClient;

/**
 * Calls an OpenAI-compatible embeddings API: {@code POST {baseUrl}/embeddings},
 * Bearer auth, response {@code data[0].embedding}. Plain JSON over {@link RestClient}.
 */
public class HttpEmbeddingModel implements EmbeddingModel {

    private final Embedding cfg;
    private final RestClient http;

    public HttpEmbeddingModel(Embedding cfg) {
        this.cfg = cfg;
        this.http = RestClient.builder().build();
    }

    @Override
    public float[] embed(String text) {
        JsonNode resp = http.post()
                .uri(cfg.baseUrl() + "/embeddings")
                .header("Authorization", "Bearer " + cfg.apiKey())
                .header("Content-Type", "application/json")
                .body(Map.of("model", cfg.model(), "input", text == null ? "" : text))
                .retrieve()
                .body(JsonNode.class);
        JsonNode arr = resp == null ? null : resp.at("/data/0/embedding");
        if (arr == null || !arr.isArray()) {
            throw new IllegalStateException("embeddings API returned no vector");
        }
        float[] v = new float[arr.size()];
        for (int i = 0; i < arr.size(); i++) {
            v[i] = (float) arr.get(i).asDouble();
        }
        return v;
    }

    @Override
    public int dimension() {
        return cfg.dimensionOrDefault();
    }

    @Override
    public String name() {
        return cfg.providerOrDefault() + ":" + cfg.model();
    }
}
