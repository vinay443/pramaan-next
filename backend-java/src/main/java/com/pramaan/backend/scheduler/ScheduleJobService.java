package com.pramaan.backend.scheduler;

import com.pramaan.backend.application.ApplicationEntity;
import com.pramaan.backend.application.ApplicationRepository;
import com.pramaan.backend.common.ApiException;
import com.pramaan.backend.integrations.IntegrationRegistry;
import com.pramaan.backend.security.SecurityEventService;
import com.pramaan.backend.scheduler.SchedulerDtos.CreateScheduleRequest;
import com.pramaan.backend.scheduler.SchedulerDtos.RunRequest;
import com.pramaan.backend.scheduler.SchedulerDtos.RunView;
import com.pramaan.backend.scheduler.SchedulerDtos.ScheduleOptions;
import com.pramaan.backend.scheduler.SchedulerDtos.ScheduleView;
import com.pramaan.backend.scheduler.domain.ScheduleJob;
import com.pramaan.backend.scheduler.domain.ScheduleJob.Frequency;
import com.pramaan.backend.scheduler.domain.SchedulerRun;
import com.pramaan.backend.scheduler.domain.SchedulerRun.Trigger;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/** Per-job collection schedules. Runs are started through the existing {@link SchedulerService}. */
@Service
public class ScheduleJobService {

    private static final Logger log = LoggerFactory.getLogger(ScheduleJobService.class);

    /** Evidence classifications produced by {@code EvidenceNaming.resolveEvidenceType}. */
    static final List<String> EVIDENCE_TYPES = List.of("HOST-CONFIG", "DB-CONFIG", "MIDDLEWARE-CONFIG",
            "TLS-SCAN", "CHANGE-TICKET", "CODE-REVIEW", "AGENT-SCAN", "GENERAL");

    private final ScheduleJobRepository jobs;
    private final SchedulerService scheduler;
    private final IntegrationRegistry integrations;
    private final ApplicationRepository applications;
    private final Clock clock;

    private final SecurityEventService security;
    private final SchedulerRunRepository runs;

    public ScheduleJobService(ScheduleJobRepository jobs, SchedulerService scheduler,
                              IntegrationRegistry integrations, ApplicationRepository applications,
                              Clock clock, SecurityEventService security, SchedulerRunRepository runs) {
        this.runs = runs;
        this.security = security;
        this.jobs = jobs;
        this.scheduler = scheduler;
        this.integrations = integrations;
        this.applications = applications;
        this.clock = clock;
    }

    /**
     * Restart durability: jobs live in the database, so after a restart they are simply loaded again.
     * Overdue ones (missed while down) fire once on the first tick and then move to the future.
     */
    @EventListener(ApplicationReadyEvent.class)
    void logLoadedSchedules() {
        log.info("schedules loaded at startup: {} active, {} overdue (will fire once on the first tick)",
                jobs.countByActiveTrue(), jobs.findByActiveTrueAndNextRunAtLessThanEqual(clock.instant()).size());
    }

    /**
     * Runs execute on this JVM's async executor, so a PENDING / RUNNING run found at startup was cut off by the
     * restart and will never finish. Mark it FAILED (kept in history) so it stops blocking its schedule.
     */
    @EventListener(ApplicationReadyEvent.class)
    void failRunsOrphanedByRestart() {
        List<SchedulerRun> orphaned = runs.findByStatusIn(List.of(SchedulerRun.Status.PENDING, SchedulerRun.Status.RUNNING));
        for (SchedulerRun run : orphaned) {
            run.fail(clock.instant(), "run aborted: backend restarted while this run was in progress");
            runs.save(run);
        }
        if (!orphaned.isEmpty()) {
            log.warn("marked {} run(s) left PENDING/RUNNING by a restart as FAILED", orphaned.size());
        }
    }

    public ScheduleOptions options() {
        return new ScheduleOptions(integrations.available(),
                applications.findByActiveTrueOrderBySlugAsc().stream().map(ApplicationEntity::getSlug).toList(),
                EVIDENCE_TYPES,
                Arrays.stream(Frequency.values()).map(Enum::name).toList());
    }

    public ScheduleView create(CreateScheduleRequest req) {
        if (req == null) throw ApiException.badRequest("request body is required");
        // untrusted text: length and character rules first (rejections are recorded as security events)
        String name = security.requireSafe("name", req.name());
        String source = security.requireSafe("sourceSystem", req.sourceSystem()).toUpperCase();
        String app = security.requireSafe("application", req.application());
        String type = security.requireSafe("evidenceType", req.evidenceType());
        String freq = required(req.frequency(), "frequency").toUpperCase();
        Frequency frequency;
        try {
            frequency = Frequency.valueOf(freq);
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("frequency must be one of " + Arrays.toString(Frequency.values()));
        }
        if (!integrations.available().contains(source)) {
            throw ApiException.badRequest("unknown sourceSystem '" + source + "' (available: "
                    + integrations.available() + ")");
        }
        if (!applications.existsBySlugAndActiveTrue(app)) {
            throw ApiException.badRequest("Application '" + app + "' is not onboarded");
        }
        String createdBy = req.createdBy() == null || req.createdBy().isBlank() ? "ui" : req.createdBy().trim();
        return ScheduleView.from(jobs.save(
                new ScheduleJob(name, source, app, type.toUpperCase(), frequency, createdBy, clock.instant())));
    }

    public List<ScheduleView> list() {
        return jobs.findAllByOrderByCreatedAtDesc().stream().map(ScheduleView::from).toList();
    }

    /**
     * Toggle active. With {@code expectedVersion} a stale value is a 409 (the caller saw an older schedule).
     * Without it, a concurrent write is retried against the fresh row, and setting the value that is already
     * in place is a no-op rather than an error.
     */
    public ScheduleView setActive(UUID id, Boolean active, Long expectedVersion) {
        if (active == null) throw ApiException.badRequest("active is required");
        for (int attempt = 1; ; attempt++) {
            ScheduleJob job = jobs.findById(id).orElseThrow(() -> ApiException.notFound("unknown schedule: " + id));
            if (expectedVersion != null && !expectedVersion.equals(job.getVersion())) {
                throw new org.springframework.orm.ObjectOptimisticLockingFailureException(ScheduleJob.class, id);
            }
            if (job.isActive() == active) return ScheduleView.from(job); // already in the requested state
            if (active) job.activate(clock.instant()); else job.deactivate();
            try {
                return ScheduleView.from(jobs.save(job));
            } catch (org.springframework.orm.ObjectOptimisticLockingFailureException e) {
                if (expectedVersion != null || attempt >= 3) throw e;
            }
        }
    }

    /** Re-reads and re-applies a small change when another writer got in between (tick vs run-now vs toggle). */
    private void touch(UUID id, java.util.function.Consumer<ScheduleJob> change) {
        for (int attempt = 1; ; attempt++) {
            ScheduleJob job = jobs.findById(id).orElse(null);
            if (job == null) return;
            change.accept(job);
            try {
                jobs.save(job);
                return;
            } catch (org.springframework.orm.ObjectOptimisticLockingFailureException e) {
                if (attempt >= 3) throw e;
            }
        }
    }

    /** Starts a SCHEDULED run for every due job and advances its schedule. Returns the number started. */
    public int runDue(Instant now) {
        int started = 0;
        for (ScheduleJob job : jobs.findByActiveTrueAndNextRunAtLessThanEqual(now)) {
            try {
                startRun(job, null);
                started++;
            } catch (RunInProgressException e) {
                // Leave nextRunAt due: the slot is retried on the next tick once the active run finishes,
                // instead of being silently skipped to the following interval.
                log.warn("scheduled job '{}' skipped this tick: run {} still in progress; will retry next tick",
                        job.getName(), e.runId());
                continue;
            } catch (RuntimeException e) {
                // Still advance below so a failing job retries on its next slot, not on every tick.
                log.warn("scheduled job '{}' could not start a run: {}", job.getName(), e.getMessage());
            }
            touch(job.getId(), j -> j.ran(now));
        }
        return started;
    }

    /** Run-now: same path as the tick, but nextRunAt is left alone. */
    public RunView runNow(UUID id, String actor) {
        ScheduleJob job = jobs.findById(id).orElseThrow(() -> ApiException.notFound("unknown schedule: " + id));
        RunView run = startRun(job, actor == null || actor.isBlank() ? "anonymous" : actor);
        Instant at = clock.instant();
        touch(job.getId(), j -> j.setLastRunAt(at));
        return run;
    }

    /** Per-schedule lock so the check-then-start below is atomic: two simultaneous callers cannot both start. */
    private final java.util.concurrent.ConcurrentHashMap<UUID, Object> startLocks = new java.util.concurrent.ConcurrentHashMap<>();

    /** A run still PENDING / RUNNING after this long is treated as orphaned (e.g. the backend died mid-run). */
    private static final java.time.Duration ORPHAN_AFTER = java.time.Duration.ofHours(1);

    private RunView startRun(ScheduleJob job, String actor) {
        synchronized (startLocks.computeIfAbsent(job.getId(), k -> new Object())) {
            runs.findFirstByScheduleIdAndStatusInAndCreatedAtAfter(job.getId(),
                    java.util.List.of(SchedulerRun.Status.PENDING, SchedulerRun.Status.RUNNING),
                    clock.instant().minus(ORPHAN_AFTER)).ifPresent(active -> {
                throw new RunInProgressException(job.getName(), active.getId().toString());
            });
            return doStartRun(job, actor);
        }
    }

    private RunView doStartRun(ScheduleJob job, String actor) {
        return scheduler.trigger(new RunRequest(List.of(job.getApplication()), null,
                List.of(job.getSourceSystem()), "schedule:" + job.getName()), Trigger.SCHEDULED, job, actor);
    }

    private static String required(String v, String field) {
        if (v == null || v.isBlank()) throw ApiException.badRequest(field + " is required");
        return v.trim();
    }
}
