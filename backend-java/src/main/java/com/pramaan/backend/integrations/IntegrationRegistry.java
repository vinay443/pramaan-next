package com.pramaan.backend.integrations;

import com.pramaan.backend.common.ApiException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Looks up registered {@link EnterpriseIntegration} beans by source-system id. */
@Component
public class IntegrationRegistry {

    private final Map<String, EnterpriseIntegration> bySource;

    public IntegrationRegistry(List<EnterpriseIntegration> integrations) {
        this.bySource = integrations.stream().collect(Collectors.toUnmodifiableMap(
                i -> i.sourceSystem().toUpperCase(), Function.identity()));
    }

    public List<String> available() {
        return bySource.keySet().stream().sorted().toList();
    }

    public EnterpriseIntegration require(String sourceSystem) {
        EnterpriseIntegration i = bySource.get(sourceSystem == null ? "" : sourceSystem.toUpperCase());
        if (i == null) {
            throw ApiException.badRequest("unknown integration source: " + sourceSystem
                    + " (available: " + available() + ")");
        }
        return i;
    }
}
