package com.pramaan.backend.ai;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Deterministic embedding: a bag-of-tokens hashing vectorizer.
 *
 * <p>The same text always maps to the same vector, and texts that share tokens
 * (same control id, same framework, same technology keywords) land close together
 * under cosine similarity — enough to demonstrate evidence reuse without any
 * external model.
 */
public class MockEmbeddingModel implements EmbeddingModel {

    private static final Pattern TOKEN = Pattern.compile("[a-z0-9]+");

    /**
     * Structural / formatting tokens carried by stored evidence (JSON field names,
     * the {@code key=value} framing added by the indexer) plus a few English
     * function words. Stripping them symmetrically from every text keeps a stored
     * JSON record and a plain-English paraphrase of the same control close under
     * cosine similarity instead of being swamped by field-name noise that a
     * free-text query never contains.
     */
    private static final Set<String> STOP = Set.of(
            "application", "framework", "control", "controlid", "source", "sourcesystem",
            "system", "findings", "finding", "checkid", "check", "status", "evidence",
            "metadata", "description", "id", "name", "value", "type", "data", "result",
            "results", "details", "field", "record", "json",
            "and", "are", "for", "from", "has", "its", "not", "the", "that", "this",
            "was", "were", "with", "into", "over", "per");

    private final int dim;

    public MockEmbeddingModel(int dimension) {
        this.dim = Math.max(16, dimension);
    }

    @Override
    public float[] embed(String text) {
        float[] v = new float[dim];
        if (text == null || text.isBlank()) {
            v[0] = 1f;
            return v;
        }
        var m = TOKEN.matcher(text.toLowerCase(Locale.ROOT));
        while (m.find()) {
            String tok = m.group();
            if (tok.length() < 2 || STOP.contains(tok)) {
                continue;
            }
            int h = bucket(tok);
            // signed contribution keeps opposite tokens from always reinforcing
            v[h] += ((tok.hashCode() & 1) == 0) ? 1f : -1f;
        }
        normalize(v);
        return v;
    }

    @Override
    public int dimension() {
        return dim;
    }

    @Override
    public String name() {
        return "mock-embed:v1(dim=" + dim + ")";
    }

    private int bucket(String token) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            int x = ((d[0] & 0xff) << 16) | ((d[1] & 0xff) << 8) | (d[2] & 0xff);
            return Math.floorMod(x, dim);
        } catch (NoSuchAlgorithmException e) {
            return Math.floorMod(token.hashCode(), dim);
        }
    }

    private static void normalize(float[] v) {
        double n = 0;
        for (float x : v) {
            n += (double) x * x;
        }
        if (n == 0) {
            v[0] = 1f;
            return;
        }
        double inv = 1.0 / Math.sqrt(n);
        for (int i = 0; i < v.length; i++) {
            v[i] = (float) (v[i] * inv);
        }
    }
}
