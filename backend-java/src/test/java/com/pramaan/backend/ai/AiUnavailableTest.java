package com.pramaan.backend.ai;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pramaan.backend.ai.AiProperties.Chat;
import com.pramaan.backend.ai.AiProperties.Embedding;
import org.junit.jupiter.api.Test;

/**
 * A live-mode AI/embedding endpoint that is unreachable (connection refused) must
 * surface as {@link AiUnavailableException}, not a generic RestClient failure — see
 * {@code com.pramaan.backend.common.GlobalExceptionHandlerTest} for the HTTP mapping
 * (503 + {@code aiUnavailable: true}) that this feeds.
 */
class AiUnavailableTest {

    /** Port 1 is a reserved/privileged port nothing listens on — connection refused. */
    private static final String UNREACHABLE = "http://localhost:1";

    @Test
    void unreachableChatEndpointThrowsAiUnavailableNotAGenericFailure() {
        HttpChatModel model = new HttpChatModel(new Chat("openai", UNREACHABLE, "k", "gpt-4o-mini", 2));

        assertThatThrownBy(() -> model.complete("system", "user"))
                .isInstanceOf(AiUnavailableException.class)
                .hasMessageContaining(UNREACHABLE);
    }

    @Test
    void unreachableEmbeddingEndpointThrowsAiUnavailableNotAGenericFailure() {
        HttpEmbeddingModel model = new HttpEmbeddingModel(new Embedding("openai", UNREACHABLE, "k", "text-embedding-3-small", 256));

        assertThatThrownBy(() -> model.embed("some text"))
                .isInstanceOf(AiUnavailableException.class)
                .hasMessageContaining(UNREACHABLE);
    }
}
