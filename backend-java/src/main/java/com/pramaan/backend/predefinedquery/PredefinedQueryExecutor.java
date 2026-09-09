package com.pramaan.backend.predefinedquery;

import java.time.Instant;
import java.util.Map;

/**
 * Seam for running a predefined technical query against a target. Swapping real
 * SSH / docker-exec / JDBC execution in later is a matter of providing another
 * implementation (see {@link LivePredefinedQueryExecutor}); nothing else changes.
 */
public interface PredefinedQueryExecutor {

    /** SIMULATED | LIVE — surfaced on results so callers know what produced the evidence. */
    String mode();

    /**
     * Run one catalogue entry for one application and return the raw collected
     * output. The caller ({@code PredefinedQueryService}) turns this into an
     * evidence record through the canonical {@code EvidenceIngestionService.ingest()}.
     */
    Output execute(PredefinedQuery query, String applicationSlug, Instant asOf);

    /** Raw output of a query run, before ingestion. */
    record Output(String content, String contentType,
                  Map<String, String> metadata, Map<String, String> tags) {}
}
