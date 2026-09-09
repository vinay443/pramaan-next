package com.pramaan.backend.predefinedquery;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * One entry of the predefined technical-query catalogue
 * ({@code classpath:predefined-queries/catalog.json}).
 *
 * <p>{@code executableNow} / {@code runtimeStatus} are carried through from the
 * original ECS assessment for reference only — they do NOT gate execution here.
 * Everything runs in simulated mode (see {@link PredefinedQueryExecutor}).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PredefinedQuery(
        String controlId,
        String technology,
        String controlName,
        String command,
        List<String> frameworks,
        String evidenceType,
        boolean executableNow,
        String runtimeStatus,
        String controlFamily) {

    public List<String> frameworksOrEmpty() {
        return frameworks == null ? List.of() : frameworks;
    }
}
