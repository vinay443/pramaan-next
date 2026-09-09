package com.pramaan.backend.ai;

/** Turns text into a dense vector for similarity search. */
public interface EmbeddingModel {

    /** Embed one text. Returned vector length equals {@link #dimension()}. */
    float[] embed(String text);

    int dimension();

    /** Identifier for provenance, e.g. {@code mock-embed:v1} or {@code openai:text-embedding-3-small}. */
    String name();

    /** Cosine similarity in [-1, 1]; 1.0 == identical direction. */
    static double cosine(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length) {
            return 0.0;
        }
        double dot = 0;
        double na = 0;
        double nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
            na += (double) a[i] * a[i];
            nb += (double) b[i] * b[i];
        }
        if (na == 0 || nb == 0) {
            return 0.0;
        }
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }
}
