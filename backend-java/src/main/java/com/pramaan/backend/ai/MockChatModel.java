package com.pramaan.backend.ai;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Deterministic stand-in for a chat model.
 *
 * <p>It does not invent content: it extracts the salient lines from the grounded
 * prompt the caller built (one fact per line, "key: value" or short sentences),
 * de-duplicates, orders them stably, and returns a compact digest. The same
 * prompt always yields the same text, so summaries and NL answers are testable.
 */
public class MockChatModel implements ChatModel {

    private final int maxLines;

    public MockChatModel(int maxLines) {
        this.maxLines = Math.max(3, maxLines);
    }

    @Override
    public String complete(String systemPrompt, String userPrompt) {
        if (userPrompt == null || userPrompt.isBlank()) {
            return "No content was provided to summarize.";
        }
        Set<String> lines = new LinkedHashSet<>();
        for (String raw : userPrompt.split("\\r?\\n")) {
            String s = raw.strip().replaceFirst("^[-*•]\\s*", "");
            if (s.length() < 4 || s.length() > 240) {
                continue;
            }
            if (s.toLowerCase().startsWith("you are ") || s.endsWith(":")) {
                continue; // skip instruction scaffolding / section headers
            }
            lines.add(s);
        }
        List<String> picked = new ArrayList<>(lines).subList(0, Math.min(lines.size(), maxLines));
        StringBuilder sb = new StringBuilder("[mock-ai] ");
        if (picked.isEmpty()) {
            return sb.append(userPrompt.strip(), 0, Math.min(userPrompt.strip().length(), 280)).toString();
        }
        sb.append("Summary based only on the supplied evidence:\n");
        for (String p : picked) {
            sb.append("- ").append(p).append('\n');
        }
        return sb.toString().stripTrailing();
    }

    @Override
    public String name() {
        return "mock-chat:v1";
    }

    @Override
    public boolean deterministic() {
        return true;
    }
}
