package com.pramaan.backend.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/** Wires the Phase 2 AI models and vector store from {@code pramaan.ai.*}. */
@Configuration
@EnableConfigurationProperties(AiProperties.class)
public class AiConfig {

    private static final Logger log = LoggerFactory.getLogger(AiConfig.class);

    @Bean
    ChatModel chatModel(AiProperties props) {
        if (props.live()) {
            requireBaseUrl(props.chatOrDefault().baseUrl(), "pramaan.ai.chat.base-url",
                    "PRAMAAN_AI_CHAT_BASE_URL");
            log.info("AI chat model: live ({})", props.chatOrDefault().providerOrDefault());
            return new HttpChatModel(props.chatOrDefault());
        }
        log.info("AI chat model: deterministic mock (prompt digest, NOT model-generated)");
        return new MockChatModel(12);
    }

    @Bean
    EmbeddingModel embeddingModel(AiProperties props) {
        if (props.live()) {
            requireBaseUrl(props.embeddingOrDefault().baseUrl(), "pramaan.ai.embedding.base-url",
                    "PRAMAAN_AI_EMBED_BASE_URL");
            log.info("AI embedding model: live ({})", props.embeddingOrDefault().providerOrDefault());
            return new HttpEmbeddingModel(props.embeddingOrDefault());
        }
        log.info("AI embedding model: deterministic mock");
        return new MockEmbeddingModel(props.embeddingOrDefault().dimensionOrDefault());
    }

    /**
     * Refuse to start when {@code pramaan.ai.mode=live} but no endpoint is configured.
     *
     * <p>Previously this silently fell back to the deterministic mock, so an operator
     * who asked for a live model got prompt digests labelled as AI output with nothing
     * in the logs to say why. Failing at startup is the only honest option.
     */
    private static void requireBaseUrl(String baseUrl, String property, String envVar) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException(
                    "pramaan.ai.mode=live but " + property + " is not set (env " + envVar + "). "
                            + "Set it, or use pramaan.ai.mode=mock. Refusing to silently "
                            + "downgrade to the deterministic mock model.");
        }
    }

    @Bean
    EmbeddingStore embeddingStore(AiProperties props, EmbeddingModel embeddingModel,
                                  org.springframework.beans.factory.ObjectProvider<JdbcTemplate> jdbc) {
        if ("pgvector".equalsIgnoreCase(props.vectorStore())) {
            JdbcTemplate t = jdbc.getIfAvailable();
            if (t != null) {
                var store = new PgVectorEmbeddingStore(t, embeddingModel.dimension());
                store.ensureSchema();
                log.info("Embedding store: pgvector (dim={})", embeddingModel.dimension());
                return store;
            }
            log.warn("pramaan.ai.vector-store=pgvector but no JdbcTemplate available; using in-memory store");
        }
        log.info("Embedding store: in-memory");
        return new InMemoryEmbeddingStore();
    }
}
