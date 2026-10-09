package com.pramaan.backend.retention;

import com.pramaan.backend.common.ApiException;
import com.pramaan.backend.evidence.EvidenceLifecycleService;
import com.pramaan.backend.evidence.EvidenceQueryService;
import com.pramaan.backend.evidence.EvidenceQueryService.EvidenceFilter;
import com.pramaan.backend.evidence.domain.EvidenceLifecycleState;
import com.pramaan.backend.evidence.domain.EvidenceRecord;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Archival workflow. EXECUTE only moves eligible evidence to the terminal ARCHIVED lifecycle state and records an
 * audit event; the stored object, SHA-256 and versions are never touched, and nothing is deleted.
 */
@Service
public class RetentionService {

    public record EligibleItem(String evidenceId, String applicationSlug, String controlId, String framework,
                               Instant collectedAt, Instant expiresAt) {}

    public record ArchiveResult(String runId, String mode, int retentionDays, String application, int scanned,
                                int eligible, int archived, int skipped, List<EligibleItem> items) {}

    public record RunView(String id, String mode, String triggeredBy, Instant startedAt, Instant finishedAt,
                          int retentionDays, String application, int scanned, int archived, int skipped) {
        static RunView from(RetentionRun r) {
            return new RunView(r.getId().toString(), r.getMode(), r.getTriggeredBy(), r.getStartedAt(),
                    r.getFinishedAt(), r.getRetentionDays(), r.getApplication(), r.getScanned(), r.getArchived(),
                    r.getSkipped());
        }
    }

    private final RetentionPolicyService policy;
    private final RetentionRunRepository runs;
    private final EvidenceQueryService evidence;
    private final EvidenceLifecycleService lifecycle;
    private final Clock clock;

    public RetentionService(RetentionPolicyService policy, RetentionRunRepository runs, EvidenceQueryService evidence,
                            EvidenceLifecycleService lifecycle, Clock clock) {
        this.policy = policy;
        this.runs = runs;
        this.evidence = evidence;
        this.lifecycle = lifecycle;
        this.clock = clock;
    }

    @Transactional
    public ArchiveResult archive(String mode, String application, String user) {
        if (!"PREVIEW".equals(mode) && !"EXECUTE".equals(mode)) {
            throw ApiException.badRequest("mode must be PREVIEW or EXECUTE");
        }
        String by = user == null || user.isBlank() ? "anonymous" : user.trim();
        String app = application == null || application.isBlank() ? null : application.trim();
        Instant started = clock.instant();
        int days = policy.days();

        List<EvidenceRecord> scope = evidence.recordsMatching(
                new EvidenceFilter(app, null, null, null, null, null, null, 0, 100_000));
        List<EvidenceRecord> eligible = scope.stream()
                .filter(r -> r.getLifecycleState() != EvidenceLifecycleState.ARCHIVED)
                .filter(r -> r.getLatestCollectedAt() != null
                        && !r.getLatestCollectedAt().plus(days, ChronoUnit.DAYS).isAfter(started))
                .toList();
        List<EligibleItem> items = eligible.stream().map(r -> new EligibleItem(r.getId().toString(),
                r.getApplicationSlug(), r.getControlId(), r.getFramework(), r.getLatestCollectedAt(),
                r.getLatestCollectedAt().plus(days, ChronoUnit.DAYS))).toList();

        int archived = 0;
        if ("EXECUTE".equals(mode)) {
            for (EvidenceRecord r : eligible) {
                lifecycle.archive(r, by, "retention " + days + "d elapsed (policy)");
                archived++;
            }
        }
        // skipped = in scope but not eligible (already archived, or not yet past retention)
        int skipped = scope.size() - eligible.size();
        RetentionRun run = runs.save(new RetentionRun(mode, by, started, clock.instant(), days, app, scope.size(),
                archived, skipped));
        return new ArchiveResult(run.getId().toString(), mode, days, app, scope.size(), eligible.size(), archived,
                skipped, items);
    }

    @Transactional(readOnly = true)
    public List<RunView> runs(Integer limit) {
        int n = limit == null ? 50 : Math.max(1, Math.min(limit, 200));
        return runs.findAllByOrderByStartedAtDesc(PageRequest.of(0, n)).stream().map(RunView::from).toList();
    }
}
