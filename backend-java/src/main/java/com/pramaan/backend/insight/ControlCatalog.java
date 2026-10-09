package com.pramaan.backend.insight;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/**
 * The Phase 2 expected-control catalog, loaded from
 * {@code classpath:phase2/control-catalog.json}. Pure reference data — completeness
 * and compliance compare stored evidence against this list.
 */
@Component
public class ControlCatalog {

    /**
     * {@code description} is the verbatim ECS Control Library description for
     * {@code "source": "xlsx"} entries; legacy entries (and any older file) omit it.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Expectation(String framework, String controlId, String title, List<String> appliesTo,
                              String source, String description) {
        boolean coversApplication(String slug) {
            return appliesTo == null || appliesTo.isEmpty()
                    || appliesTo.contains("*") || appliesTo.contains(slug);
        }

        /** True for the 133 controls imported from docs/ECS_Control_Library.xlsx. */
        public boolean fromLibrary() {
            return "xlsx".equalsIgnoreCase(source);
        }
    }

    private record CatalogFile(List<Expectation> expectations) {}

    private final List<Expectation> expectations;

    public ControlCatalog(ObjectMapper mapper,
                          @Value("classpath:phase2/control-catalog.json") Resource resource) {
        try (InputStream in = resource.getInputStream()) {
            this.expectations = List.copyOf(mapper.readValue(in, CatalogFile.class).expectations());
        } catch (IOException e) {
            throw new UncheckedIOException("cannot load phase2/control-catalog.json", e);
        }
    }

    public List<Expectation> all() {
        return expectations;
    }

    /**
     * One control: the exact (framework, controlId) entry when present, otherwise the first
     * entry carrying that control id (ids are globally distinct in the catalog).
     */
    public Optional<Expectation> find(String framework, String controlId) {
        if (controlId == null || controlId.isBlank()) {
            return Optional.empty();
        }
        String id = controlId.trim();
        String fw = framework == null ? "" : framework.trim();
        Optional<Expectation> exact = expectations.stream()
                .filter(e -> e.controlId().equalsIgnoreCase(id) && e.framework().equalsIgnoreCase(fw))
                .findFirst();
        return exact.isPresent() ? exact : expectations.stream()
                .filter(e -> e.controlId().equalsIgnoreCase(id))
                .findFirst();
    }

    /** Distinct frameworks whose catalog references the given control id. */
    public List<String> frameworksForControl(String controlId) {
        if (controlId == null || controlId.isBlank()) {
            return List.of();
        }
        return expectations.stream()
                .filter(e -> e.controlId().equalsIgnoreCase(controlId.trim()))
                .map(Expectation::framework)
                .distinct()
                .sorted()
                .toList();
    }

    /** Expected (framework, control) pairs for one application, optionally narrowed to a framework. */
    public List<Expectation> forApplication(String slug, String framework) {
        return expectations.stream()
                .filter(e -> e.coversApplication(slug))
                .filter(e -> framework == null || framework.isBlank()
                        || e.framework().equalsIgnoreCase(framework))
                .toList();
    }
}
