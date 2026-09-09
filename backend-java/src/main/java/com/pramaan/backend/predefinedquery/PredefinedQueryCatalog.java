package com.pramaan.backend.predefinedquery;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pramaan.backend.common.ApiException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/**
 * Loads and exposes the predefined technical-query catalogue
 * ({@code classpath:predefined-queries/catalog.json}). Reference data only — mirrors
 * the {@code ControlCatalog} / {@code ControlFrameworkCatalog} pattern; overridable
 * with {@code pramaan.predefined-queries.catalog}.
 */
@Component
public class PredefinedQueryCatalog {

    private final List<PredefinedQuery> entries;

    public PredefinedQueryCatalog(ObjectMapper mapper,
                                  @Value("${pramaan.predefined-queries.catalog:classpath:predefined-queries/catalog.json}")
                                  Resource resource) {
        try (InputStream in = resource.getInputStream()) {
            this.entries = List.of(mapper.readValue(in, PredefinedQuery[].class));
        } catch (IOException e) {
            throw new UncheckedIOException("cannot load predefined-queries/catalog.json", e);
        }
    }

    public List<PredefinedQuery> all() {
        return entries;
    }

    public PredefinedQuery require(String controlId) {
        return entries.stream()
                .filter(q -> q.controlId().equalsIgnoreCase(controlId == null ? null : controlId.trim()))
                .findFirst()
                .orElseThrow(() -> ApiException.notFound("Unknown predefined query: " + controlId));
    }

    /** Catalogue filtered by any combination of technology / framework / controlFamily (null = any). */
    public List<PredefinedQuery> filter(String technology, String framework, String controlFamily) {
        return entries.stream()
                .filter(q -> matches(q.technology(), technology))
                .filter(q -> framework == null || framework.isBlank()
                        || q.frameworksOrEmpty().stream().anyMatch(f -> f.equalsIgnoreCase(framework.trim())))
                .filter(q -> matches(q.controlFamily(), controlFamily))
                .toList();
    }

    public List<String> technologies() {
        return entries.stream().map(PredefinedQuery::technology).filter(s -> s != null)
                .distinct().sorted().toList();
    }

    public List<String> frameworks() {
        return entries.stream().flatMap(q -> q.frameworksOrEmpty().stream())
                .distinct().sorted().toList();
    }

    public List<String> controlFamilies() {
        return entries.stream().map(PredefinedQuery::controlFamily).filter(s -> s != null)
                .distinct().sorted().toList();
    }

    private static boolean matches(String actual, String wanted) {
        return wanted == null || wanted.isBlank()
                || (actual != null && actual.trim().toLowerCase(Locale.ROOT)
                        .equals(wanted.trim().toLowerCase(Locale.ROOT)));
    }
}
