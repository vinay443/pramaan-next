package com.pramaan.backend.insight;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pramaan.backend.insight.ControlCatalog.Expectation;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class ControlCatalogTest {

    // same leniency as Spring Boot's ObjectMapper (the file has a top-level "note")
    private final ControlCatalog catalog = new ControlCatalog(
            new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false),
            new ClassPathResource("phase2/control-catalog.json"));

    @Test
    void everyXlsxSourcedControlCarriesANonBlankDescription() {
        var library = catalog.all().stream().filter(Expectation::fromLibrary).toList();

        assertThat(library).hasSize(133);
        assertThat(library).allSatisfy(e -> assertThat(e.description())
                .as(e.framework() + " " + e.controlId()).isNotBlank());
    }

    @Test
    void legacyControlsKeepNoDescriptionAndAreNotRenamed() {
        var legacy = catalog.all().stream().filter(e -> !e.fromLibrary()).toList();

        assertThat(legacy).hasSize(18).allSatisfy(e -> assertThat(e.description()).isNull());
        assertThat(catalog.find("C-SITE", "OS-SSH-ROOT-LOGIN")).get()
                .satisfies(e -> {
                    assertThat(e.title()).isEqualTo("SSH root login disabled");
                    assertThat(e.source()).isEqualTo("legacy");
                });
    }

    @Test
    void descriptionsMatchTheEcsControlLibraryVerbatim() {
        assertThat(description("PCI_DSS", "PCI-C6"))
                .isEqualTo("Securely develop and maintain systems/software and remediate security vulnerabilities.");
        assertThat(description("DPSC", "DPSC-C14"))
                .isEqualTo("Integrate security controls into payment-application development and change processes.");
        assertThat(description("OS_BASELINING", "OSBL-C1")).isEqualTo("OS must run an approved and supported version.");
        assertThat(description("NGINX_BASELINING", "NGBL-C5")).isEqualTo("Only approved TLS versions may be enabled.");
        assertThat(description("INTERNAL_AUDIT", "IA-C1"))
                .isEqualTo("Ensure required applications/processes/control areas are included in audit scope.");
    }

    @Test
    void lookupPrefersTheExactFrameworkThenFallsBackToTheControlId() {
        assertThat(catalog.find("pci_dss", "pci-c6")).get().extracting(Expectation::framework).isEqualTo("PCI_DSS");
        assertThat(catalog.find("SOME_OTHER", "PCI-C6")).get().extracting(Expectation::framework).isEqualTo("PCI_DSS");
        assertThat(catalog.find("PCI_DSS", "NO-SUCH-CONTROL")).isEmpty();
        assertThat(catalog.find("PCI_DSS", null)).isEmpty();
    }

    private String description(String framework, String controlId) {
        return catalog.find(framework, controlId).orElseThrow().description();
    }
}
