package com.pramaan.backend.insight;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
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

    public record Expectation(String framework, String controlId, String title, List<String> appliesTo) {
        boolean coversApplication(String slug) {
            return appliesTo == null || appliesTo.isEmpty()
                    || appliesTo.contains("*") || appliesTo.contains(slug);
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
