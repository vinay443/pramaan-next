package com.pramaan.backend.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pramaan.backend.ai.AiProperties.Chat;
import com.pramaan.backend.ai.AiProperties.Embedding;
import org.junit.jupiter.api.Test;

/**
 * UC-P2-3: {@code pramaan.ai.mode=live} with no configured endpoint used to fall back
 * to the deterministic mock silently, so an operator got prompt digests presented as
 * AI output. It must fail loudly instead.
 */
class AiConfigTest {

    private final AiConfig config = new AiConfig();

    @Test
    void liveModeWithoutChatBaseUrlRefusesToBuildTheBean() {
        AiProperties props = new AiProperties("live", "memory",
                new Chat("openai", null, "k", "gpt-4o-mini", 30), null);

        assertThatThrownBy(() -> config.chatModel(props))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("pramaan.ai.chat.base-url")
                .hasMessageContaining("Refusing to silently");
    }

    @Test
    void liveModeWithBlankChatBaseUrlAlsoRefuses() {
        AiProperties props = new AiProperties("live", "memory",
                new Chat("openai", "   ", "k", "gpt-4o-mini", 30), null);

        assertThatThrownBy(() -> config.chatModel(props)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void liveModeWithoutEmbeddingBaseUrlRefusesToBuildTheBean() {
        AiProperties props = new AiProperties("live", "memory",
                new Chat("openai", "http://localhost:1234/v1", "k", "gpt-4o-mini", 30),
                new Embedding("openai", null, "k", "text-embedding-3-small", 256));

        assertThatThrownBy(() -> config.embeddingModel(props))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("pramaan.ai.embedding.base-url");
    }

    @Test
    void liveModeWithBaseUrlBuildsTheHttpModelAndMarksItModelGenerated() {
        AiProperties props = new AiProperties("live", "memory",
                new Chat("openai", "http://localhost:1234/v1", "k", "gpt-4o-mini", 30), null);

        ChatModel model = config.chatModel(props);

        assertThat(model).isInstanceOf(HttpChatModel.class);
        assertThat(model.modelGenerated()).isTrue();
        assertThat(model.deterministic()).isFalse();
    }

    @Test
    void mockModeBuildsTheDigestAndMarksItNotModelGenerated() {
        AiProperties props = new AiProperties("mock", "memory", null, null);

        ChatModel model = config.chatModel(props);

        assertThat(model).isInstanceOf(MockChatModel.class);
        assertThat(model.modelGenerated()).isFalse();
        assertThat(model.deterministic()).isTrue();
    }
}
