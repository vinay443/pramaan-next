package com.pramaan.backend.predefinedquery;

import java.util.List;

public final class PredefinedQueryDtos {

    private PredefinedQueryDtos() {}

    /** One catalogue row plus the run facets the UI filters on. */
    public record CatalogItemView(String controlId, String technology, String controlName,
                                  String command, List<String> frameworks, String evidenceType,
                                  boolean executableNow, String runtimeStatus, String controlFamily) {
        public static CatalogItemView from(PredefinedQuery q) {
            return new CatalogItemView(q.controlId(), q.technology(), q.controlName(), q.command(),
                    q.frameworksOrEmpty(), q.evidenceType(), q.executableNow(), q.runtimeStatus(),
                    q.controlFamily());
        }
    }

    public record CatalogResponse(int total, List<String> technologies, List<String> frameworks,
                                  List<String> controlFamilies, List<CatalogItemView> items) {}

    /** Result of running one predefined query. {@code outputPreview} is the simulated/collected
     *  content that was ingested as evidence, truncated so a bulk-run summary stays a reasonable size. */
    public record QueryRunResult(String controlId, String technology, String applicationSlug,
                                 String mode, String outcome, String evidenceId, String sha256,
                                 String error, String outputPreview) {}

    /**
     * Bulk-run summary — same received / ingested / duplicates / failed shape as a
     * scheduler run result so the UI can reuse the Scheduler run-result panel.
     */
    public record RunSummary(int received, int ingested, int duplicates, int failed,
                             String applicationSlug, String mode, String message,
                             List<QueryRunResult> results) {}
}
