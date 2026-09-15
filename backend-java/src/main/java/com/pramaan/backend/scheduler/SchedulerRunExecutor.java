package com.pramaan.backend.scheduler;

import com.pramaan.backend.application.ApplicationEntity;
import com.pramaan.backend.application.ApplicationRepository;
import com.pramaan.backend.evidence.EvidenceDtos.IngestOutcome;
import com.pramaan.backend.evidence.EvidenceDtos.IngestRequest;
import com.pramaan.backend.evidence.EvidenceDtos.IngestResult;
import com.pramaan.backend.evidence.EvidenceIngestionService;
import com.pramaan.backend.evidence.repo.EvidenceRecordRepository;
import com.pramaan.backend.insight.EvidenceEmbeddingIndexer;
import com.pramaan.backend.integrations.EnterpriseIntegration;
import com.pramaan.backend.integrations.EnterpriseIntegration.CollectedEvidence;
import com.pramaan.backend.integrations.EnterpriseIntegration.CollectionRequest;
import com.pramaan.backend.integrations.IntegrationRegistry;
import com.pramaan.backend.scheduler.domain.SchedulerRun;
import java.time.Clock;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Runs a scheduler collection: pull from each integration, ingest, tally.
 *
 * <p>Deliberately NOT {@code @Transactional} at this level. {@link
 * EvidenceIngestionService#ingest} is transactional per call, so leaving this method
 * bare lets each of the (potentially hundreds of) items across all sources commit or
 * fail independently. Wrapping the whole loop in one transaction meant a single
 * genuine failure (or, before the {@code replaceTags} fix, any re-tagging of an
 * already-tagged record) marked the shared transaction rollback-only, silently
 * failing every item after it in the same run.
 */
@Component
public class SchedulerRunExecutor {

    private static final Logger log = LoggerFactory.getLogger(SchedulerRunExecutor.class);

    private final SchedulerRunRepository runs;
    private final ApplicationRepository applications;
    private final IntegrationRegistry integrations;
    private final EvidenceIngestionService ingestion;
    private final EvidenceRecordRepository records;
    private final EvidenceEmbeddingIndexer embeddingIndexer;
    private final Clock clock;

    public SchedulerRunExecutor(SchedulerRunRepository runs, ApplicationRepository applications,
                                IntegrationRegistry integrations, EvidenceIngestionService ingestion,
                                EvidenceRecordRepository records, EvidenceEmbeddingIndexer embeddingIndexer,
                                Clock clock) {
        this.runs = runs;
        this.applications = applications;
        this.integrations = integrations;
        this.ingestion = ingestion;
        this.records = records;
        this.embeddingIndexer = embeddingIndexer;
        this.clock = clock;
    }

    @Async
    public void executeAsync(UUID runId) {
        try {
            execute(runId);
        } catch (RuntimeException ex) {
            log.error("scheduler run {} failed", runId, ex);
        }
    }

    public void execute(UUID runId) {
        SchedulerRun run = runs.findById(runId).orElseThrow();
        run.markRunning(clock.instant());
        runs.saveAndFlush(run);

        List<String> appSlugs = split(run.getApplications());
        List<String> skippedApps = List.of();
        if (appSlugs.isEmpty()) {
            // blank = every onboarded (active) application
            appSlugs = applications.findByActiveTrueOrderBySlugAsc().stream()
                    .map(ApplicationEntity::getSlug).toList();
        } else {
            // Defense in depth — trigger() already rejects a run that names a
            // not-onboarded app, but never ingest for one that slipped through.
            Map<Boolean, List<String>> byOnboarded = appSlugs.stream()
                    .collect(java.util.stream.Collectors.partitioningBy(
                            applications::existsBySlugAndActiveTrue));
            appSlugs = byOnboarded.get(true);
            skippedApps = byOnboarded.get(false);
            if (!skippedApps.isEmpty()) {
                log.warn("scheduler run {} skipping not-onboarded applications: {}", runId, skippedApps);
            }
        }
        List<String> frameworks = split(run.getFrameworks());
        List<String> sources = split(run.getSources());

        int received = 0, ingested = 0, duplicates = 0, failed = 0;
        Map<String, String> perSource = new LinkedHashMap<>();
        if (!skippedApps.isEmpty()) {
            perSource.put("_skipped", "not onboarded: " + String.join(",", skippedApps));
        }

        for (String source : sources) {
            int sRecv = 0, sIng = 0, sDup = 0, sFail = 0;
            EnterpriseIntegration integration;
            try {
                integration = integrations.require(source);
            } catch (RuntimeException ex) {
                perSource.put(source, "error: " + ex.getMessage());
                failed++;
                continue;
            }
            for (String slug : appSlugs) {
                List<CollectedEvidence> collected;
                try {
                    collected = integration.collect(
                            new CollectionRequest(slug, frameworks, clock.instant()));
                } catch (RuntimeException ex) {
                    sFail++;
                    continue;
                }
                for (CollectedEvidence ce : collected) {
                    sRecv++;
                    try {
                        IngestResult r = ingestion.ingest(toRequest(slug, source, ce), runId);
                        switch (r.outcome()) {
                            case CREATED, NEW_VERSION -> {
                                sIng++;
                                indexEmbedding(r);
                            }
                            case DUPLICATE -> sDup++;
                        }
                    } catch (RuntimeException ex) {
                        sFail++;
                    }
                }
            }
            perSource.put(source, "received=%d ingested=%d duplicates=%d failed=%d"
                    .formatted(sRecv, sIng, sDup, sFail));
            received += sRecv; ingested += sIng; duplicates += sDup; failed += sFail;
        }

        run.complete(clock.instant(), received, ingested, duplicates, failed, perSource);
        runs.save(run);
        log.info("scheduler run {} complete: {}", runId, run.getMessage());
    }

    /**
     * Embed newly ingested/changed evidence into the vector store so it's searchable
     * (reuse/similarity, NL query) without waiting for the on-demand
     * {@link EvidenceEmbeddingIndexer#ensureIndexed()} reindex-on-query path. Best
     * effort: an embedding failure (e.g. the embedding model being unreachable) must
     * not fail evidence that was already durably ingested — {@code ensureIndexed()}
     * will catch it up on the next reuse/NL-query call regardless.
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

    private static IngestRequest toRequest(String slug, String source, CollectedEvidence ce) {
        return new IngestRequest(slug, ce.controlId(), ce.framework(), source, ce.sourceObjectId(),
                ce.title(), ce.contentType(),
                Base64.getEncoder().encodeToString(ce.content()), null,
                ce.collectedAt(), source, ce.metadata(), ce.tags());
    }

    private static List<String> split(String csv) {
        return csv == null || csv.isBlank() ? List.of() : List.of(csv.split("\\s*,\\s*"));
    }
}
