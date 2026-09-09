package com.pramaan.backend.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/**
 * Use Case 12 — configuration-driven multi-application onboarding. The set of
 * applications to onboard (identity, metadata, in-scope frameworks and evidence
 * sources) is declared in {@code classpath:phase2/onboarding.json}; overridable
 * with {@code pramaan.onboarding.catalog}.
 */
@Component
public class OnboardingCatalog {

    public record OnboardingEntry(
            String slug,
            String name,
            String businessUnit,
            String criticality,
            String owner,
            List<String> technology,
            List<String> frameworks,
            List<String> sources) {}

    private record CatalogFile(List<OnboardingEntry> applications) {}

    private final List<OnboardingEntry> entries;

    public OnboardingCatalog(ObjectMapper mapper,
                             @Value("${pramaan.onboarding.catalog:classpath:phase2/onboarding.json}")
                             Resource resource) {
        try (InputStream in = resource.getInputStream()) {
            this.entries = List.copyOf(mapper.readValue(in, CatalogFile.class).applications());
        } catch (IOException e) {
            throw new UncheckedIOException("cannot load onboarding catalog: " + resource, e);
        }
    }

    public List<OnboardingEntry> entries() {
        return entries;
    }
}
