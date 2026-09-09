package com.pramaan.backend.predefinedquery;

import com.pramaan.backend.predefinedquery.PredefinedQueryDtos.CatalogResponse;
import com.pramaan.backend.predefinedquery.PredefinedQueryDtos.QueryRunResult;
import com.pramaan.backend.predefinedquery.PredefinedQueryDtos.RunSummary;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Predefined technical-query catalogue + simulated execution. */
@RestController
@RequestMapping("/api/v1/predefined-queries")
public class PredefinedQueryController {

    private final PredefinedQueryService service;

    public PredefinedQueryController(PredefinedQueryService service) {
        this.service = service;
    }

    /** List the catalogue, filterable by technology / framework / controlFamily. */
    @GetMapping
    public CatalogResponse list(@RequestParam(required = false) String technology,
                                @RequestParam(required = false) String framework,
                                @RequestParam(required = false) String controlFamily) {
        return service.list(technology, framework, controlFamily);
    }

    /** Run one catalogue entry (simulated), optionally scoped to an onboarded application. */
    @PostMapping("/{controlId}/run")
    public QueryRunResult run(@PathVariable String controlId,
                              @RequestParam(required = false) String applicationSlug) {
        return service.run(controlId, applicationSlug);
    }

    /** Bulk-run the whole catalogue or a filtered subset; scheduler-run-shaped summary. */
    @PostMapping("/run-all")
    public RunSummary runAll(@RequestParam(required = false) String technology,
                             @RequestParam(required = false) String framework,
                             @RequestParam(required = false) String controlFamily,
                             @RequestParam(required = false) String applicationSlug) {
        return service.runAll(technology, framework, controlFamily, applicationSlug);
    }
}
