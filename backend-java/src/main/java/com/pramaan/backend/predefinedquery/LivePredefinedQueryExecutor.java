package com.pramaan.backend.predefinedquery;

import java.time.Instant;

/**
 * Stub for real execution (SSH / docker-exec / JDBC against the target host or
 * container). Not implemented — activating it is the ONLY change needed to run
 * predefined queries for real: implement {@link #execute} and set
 * {@code pramaan.predefined-queries.mode=LIVE}.
 */
public class LivePredefinedQueryExecutor implements PredefinedQueryExecutor {

    @Override
    public String mode() {
        return "LIVE";
    }

    @Override
    public Output execute(PredefinedQuery query, String applicationSlug, Instant asOf) {
        throw new UnsupportedOperationException(
                "Live predefined-query execution is not implemented — requires target connectivity "
                        + "(SSH / docker-exec / JDBC to the host or container). "
                        + "Set pramaan.predefined-queries.mode=SIMULATED, or implement LivePredefinedQueryExecutor.");
    }
}
