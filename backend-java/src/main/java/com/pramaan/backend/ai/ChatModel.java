package com.pramaan.backend.ai;

/** A text-generation model. Implementations must be side-effect free w.r.t. Pramaan data. */
public interface ChatModel {

    /**
     * Generate a completion for the given system + user prompt.
     *
     * @return the model's text; never null (may be a deterministic fallback line)
     */
    String complete(String systemPrompt, String userPrompt);

    /** Identifier for provenance stamping, e.g. {@code mock:v1} or {@code openai:gpt-4o-mini}. */
    String name();

    /** True when this model is deterministic (mock). Used to mark AI output as simulated. */
    boolean deterministic();
}
