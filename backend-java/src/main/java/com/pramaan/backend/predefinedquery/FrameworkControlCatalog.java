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
 * Loads and exposes the framework control catalogue
 * ({@code classpath:predefined-queries/framework-controls.json}): the per-control
 * rows (code, name, description, source status) for each compliance/security
 * framework — PCI DSS, DPSE, ITPP, OS/database/middleware baselines, VAPT,
 * C-SITE, ITDRM, Internal Audit. Reference data only, not a JPA entity — same
 * rationale as {@code ControlFrameworkCatalog}: small, slow-moving, and only
 * needed for lookup, not per-row lifecycle. Overridable with
 * {@code pramaan.predefined-queries.framework-controls}.
 */
@Component
public class FrameworkControlCatalog {

    private final List<FrameworkControl> entries;

    public FrameworkControlCatalog(ObjectMapper mapper,
                                   @Value("${pramaan.predefined-queries.framework-controls:classpath:predefined-queries/framework-controls.json}")
                                   Resource resource) {
        try (InputStream in = resource.getInputStream()) {
            this.entries = List.of(mapper.readValue(in, FrameworkControl[].class));
        } catch (IOException e) {
            throw new UncheckedIOException("cannot load predefined-queries/framework-controls.json", e);
        }
    }

    public List<FrameworkControl> all() {
        return entries;
    }

    public List<FrameworkControl> byFramework(String framework) {
        return entries.stream()
                .filter(c -> matches(c.framework(), framework))
                .toList();
    }

    public FrameworkControl byCode(String framework, String controlCode) {
        return entries.stream()
                .filter(c -> matches(c.framework(), framework))
                .filter(c -> c.controlCode() != null
                        && c.controlCode().equalsIgnoreCase(controlCode == null ? null : controlCode.trim()))
                .findFirst()
                .orElseThrow(() -> ApiException.notFound(
                        "Unknown framework control: " + framework + "/" + controlCode));
    }

    /** Distinct framework list, in catalogue order. */
    public List<String> frameworks() {
        return entries.stream().map(FrameworkControl::framework).filter(s -> s != null)
                .distinct().toList();
    }

    private static boolean matches(String actual, String wanted) {
        return wanted != null && actual != null
                && actual.trim().toLowerCase(Locale.ROOT).equals(wanted.trim().toLowerCase(Locale.ROOT));
    }
}
