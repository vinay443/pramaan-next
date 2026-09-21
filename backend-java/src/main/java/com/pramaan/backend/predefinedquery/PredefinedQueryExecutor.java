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
     * Whether this executor can actually run {@code query} for real right now (as
     * opposed to falling back to SIMULATED_FALLBACK). Used only to report an
     * honest ECS status in the catalogue listing ({@code PredefinedQueryService#list}) —
     * it must stay a cheap, local check (no I/O, no live connection attempt), since
     * it's called once per catalogue row on every listing request. Defaults to
     * {@code false}: in SIMULATED mode nothing is "live", so the catalogue's static
     * legacy {@code runtimeStatus}/{@code executableNow} is shown as-is.
     */
    default boolean supportsLive(PredefinedQuery query) {
        return false;
    }

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
