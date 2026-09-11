package com.pramaan.backend.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.pramaan.backend.ai.AiProperties.Chat;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * Calls a configurable HTTP chat API. Two wire formats are supported:
 *
 * <ul>
 *   <li>{@code openai} — {@code POST {baseUrl}/chat/completions}, Bearer auth</li>
 *   <li>{@code anthropic} — {@code POST {baseUrl}/v1/messages}, x-api-key + anthropic-version</li>
 * </ul>
 *
 * No SDK — plain JSON over {@link RestClient}.
 */
public class HttpChatModel implements ChatModel {

    private final Chat cfg;
    private final RestClient http;

    public HttpChatModel(Chat cfg) {
        this.cfg = cfg;
        Duration timeout = Duration.ofSeconds(cfg.timeoutSeconds() > 0 ? cfg.timeoutSeconds() : 30);
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) timeout.toMillis());
        factory.setReadTimeout((int) timeout.toMillis());
        this.http = RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public String complete(String systemPrompt, String userPrompt) {
        try {
            return "anthropic".equals(cfg.providerOrDefault()) ? anthropic(systemPrompt, userPrompt)
                    : openai(systemPrompt, userPrompt);
        } catch (ResourceAccessException e) {
            throw new AiUnavailableException("chat model (" + name() + ") unreachable at " + cfg.baseUrl(), e);
        }
    }

    private String openai(String system, String user) {
        JsonNode resp = http.post()
                .uri(cfg.baseUrl() + "/chat/completions")
                .header("Authorization", "Bearer " + cfg.apiKey())
                .header("Content-Type", "application/json")
                .body(Map.of(
                        "model", cfg.model(),
                        "temperature", 0,
                        "messages", List.of(
                                Map.of("role", "system", "content", system),
                                Map.of("role", "user", "content", user))))
                .retrieve()
                .body(JsonNode.class);
        return text(resp == null ? null : resp.at("/choices/0/message/content"));
    }

    private String anthropic(String system, String user) {
        JsonNode resp = http.post()
                .uri(cfg.baseUrl() + "/v1/messages")
                .header("x-api-key", cfg.apiKey())
                .header("anthropic-version", "2023-06-01")
                .header("Content-Type", "application/json")
                .body(Map.of(
                        "model", cfg.model(),
                        "max_tokens", 1024,
                        "system", system,
                        "messages", List.of(Map.of("role", "user", "content", user))))
                .retrieve()
                .body(JsonNode.class);
        return text(resp == null ? null : resp.at("/content/0/text"));
    }

    private static String text(JsonNode n) {
        return n == null || n.isMissingNode() || n.isNull() ? "" : n.asText();
    }

    @Override
    public String name() {
        return cfg.providerOrDefault() + ":" + cfg.model();
    }

    @Override
    public boolean deterministic() {
        return false;
    }

    @Override
    public boolean modelGenerated() {
        return true;
    }
}
