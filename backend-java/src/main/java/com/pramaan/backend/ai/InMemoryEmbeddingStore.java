package com.pramaan.backend.ai;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** In-process embedding store with brute-force cosine search. Fine for Phase 1/2 data volumes. */
public class InMemoryEmbeddingStore implements EmbeddingStore {

    private final Map<UUID, Entry> byEvidence = new ConcurrentHashMap<>();

    @Override
    public void upsert(Entry entry) {
        byEvidence.put(entry.evidenceId(), entry);
    }

    @Override
    public void clear() {
        byEvidence.clear();
    }

    @Override
    public long count() {
        return byEvidence.size();
    }

    @Override
    public List<Match> search(float[] query, int limit) {
        return byEvidence.values().stream()
                .map(e -> new Match(e, EmbeddingModel.cosine(query, e.vector())))
                .sorted(Comparator.comparingDouble(Match::score).reversed()
                        .thenComparing(m -> m.entry().evidenceId()))
                .limit(Math.max(1, limit))
                .toList();
    }

    @Override
    public String driver() {
        return "memory";
    }
}
