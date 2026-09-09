package com.pramaan.backend.insight;

import com.pramaan.backend.ai.EmbeddingModel;
import com.pramaan.backend.ai.EmbeddingStore;
import com.pramaan.backend.evidence.EvidenceQueryService;
import com.pramaan.backend.evidence.EvidenceQueryService.EvidenceFilter;
import com.pramaan.backend.evidence.domain.EvidenceRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Builds and refreshes evidence embeddings in the {@link EmbeddingStore}. */
@Component
public class EvidenceEmbeddingIndexer {

    private static final Logger log = LoggerFactory.getLogger(EvidenceEmbeddingIndexer.class);
    private static final int MAX_CHARS = 4000;

    private final EvidenceQueryService evidence;
    private final EmbeddingModel model;
    private final EmbeddingStore store;

    /** Evidence-record count at the last full reindex; drives stale-index detection. */
    private volatile long indexedAtCount = -1;

    public EvidenceEmbeddingIndexer(EvidenceQueryService evidence, EmbeddingModel model, EmbeddingStore store) {
        this.evidence = evidence;
        this.model = model;
        this.store = store;
    }

    /** Rebuild every embedding from scratch. Returns the number indexed. */
    @Transactional(readOnly = true)
    public int reindex() {
        store.clear();
        int n = 0;
        for (EvidenceRecord r : allRecords()) {
            if (indexOne(r)) {
                n++;
            }
        }
        indexedAtCount = evidence.count();
        log.info("reindexed {} evidence embeddings ({}, {})", n, model.name(), store.driver());
        return n;
    }

    /**
     * Ensure the index reflects the current repository before a retrieval. Reindexes
     * on a cold store <b>and</b> whenever evidence has been ingested since the last
     * reindex — otherwise a scheduler run or bulk upload after the first query would
     * leave those records unsearchable ({@code retrieved: []} on every free-text query).
     */
    @Transactional(readOnly = true)
    public void ensureIndexed() {
        if (store.count() == 0 || evidence.count() > indexedAtCount) {
            reindex();
        }
    }

    boolean indexOne(EvidenceRecord r) {
        String content = evidence.latestContentText(r.getId()).orElse(null);
        if (content == null) {
            return false;
        }
        store.upsert(new EmbeddingStore.Entry(r.getId(), r.getApplicationSlug(), r.getFramework(),
                r.getControlId(), r.getLatestSha256(), model.embed(embedText(r, content))));
        return true;
    }

    /**
     * The text actually embedded: control identity plus a bounded slice of content.
     *
     * <p>Only the control id and framework are prepended. The application slug and
     * source-system name are deliberately left out: they are low-signal metadata
     * that a free-text query never contains, and including them made stored
     * evidence vectors drift away from plain-English paraphrases of the same
     * control (see {@code EvidenceReuseService.similarToText}).
     */
    static String embedText(EvidenceRecord r, String content) {
        String body = content.length() > MAX_CHARS ? content.substring(0, MAX_CHARS) : content;
        return "control=" + r.getControlId()
                + " framework=" + r.getFramework()
                + "\n" + body;
    }

    private Iterable<EvidenceRecord> allRecords() {
        return evidence.recordsMatching(new EvidenceFilter(null, null, null, null, null, null, null, 0, 100_000));
    }
}
