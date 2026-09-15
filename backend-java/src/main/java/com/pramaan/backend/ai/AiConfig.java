package com.pramaan.backend.ai;

import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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

    /**
     * A separate connection pool to the {@code pgvector} container/DB
     * ({@code pramaan_vectors}), distinct from the app's primary {@code
     * spring.datasource} (plain PostgreSQL, no {@code vector} extension).
     *
     * <p>{@code @ConditionalOnProperty}, not just an {@code if} at the call site: any
     * unconditionally-registered {@link javax.sql.DataSource} bean gets picked up by
     * Spring Boot's datasource health indicator, which eagerly opens a connection at
     * context startup regardless of whether anything actually uses the bean — so
     * without this the pool would try to reach the pgvector container (and fail
     * outside option D, e.g. in tests or the L/R modes) even with {@code
     * vector-store=memory}.
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(prefix = "pramaan.ai", name = "vector-store", havingValue = "pgvector")
    HikariDataSource pgVectorDataSource(AiProperties props) {
        AiProperties.PgVector cfg = props.pgvectorOrDefault();
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(cfg.urlOrDefault());
        ds.setUsername(cfg.usernameOrDefault());
        ds.setPassword(cfg.passwordOrDefault());
        ds.setPoolName("pgvector-pool");
        ds.setMaximumPoolSize(5);
        return ds;
    }

    @Bean
    EmbeddingStore embeddingStore(AiProperties props, EmbeddingModel embeddingModel,
                                  @Autowired(required = false) HikariDataSource pgVectorDataSource) {
        if ("pgvector".equalsIgnoreCase(props.vectorStore())) {
            if (pgVectorDataSource != null) {
                var store = new PgVectorEmbeddingStore(new JdbcTemplate(pgVectorDataSource), embeddingModel.dimension());
                store.ensureSchema();
                log.info("Embedding store: pgvector (dim={}, url={})",
                        embeddingModel.dimension(), props.pgvectorOrDefault().urlOrDefault());
                return store;
            }
            log.warn("pramaan.ai.vector-store=pgvector but pgvector datasource unavailable; using in-memory store");
        }
        log.info("Embedding store: in-memory");
        return new InMemoryEmbeddingStore();
    }
}
