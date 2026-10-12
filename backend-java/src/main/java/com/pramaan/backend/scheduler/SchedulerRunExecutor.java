package com.pramaan.backend.scheduler;

import com.pramaan.backend.application.ApplicationEntity;
import com.pramaan.backend.application.ApplicationRepository;
import com.pramaan.backend.evidence.EvidenceDtos.IngestOutcome;
import com.pramaan.backend.evidence.EvidenceDtos.IngestRequest;
import com.pramaan.backend.evidence.EvidenceDtos.IngestResult;
import com.pramaan.backend.evidence.EvidenceIngestionService;
import com.pramaan.backend.evidence.EvidenceLifecycleService;
import com.pramaan.backend.evidence.repo.EvidenceRecordRepository;
import com.pramaan.backend.insight.EvidenceEmbeddingIndexer;
import com.pramaan.backend.integrations.EnterpriseIntegration;
import com.pramaan.backend.integrations.EnterpriseIntegration.CollectedEvidence;
import com.pramaan.backend.integrations.EnterpriseIntegration.CollectionRequest;
import com.pramaan.backend.integrations.IntegrationRegistry;
import com.pramaan.backend.integrations.SourceConnection.Status;
import com.pramaan.backend.integrations.SourceConnectionService;
import com.pramaan.backend.notification.NotificationService;
import com.pramaan.backend.scheduler.domain.SchedulerRun;
import java.time.Clock;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
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
    private final EvidenceLifecycleService lifecycle;
    private final SourceConnectionService connections;
    private final NotificationService notifications;
    private final EvidenceRecordRepository records;
    private final EvidenceEmbeddingIndexer embeddingIndexer;
    private final Clock clock;

    public SchedulerRunExecutor(SchedulerRunRepository runs, ApplicationRepository applications,
                                IntegrationRegistry integrations, EvidenceIngestionService ingestion,
                                EvidenceLifecycleService lifecycle,
                                SourceConnectionService connections,
                                NotificationService notifications,
                                EvidenceRecordRepository records, EvidenceEmbeddingIndexer embeddingIndexer,
                                Clock clock) {
        this.runs = runs;
        this.applications = applications;
        this.integrations = integrations;
        this.ingestion = ingestion;
        this.lifecycle = lifecycle;
        this.connections = connections;
        this.notifications = notifications;
        this.records = records;
        this.embeddingIndexer = embeddingIndexer;
        this.clock = clock;
    }

    @Async
    public void executeAsync(UUID runId) {
        executeGuarded(runId);
    }

    /**
     * Runs {@link #execute} and guarantees the run never stays PENDING / RUNNING: an unexpected exception
     * marks it FAILED (with the reason), so it shows in history and stops blocking its schedule.
     */
    void executeGuarded(UUID runId) {
        try {
            execute(runId);
        } catch (RuntimeException ex) {
            log.error("scheduler run {} failed", runId, ex);
            markFailed(runId, ex);
        }
    }

    private void markFailed(UUID runId, RuntimeException ex) {
        try {
            runs.findById(runId).ifPresent(run -> {
                if (run.getStatus() != SchedulerRun.Status.PENDING && run.getStatus() != SchedulerRun.Status.RUNNING) {
                    return; // already finished; the exception came after completion
                }
                String reason = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
                run.fail(clock.instant(), "run aborted: " + (reason.length() > 1800 ? reason.substring(0, 1800) : reason));
                runs.save(run);
                notifications.onRunFinished(run); // best effort, idempotent per run
            });
        } catch (RuntimeException inner) {
            log.error("could not mark scheduler run {} as FAILED", runId, inner);
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

        int received = 0, ingested = 0, duplicates = 0, failed = 0, sourcesFailed = 0;
        Map<String, String> perSource = new LinkedHashMap<>();
        if (!skippedApps.isEmpty()) {
            perSource.put("_skipped", "not onboarded: " + String.join(",", skippedApps));
        }

        for (String source : sources) {
            int sRecv = 0, sIng = 0, sDup = 0, sFail = 0;
            // Simulated credential state: a blocked source is neither polled nor ingested.
            Status connection = connectionStatus(source);
            if (connection != Status.VALID) {
                boolean disabled = connection == Status.DISABLED;
                perSource.put(source, failure(disabled ? "CONNECTION_DISABLED" : "AUTH_FAILED", disabled
                        ? "Connection for " + source + " is disabled"
                        : "Authentication failed for " + source + ": credential invalid or expired (HTTP 401)"));
                failed++;
                sourcesFailed++;
                continue;
            }
            EnterpriseIntegration integration;
            try {
                integration = integrations.require(source);
            } catch (RuntimeException ex) {
                perSource.put(source, failure("UNKNOWN_SOURCE", ex.getMessage()));
                failed++;
                sourcesFailed++;
                continue;
            }
            String collectError = null;
            boolean connected = true;
            for (String slug : appSlugs) {
                List<CollectedEvidence> collected;
                try {
                    collected = integration.collect(
                            new CollectionRequest(slug, frameworks, clock.instant()));
                } catch (RuntimeException ex) {
                    sFail++;
                    connected = false;
                    collectError = ex.getMessage();
                    continue;
                }
                for (CollectedEvidence ce : collected) {
                    sRecv++;
                    try {
                        IngestResult r = ingestWithRetry(toRequest(slug, source, ce, run.getEvidenceType()), runId);
                        switch (r.outcome()) {
                            case CREATED, NEW_VERSION -> {
                                sIng++;
                                indexEmbedding(r);
                            }
                            case DUPLICATE -> {
                                sDup++;
                                auditDuplicate(r, run);
                            }
                        }
                    } catch (RuntimeException ex) {
                        sFail++;
                    }
                }
            }
            perSource.put(source, "received=%d ingested=%d duplicates=%d failed=%d".formatted(sRecv, sIng, sDup, sFail)
                    + (connected ? " connectivity=CONNECTED" : " " + failure("COLLECT_FAILED", collectError)));
            if (!connected) sourcesFailed++;
            received += sRecv; ingested += sIng; duplicates += sDup; failed += sFail;
        }

        run.complete(clock.instant(), received, ingested, duplicates, failed, perSource, sourcesFailed, sources.size());
        runs.save(run);
        notifications.onRunFinished(run); // best effort, idempotent per run
        log.info("scheduler run {} complete: {}", runId, run.getMessage());
    }

    /**
     * Concurrent runs over the same scope can create the same evidence record at the same instant; the loser
     * hits the unique evidence key (or version) and its transaction rolls back. Retrying in a fresh
     * transaction sees the winner's row and resolves to a DUPLICATE / NEW_VERSION instead of a spurious failure.
     */
    private IngestResult ingestWithRetry(IngestRequest req, UUID runId) {
        for (int attempt = 1; ; attempt++) {
            try {
                return ingestion.ingest(req, runId);
            } catch (DataIntegrityViolationException ex) {
                if (attempt >= 3) throw ex;
            }
        }
    }

    private Status connectionStatus(String source) {
        return connections.statusOf(source);
    }

    /** Summary text for a failed source; the run view parses errorCode / message back out of it. */
    private static String failure(String code, String message) {
        String m = message == null ? "unknown error" : message.replaceAll("\s+", " ");
        return "error: " + (m.length() > 300 ? m.substring(0, 300) : m) + " connectivity=FAILED errorCode=" + code;
    }

    /** Best effort, like indexing: an audit-write problem must not fail evidence that was handled correctly. */
    private void auditDuplicate(IngestResult r, SchedulerRun run) {
        try {
            String trigger = run.getTrigger().name();
            lifecycle.recordDuplicateSkipped(UUID.fromString(r.evidenceId()), run.getId(), trigger,
                    run.getScheduleName() != null ? run.getScheduleName() : trigger);
        } catch (RuntimeException ex) {
            log.warn("duplicate-skipped audit failed for evidence {}: {}", r.evidenceId(), ex.getMessage());
        }
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

    private static IngestRequest toRequest(String slug, String source, CollectedEvidence ce, String evidenceType) {
        Map<String, String> tags = ce.tags();
        if (evidenceType != null && !evidenceType.isBlank()) {
            // A schedule's evidence type wins over the connector default (EvidenceNaming honors this tag).
            tags = new LinkedHashMap<>(tags == null ? Map.of() : tags);
            tags.put("evidenceType", evidenceType);
        }
        return new IngestRequest(slug, ce.controlId(), ce.framework(), source, ce.sourceObjectId(),
                ce.title(), ce.contentType(),
                Base64.getEncoder().encodeToString(ce.content()), null,
                ce.collectedAt(), source, ce.metadata(), tags);
    }

    private static List<String> split(String csv) {
        return csv == null || csv.isBlank() ? List.of() : List.of(csv.split("\\s*,\\s*"));
    }
}
