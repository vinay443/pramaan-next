package com.pramaan.backend.predefinedquery;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Dispatches each catalogue entry to the first {@link TechnologyLiveExecutor}
 * that claims it (see {@link PostgresLiveExecutor}, {@link AerospikeLiveExecutor}).
 * Anything no registered executor claims falls back to
 * {@link SimulatedPredefinedQueryExecutor} output, explicitly tagged
 * {@code executionMode=SIMULATED_FALLBACK} so a genuine live run
 * ({@code executionMode=LIVE}) is never confused with fallback data.
 */
public class LivePredefinedQueryExecutor implements PredefinedQueryExecutor {

    private final List<TechnologyLiveExecutor> liveExecutors;
    private final PredefinedQueryExecutor fallback = new SimulatedPredefinedQueryExecutor();

    public LivePredefinedQueryExecutor(List<TechnologyLiveExecutor> liveExecutors) {
        this.liveExecutors = liveExecutors;
    }

    @Override
    public String mode() {
        return "LIVE";
    }

    @Override
    public boolean supportsLive(PredefinedQuery q) {
        return liveExecutors.stream().anyMatch(e -> e.supports(q));
    }

    @Override
    public Output execute(PredefinedQuery q, String applicationSlug, Instant asOf) {
        for (TechnologyLiveExecutor live : liveExecutors) {
            if (live.supports(q)) {
                return live.execute(q, applicationSlug, asOf);
            }
        }
        return simulatedFallback(q, applicationSlug, asOf);
    }

    private Output simulatedFallback(PredefinedQuery q, String applicationSlug, Instant asOf) {
        Output sim = fallback.execute(q, applicationSlug, asOf);

        Map<String, String> metadata = new LinkedHashMap<>(sim.metadata());
        metadata.put("live", "false");
        metadata.put("executionMode", "SIMULATED_FALLBACK");

        Map<String, String> tags = new LinkedHashMap<>(sim.tags());
        tags.put("live", "false");
        tags.put("executionMode", "SIMULATED_FALLBACK");

        return new Output(sim.content(), sim.contentType(), metadata, tags);
    }
}
