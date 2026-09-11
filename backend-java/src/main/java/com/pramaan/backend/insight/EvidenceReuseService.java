package com.pramaan.backend.insight;

import com.pramaan.backend.ai.EmbeddingModel;
import com.pramaan.backend.ai.EmbeddingStore;
import com.pramaan.backend.common.ApiException;
import com.pramaan.backend.evidence.ControlFrameworkCatalog;
import com.pramaan.backend.evidence.EvidenceNaming;
import com.pramaan.backend.evidence.EvidenceQueryService;
import com.pramaan.backend.evidence.domain.EvidenceRecord;
import com.pramaan.backend.evidence.domain.EvidenceTag;
import com.pramaan.backend.evidence.repo.EvidenceVersionRepository;
import com.pramaan.backend.insight.InsightDtos.ControlReuseEvidence;
import com.pramaan.backend.insight.InsightDtos.ControlReuseResult;
import com.pramaan.backend.insight.InsightDtos.ReuseResult;
import com.pramaan.backend.insight.InsightDtos.SimilarEvidence;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Evidence reuse / similarity.
 *
 * <ul>
 *   <li><b>Exact duplicates</b> — detected deterministically by SHA-256 over the
 *       current version of every persisted record (no re-collection needed).</li>
 *   <li><b>Similar evidence</b> — embedding nearest-neighbour over the
 *       {@link EmbeddingStore} (in-memory or pgvector); ranking + reuse hints are
 *       deterministic.</li>
 * </ul>
 * No LLM.
 */
@Service
@Transactional(readOnly = true)
public class EvidenceReuseService {

    private final EvidenceQueryService evidence;
    private final EvidenceVersionRepository versions;
    private final EvidenceEmbeddingIndexer indexer;
    private final EmbeddingModel model;
    private final EmbeddingStore store;
    private final ControlFrameworkCatalog controlFrameworks;

    public EvidenceReuseService(EvidenceQueryService evidence, EvidenceVersionRepository versions,
                                EvidenceEmbeddingIndexer indexer, EmbeddingModel model, EmbeddingStore store,
                                ControlFrameworkCatalog controlFrameworks) {
        this.evidence = evidence;
        this.versions = versions;
        this.indexer = indexer;
        this.model = model;
        this.store = store;
        this.controlFrameworks = controlFrameworks;
    }

    // ---- reuse by control (deterministic, cross-framework) --------------------

    /** The UC03 control→frameworks catalogue — the control picker for "browse by control". */
    public List<ControlFrameworkCatalog.ControlFrameworks> controlCatalogue() {
        return controlFrameworks.all();
    }

    /**
     * For one control code: the frameworks it satisfies (UC03) and every evidence
     * record already held for it, with the frameworks each record is tagged to.
     * A control required by several frameworks can then reuse existing evidence
     * instead of prompting re-collection.
     */
    public ControlReuseResult reuseByControl(String controlId) {
        if (controlId == null || controlId.isBlank()) {
            throw ApiException.badRequest("controlId is required");
        }
        String cid = controlId.trim();
        List<String> frameworks = controlFrameworks.frameworksFor(cid, null);
        List<ControlReuseEvidence> held = evidence.recordsMatching(new EvidenceQueryService.EvidenceFilter(
                        null, null, cid, null, null, null, null, 0, 100_000))
                .stream()
                .map(this::toControlReuseEvidence)
                .toList();
        return new ControlReuseResult(cid.toUpperCase(Locale.ROOT), frameworks, held);
    }

    private ControlReuseEvidence toControlReuseEvidence(EvidenceRecord r) {
        String frameworksTag = tagValue(r, EvidenceNaming.TAG_FRAMEWORKS);
        List<String> mapped = frameworksTag == null || frameworksTag.isBlank()
                ? (r.getFramework() == null ? List.of() : List.of(r.getFramework()))
                : Arrays.stream(frameworksTag.split(",")).map(String::trim).filter(s -> !s.isBlank())
                        .map(s -> s.toUpperCase(Locale.ROOT)).distinct().toList();
        return new ControlReuseEvidence(r.getId().toString(), r.getApplicationSlug(), r.getControlId(),
                r.getSourceSystem(), tagValue(r, EvidenceNaming.TAG_COLLECTION_METHOD),
                r.getLatestCollectedAt(), r.getLatestSha256(), mapped);
    }

    private static String tagValue(EvidenceRecord r, String key) {
        return r.getTags().stream().filter(t -> key.equals(t.getTagKey()))
                .map(EvidenceTag::getTagValue).findFirst().orElse(null);
    }

    public ReuseResult similarTo(UUID evidenceId, int limit, double minScore) {
        indexer.ensureIndexed();
        EvidenceRecord target = evidence.recordsMatching(
                        new EvidenceQueryService.EvidenceFilter(null, null, null, null, null, null, null, 0, 100_000))
                .stream().filter(r -> r.getId().equals(evidenceId)).findFirst()
                .orElseThrow(() -> ApiException.notFound("Unknown evidence: " + evidenceId));

        String sha = target.getLatestSha256();
        List<SimilarEvidence> exactDuplicates = versions.findRecordsWithCurrentSha256(sha).stream()
                .filter(r -> !r.getId().equals(evidenceId))
                .map(r -> exactDuplicate(r, target))
                .toList();

        String content = evidence.latestContentText(evidenceId)
                .orElseThrow(() -> ApiException.badRequest("evidence " + evidenceId + " has no stored content"));
        float[] q = model.embed(EvidenceEmbeddingIndexer.embedText(target, content));

        List<SimilarEvidence> matches = rank(q, limit, minScore, evidenceId,
                target.getApplicationSlug(), target.getControlId(), sha);
        return new ReuseResult(evidenceId.toString(), null, model.name(), store.driver(),
                (int) store.count(), sha, exactDuplicates, matches);
    }

    public ReuseResult similarToText(String text, int limit, double minScore) {
        if (text == null || text.isBlank()) {
            throw ApiException.badRequest("text is required");
        }
        indexer.ensureIndexed();
        float[] q = model.embed(text);
        List<SimilarEvidence> matches = rank(q, limit, minScore, null, null, null, null);
        return new ReuseResult(null, text, model.name(), store.driver(), (int) store.count(),
                null, List.of(), matches);
    }

    private List<SimilarEvidence> rank(float[] q, int limit, double minScore, UUID excludeId,
                                       String queryApp, String queryControl, String querySha) {
        return store.search(q, Math.max(limit, 1) + (excludeId != null ? 1 : 0)).stream()
                .filter(m -> excludeId == null || !m.entry().evidenceId().equals(excludeId))
                .filter(m -> m.score() >= minScore)
                .limit(limit)
                .map(m -> toSimilar(m, queryApp, queryControl, querySha))
                .toList();
    }

    private SimilarEvidence toSimilar(EmbeddingStore.Match m, String queryApp, String queryControl,
                                      String querySha) {
        var e = m.entry();
        boolean crossApp = queryApp != null && !queryApp.equalsIgnoreCase(e.applicationSlug());
        boolean sameControl = queryControl != null && queryControl.equalsIgnoreCase(e.controlId());
        boolean exact = querySha != null && querySha.equalsIgnoreCase(e.sha256());
        String hint;
        if (exact) {
            hint = "exact SHA-256 duplicate — reuse instead of re-collecting";
        } else if (sameControl && crossApp) {
            hint = "same control already evidenced for " + e.applicationSlug() + " — candidate for reuse";
        } else if (sameControl) {
            hint = "prior evidence for the same control";
        } else if (m.score() >= 0.9) {
            hint = "near-identical content — check for a shared/common control";
        } else {
            hint = "related evidence";
        }
        return new SimilarEvidence(e.evidenceId().toString(), e.applicationSlug(), e.framework(),
                e.controlId(), e.sha256(), round(m.score()), crossApp, sameControl, exact, hint);
    }

    private SimilarEvidence exactDuplicate(EvidenceRecord r, EvidenceRecord target) {
        boolean crossApp = !r.getApplicationSlug().equalsIgnoreCase(target.getApplicationSlug());
        boolean sameControl = r.getControlId().equalsIgnoreCase(target.getControlId());
        String hint = crossApp
                ? "byte-identical evidence already held for " + r.getApplicationSlug()
                        + " — reuse instead of re-collecting"
                : "byte-identical to another record for this application";
        return new SimilarEvidence(r.getId().toString(), r.getApplicationSlug(), r.getFramework(),
                r.getControlId(), r.getLatestSha256(), 1.0, crossApp, sameControl, true, hint);
    }

    private static double round(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }
}
