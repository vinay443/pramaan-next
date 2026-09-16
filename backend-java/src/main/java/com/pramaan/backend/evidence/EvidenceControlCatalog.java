package com.pramaan.backend.evidence;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/**
 * The subset of {@code classpath:phase2/control-catalog.json} sourced from
 * {@code docs/ECS_Control_Library.xlsx} ({@code "source": "xlsx"} entries only) —
 * the 133-control bank catalog. Used to gate live evidence ingestion (Scheduler,
 * manual/bulk upload) so new evidence can only be tagged against a real xlsx
 * (framework, controlId) pair, never a legacy placeholder or an out-of-catalog
 * framework (ISO27001, IS, …).
 *
 * <p>Deliberately a separate loader from {@code insight.ControlCatalog} (same
 * file, same pattern as {@link ControlFrameworkCatalog} vs. that class) so the
 * {@code evidence} package doesn't take a dependency on {@code insight}, which
 * already depends on {@code evidence}.
 */
@Component
public class EvidenceControlCatalog {

    private record Expectation(String framework, String controlId, String source) {}

    private record CatalogFile(List<Expectation> expectations) {}

    /** controlId (upper) -> framework (upper), xlsx-sourced entries only. */
    private final Map<String, String> byControlId;

    public EvidenceControlCatalog(ObjectMapper mapper,
                                  @Value("classpath:phase2/control-catalog.json") Resource resource) {
        try (InputStream in = resource.getInputStream()) {
            List<Expectation> all = mapper.readValue(in, CatalogFile.class).expectations();
            Map<String, String> byId = new LinkedHashMap<>();
            for (Expectation e : all) {
                if ("xlsx".equalsIgnoreCase(e.source())) {
                    byId.put(e.controlId().trim().toUpperCase(Locale.ROOT),
                            e.framework().trim().toUpperCase(Locale.ROOT));
                }
            }
            this.byControlId = Map.copyOf(byId);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot load phase2/control-catalog.json", e);
        }
    }

    /** Whether (framework, controlId) is one of the 133 xlsx-sourced catalog entries. */
    public boolean isConformant(String framework, String controlId) {
        if (framework == null || controlId == null) {
            return false;
        }
        String expected = byControlId.get(controlId.trim().toUpperCase(Locale.ROOT));
        return expected != null && expected.equals(framework.trim().toUpperCase(Locale.ROOT));
    }

    /** Every xlsx-sourced control id, for diagnostics/reporting. */
    public java.util.Set<String> controlIds() {
        return byControlId.keySet();
    }
}
