package com.pramaan.backend.ai;

import java.util.List;
import java.util.UUID;

/**
 * Storage + nearest-neighbour search for evidence embeddings.
 *
 * <p>{@link InMemoryEmbeddingStore} is the default (dev / tests). {@link PgVectorEmbeddingStore}
 * uses PostgreSQL + pgvector and is selected with {@code pramaan.ai.vector-store=pgvector}.
 */
public interface EmbeddingStore {

    record Entry(UUID evidenceId, String applicationSlug, String framework, String controlId,
                 String sha256, float[] vector) {}

    record Match(Entry entry, double score) {}

    /** Insert or replace the embedding for one evidence record. */
    void upsert(Entry entry);

    /** Remove every stored embedding (used before a full re-index). */
    void clear();

    long count();

    /** The {@code limit} entries most similar to {@code query} (cosine), highest score first. */
    List<Match> search(float[] query, int limit);

    String driver();
}
