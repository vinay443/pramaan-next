package com.pramaan.backend.evidence;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/**
 * UC03 Controls_Framework_Mapping — resolves an internal control code to every
 * compliance framework it maps to (PCI DSS, DPSC, ISG, ISO27001, IS, C-SITE, …).
 *
 * <p>Backed by {@code classpath:evidence/control-frameworks.json}. This is
 * intentionally reference data, not a JPA entity/table: the mapping is small,
 * slow-moving, and only consumed at ingestion time to stamp the {@code frameworks}
 * metadata tag. If control metadata ever needs its own lifecycle (ownership,
 * effective dates, per-application overrides) this should be promoted to a
 * {@code control} table — that is a deliberate future decision, not a gap.
 */
@Component
public class ControlFrameworkCatalog {

    private record MappingFile(String note, Map<String, List<String>> mappings) {}

    /** One control code and every framework it helps satisfy (UC03 mapping). */
    public record ControlFrameworks(String controlId, List<String> frameworks) {}

    private final Map<String, List<String>> byControl;

    public ControlFrameworkCatalog(ObjectMapper mapper,
                                   @Value("classpath:evidence/control-frameworks.json") Resource resource) {
        try (InputStream in = resource.getInputStream()) {
            Map<String, List<String>> raw = mapper.readValue(in, MappingFile.class).mappings();
            Map<String, List<String>> normalized = new LinkedHashMap<>();
            raw.forEach((k, v) -> normalized.put(k.trim().toUpperCase(Locale.ROOT), List.copyOf(v)));
            this.byControl = Map.copyOf(normalized);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot load evidence/control-frameworks.json", e);
        }
    }

    /**
     * Every framework the control maps to, with {@code requestedFramework} guaranteed
     * to appear (first if the control is unmapped). Order is stable and de-duplicated.
     */
    public List<String> frameworksFor(String controlId, String requestedFramework) {
        List<String> out = new ArrayList<>();
        String req = requestedFramework == null ? null : requestedFramework.trim().toUpperCase(Locale.ROOT);
        if (req != null && !req.isBlank()) {
            out.add(req);
        }
        if (controlId != null) {
            for (String fw : byControl.getOrDefault(controlId.trim().toUpperCase(Locale.ROOT), List.of())) {
                if (!out.contains(fw)) {
                    out.add(fw);
                }
            }
        }
        return out;
    }

    /** Whether this control code is present in the UC03 mapping. */
    public boolean isMapped(String controlId) {
        return controlId != null
                && byControl.containsKey(controlId.trim().toUpperCase(Locale.ROOT));
    }

    /** The whole catalogue — every control code and its framework list, control-id order. */
    public List<ControlFrameworks> all() {
        return byControl.entrySet().stream()
                .map(e -> new ControlFrameworks(e.getKey(), e.getValue()))
                .sorted(Comparator.comparing(ControlFrameworks::controlId))
                .toList();
    }
}
