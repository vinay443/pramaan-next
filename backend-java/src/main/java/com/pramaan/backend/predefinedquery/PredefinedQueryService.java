package com.pramaan.backend.predefinedquery;

import com.pramaan.backend.application.ApplicationEntity;
import com.pramaan.backend.application.ApplicationRepository;
import com.pramaan.backend.common.ApiException;
import com.pramaan.backend.evidence.EvidenceDtos.IngestOutcome;
import com.pramaan.backend.evidence.EvidenceDtos.IngestRequest;
import com.pramaan.backend.evidence.EvidenceDtos.IngestResult;
import com.pramaan.backend.evidence.EvidenceIngestionService;
import com.pramaan.backend.evidence.repo.EvidenceRecordRepository;
import com.pramaan.backend.insight.EvidenceEmbeddingIndexer;
import com.pramaan.backend.predefinedquery.PredefinedQueryDtos.CatalogItemView;
import com.pramaan.backend.predefinedquery.PredefinedQueryDtos.CatalogResponse;
import com.pramaan.backend.predefinedquery.PredefinedQueryDtos.QueryRunResult;
import com.pramaan.backend.predefinedquery.PredefinedQueryDtos.RunSummary;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Runs predefined technical queries (UC — technical evidence collection). A run
 * produces an evidence record through the canonical
 * {@link EvidenceIngestionService#ingest} path, so UC03 naming/tagging and UC04
 * hash integrity apply automatically. Query-collected evidence is tagged
 * {@code collectionMethod=predefined-query}.
 */
@Service
public class PredefinedQueryService {

    private static final Logger log = LoggerFactory.getLogger(PredefinedQueryService.class);
    private static final String SOURCE_SYSTEM = "PREDEFINED_QUERY";
    private static final int OUTPUT_PREVIEW_MAX_CHARS = 4000;

    private final PredefinedQueryCatalog catalog;
    private final PredefinedQueryExecutor executor;
    private final EvidenceIngestionService ingestion;
    private final ApplicationRepository applications;
    private final EvidenceRecordRepository records;
    private final EvidenceEmbeddingIndexer embeddingIndexer;
    private final Clock clock;

    public PredefinedQueryService(PredefinedQueryCatalog catalog, PredefinedQueryExecutor executor,
                                  EvidenceIngestionService ingestion, ApplicationRepository applications,
                                  EvidenceRecordRepository records, EvidenceEmbeddingIndexer embeddingIndexer,
                                  Clock clock) {
        this.catalog = catalog;
        this.executor = executor;
        this.ingestion = ingestion;
        this.applications = applications;
        this.records = records;
        this.embeddingIndexer = embeddingIndexer;
        this.clock = clock;
    }

    public CatalogResponse list(String technology, String framework, String controlFamily) {
        List<CatalogItemView> items = catalog.filter(technology, framework, controlFamily).stream()
                .map(CatalogItemView::from).toList();
        return new CatalogResponse(items.size(), catalog.technologies(), catalog.frameworks(),
                catalog.controlFamilies(), items);
    }

    /** Run one catalogue entry, optionally scoped to a specific onboarded application. */
    public QueryRunResult run(String controlId, String applicationSlug) {
        PredefinedQuery q = catalog.require(controlId);
        String app = resolveApplications(applicationSlug).get(0);
        return runOne(q, app, clock.instant());
    }

    /** Bulk-run the catalogue (optionally a technology/framework/family subset). */
    public RunSummary runAll(String technology, String framework, String controlFamily,
                             String applicationSlug) {
        List<PredefinedQuery> queries = catalog.filter(technology, framework, controlFamily);
        List<String> apps = resolveApplications(applicationSlug);
        String app = apps.get(0);
        Instant asOf = clock.instant();

        List<QueryRunResult> results = new ArrayList<>();
        int ingested = 0, duplicates = 0, failed = 0;
        for (PredefinedQuery q : queries) {
            QueryRunResult r = runOne(q, app, asOf);
            results.add(r);
            switch (r.outcome()) {
                case "CREATED", "NEW_VERSION" -> ingested++;
                case "DUPLICATE" -> duplicates++;
                default -> failed++;
            }
        }
        String msg = "ran %d predefined queries for '%s' (%s): %d ingested, %d duplicates, %d failed"
                .formatted(queries.size(), app, executor.mode(), ingested, duplicates, failed);
        log.info(msg);
        return new RunSummary(queries.size(), ingested, duplicates, failed, app, executor.mode(),
                msg, results);
    }

    private QueryRunResult runOne(PredefinedQuery q, String applicationSlug, Instant asOf) {
        try {
            PredefinedQueryExecutor.Output out = executor.execute(q, applicationSlug, asOf);
            IngestRequest req = new IngestRequest(
                    applicationSlug,
                    q.controlId(),
                    q.frameworksOrEmpty().isEmpty() ? "PREDEFINED" : q.frameworksOrEmpty().get(0),
                    SOURCE_SYSTEM,
                    (q.technology() == null ? "unknown" : q.technology().toLowerCase()) + "/" + q.controlId(),
                    null,
                    out.contentType(),
                    null,
                    out.content(),
                    asOf,
                    "predefined-query",
                    out.metadata(),
                    out.tags());
            // Predefined-query controls aren't in ControlFrameworkCatalog — tag the
            // evidence with the catalogue entry's own full framework list.
            IngestResult ir = ingestion.ingest(req, null, q.frameworksOrEmpty());
            indexEmbedding(ir);
            return new QueryRunResult(q.controlId(), q.technology(), applicationSlug, executor.mode(),
                    ir.outcome().name(), ir.evidenceId(), ir.sha256(), null, truncate(out.content()));
        } catch (RuntimeException ex) {
            log.warn("predefined query {} failed for {}: {}", q.controlId(), applicationSlug, ex.toString());
            return new QueryRunResult(q.controlId(), q.technology(), applicationSlug, executor.mode(),
                    "FAILED", null, null, ex.getMessage(), null);
        }
    }

    /**
     * Embed newly ingested/changed evidence into the vector store eagerly, same as
     * {@code SchedulerRunExecutor.indexEmbedding}. Best effort: an embedding failure
     * must not fail evidence that was already durably ingested — {@code
     * EvidenceEmbeddingIndexer#ensureIndexed()} will catch it up on the next
     * reuse/NL-query call regardless.
     */
    private void indexEmbedding(IngestResult r) {
        if (r.outcome() != IngestOutcome.CREATED && r.outcome() != IngestOutcome.NEW_VERSION) {
            return;
        }
        try {
            records.findById(UUID.fromString(r.evidenceId())).ifPresent(embeddingIndexer::indexOne);
        } catch (RuntimeException ex) {
            log.warn("embedding index failed for evidence {}: {}", r.evidenceId(), ex.getMessage());
        }
    }

    private static String truncate(String content) {
        if (content == null || content.length() <= OUTPUT_PREVIEW_MAX_CHARS) {
            return content;
        }
        return content.substring(0, OUTPUT_PREVIEW_MAX_CHARS) + "…";
    }

    /** Onboarded target(s): the named app (must be active) or every active app. */
    private List<String> resolveApplications(String applicationSlug) {
        if (applicationSlug != null && !applicationSlug.isBlank()) {
            String slug = applicationSlug.trim();
            if (!applications.existsBySlugAndActiveTrue(slug)) {
                throw ApiException.badRequest(
                        "Application '" + slug + "' is not onboarded — onboard it before running predefined queries.");
            }
            return List.of(slug);
        }
        List<String> active = applications.findByActiveTrueOrderBySlugAsc().stream()
                .map(ApplicationEntity::getSlug).toList();
        if (active.isEmpty()) {
            throw ApiException.badRequest(
                    "No applications onboarded — onboard at least one application before running predefined queries.");
        }
        return active;
    }
}
