package com.pramaan.backend.predefinedquery;

import java.time.Instant;

/**
 * One technology's real execution path for {@code LIVE} mode. The dispatcher
 * ({@link LivePredefinedQueryExecutor}) tries each registered instance in turn;
 * whichever first claims a catalogue entry (via {@link #supports}) executes it
 * for real. Any entry nothing here claims falls back to
 * {@link SimulatedPredefinedQueryExecutor}, tagged {@code executionMode=SIMULATED_FALLBACK}.
 */
interface TechnologyLiveExecutor {

    /** Whether this executor can run the given catalogue entry for real right now. */
    boolean supports(PredefinedQuery query);

    /**
     * Execute a control this instance has already claimed via {@link #supports}.
     * Must return output tagged {@code live=true} / {@code executionMode=LIVE}, or
     * throw so the caller can surface a genuine execution failure — never silently
     * substitute simulated content.
     */
    PredefinedQueryExecutor.Output execute(PredefinedQuery query, String applicationSlug, Instant asOf);
}
