package com.pramaan.backend.predefinedquery;

import static org.assertj.core.api.Assertions.assertThat;

import com.pramaan.backend.predefinedquery.PredefinedQueryExecutor.Output;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Dispatcher-only tests — routing to whichever {@link TechnologyLiveExecutor}
 * claims a control, and the SIMULATED_FALLBACK path when none do. Technology-specific
 * behavior lives in {@link PostgresLiveExecutorTest} / {@link AerospikeLiveExecutorTest}.
 */
class LivePredefinedQueryExecutorTest {

    private static final Instant NOW = Instant.parse("2026-09-15T00:00:00Z");

    private static PredefinedQuery control(String controlId, String technology) {
        return new PredefinedQuery(controlId, technology, "Some control", "do-the-thing",
                List.of("CSITE"), "Command Output", true, "Ready", "Secure Configuration");
    }

    /** Fake executor that claims one fixed technology and returns a distinctive marker output. */
    private static TechnologyLiveExecutor fakeLiveExecutorFor(String technology) {
        return new TechnologyLiveExecutor() {
            @Override
            public boolean supports(PredefinedQuery q) {
                return technology.equals(q.technology());
            }

            @Override
            public Output execute(PredefinedQuery q, String applicationSlug, Instant asOf) {
                Map<String, String> metadata = new LinkedHashMap<>();
                metadata.put("live", "true");
                metadata.put("executionMode", "LIVE");
                metadata.put("source", technology + "-fake");
                return new Output("fake-live-output", "text/plain", metadata, Map.of());
            }
        };
    }

    @Test
    void dispatchesToTheExecutorThatClaimsTheControl() {
        LivePredefinedQueryExecutor dispatcher = new LivePredefinedQueryExecutor(
                List.of(fakeLiveExecutorFor("PostgreSQL"), fakeLiveExecutorFor("Aerospike")));

        Output out = dispatcher.execute(control("ASX-001", "Aerospike"), "net-banking", NOW);

        assertThat(out.content()).isEqualTo("fake-live-output");
        assertThat(out.metadata()).containsEntry("source", "Aerospike-fake");
        assertThat(out.metadata()).containsEntry("executionMode", "LIVE");
    }

    @Test
    void firstMatchingExecutorWinsWhenMultipleAreRegistered() {
        LivePredefinedQueryExecutor dispatcher = new LivePredefinedQueryExecutor(
                List.of(fakeLiveExecutorFor("Aerospike"), fakeLiveExecutorFor("Aerospike")));

        Output out = dispatcher.execute(control("ASX-002", "Aerospike"), "net-banking", NOW);

        assertThat(out.metadata()).containsEntry("source", "Aerospike-fake");
    }

    @Test
    void fallsBackToSimulatedWhenNoRegisteredExecutorClaimsTheControl() {
        LivePredefinedQueryExecutor dispatcher = new LivePredefinedQueryExecutor(
                List.of(fakeLiveExecutorFor("PostgreSQL")));

        Output out = dispatcher.execute(control("LNX-007", "Linux"), "net-banking", NOW);

        assertThat(out.metadata()).containsEntry("live", "false");
        assertThat(out.metadata()).containsEntry("executionMode", "SIMULATED_FALLBACK");
        assertThat(out.tags()).containsEntry("live", "false");
        assertThat(out.tags()).containsEntry("executionMode", "SIMULATED_FALLBACK");
    }

    @Test
    void fallsBackToSimulatedWithNoLiveExecutorsRegisteredAtAll() {
        LivePredefinedQueryExecutor dispatcher = new LivePredefinedQueryExecutor(List.of());

        Output out = dispatcher.execute(control("DB-001", "PostgreSQL"), "net-banking", NOW);

        assertThat(out.metadata()).containsEntry("executionMode", "SIMULATED_FALLBACK");
    }
}
