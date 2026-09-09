package com.pramaan.backend.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code pramaan.ai.*} — configuration for Phase 2 AI features.
 *
 * <p>{@code mode=mock} (the default) uses deterministic in-process models, so the
 * whole stack runs and tests pass with no external model API. {@code mode=live}
 * calls the configured HTTP model APIs. No Python, no SDK — plain HTTP from Java.
 */
@ConfigurationProperties(prefix = "pramaan.ai")
public record AiProperties(
        String mode,
        String vectorStore,
        Chat chat,
        Embedding embedding) {

    public AiProperties {
        if (mode == null || mode.isBlank()) {
            mode = "mock";
        }
        if (vectorStore == null || vectorStore.isBlank()) {
            vectorStore = "memory";
        }
    }

    public boolean live() {
        return "live".equalsIgnoreCase(mode);
    }

    public Chat chatOrDefault() {
        return chat != null ? chat : new Chat(null, null, null, null, 0);
    }

    public Embedding embeddingOrDefault() {
        return embedding != null ? embedding : new Embedding(null, null, null, null, 0);
    }

    /** Chat / text-generation model (evidence summaries, NL-query narration). */
    public record Chat(String provider, String baseUrl, String apiKey, String model, int timeoutSeconds) {
        public String providerOrDefault() {
            return provider == null || provider.isBlank() ? "openai" : provider.toLowerCase();
        }
    }

    /** Embedding model (evidence reuse / similarity). */
    public record Embedding(String provider, String baseUrl, String apiKey, String model, int dimension) {
        public int dimensionOrDefault() {
            return dimension > 0 ? dimension : 256;
        }

        public String providerOrDefault() {
            return provider == null || provider.isBlank() ? "openai" : provider.toLowerCase();
        }
    }
}
