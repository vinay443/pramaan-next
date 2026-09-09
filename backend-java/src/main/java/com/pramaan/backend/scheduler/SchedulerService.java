package com.pramaan.backend.scheduler;

import com.pramaan.backend.application.ApplicationRepository;
import com.pramaan.backend.common.ApiException;
import com.pramaan.backend.common.PageResponse;
import com.pramaan.backend.config.PramaanProperties;
import com.pramaan.backend.scheduler.SchedulerDtos.RunRequest;
import com.pramaan.backend.scheduler.SchedulerDtos.RunView;
import com.pramaan.backend.scheduler.domain.SchedulerRun;
import com.pramaan.backend.scheduler.domain.SchedulerRun.Status;
import com.pramaan.backend.scheduler.domain.SchedulerRun.Trigger;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SchedulerService {

    private final SchedulerRunRepository runs;
    private final SchedulerRunExecutor executor;
    private final ApplicationRepository applications;
    private final Clock clock;
    private final List<String> defaultSources;
    private final boolean dispatch;

    public SchedulerService(SchedulerRunRepository runs, SchedulerRunExecutor executor,
                            ApplicationRepository applications, Clock clock, PramaanProperties props) {
        this.runs = runs;
        this.executor = executor;
        this.applications = applications;
        this.clock = clock;
        this.defaultSources = props.scheduler() != null && props.scheduler().defaultSources() != null
                ? props.scheduler().defaultSources()
                : List.of();
        this.dispatch = props.scheduler() == null || props.scheduler().dispatch();
    }

    /** Create a run and hand it to the async executor. Returns immediately with status PENDING. */
    public RunView trigger(RunRequest req, Trigger trigger) {
        if (!applications.existsByActiveTrue()) {
            throw ApiException.badRequest(
                    "No applications onboarded — onboard at least one application before running the scheduler.");
        }
        List<String> named = req.applications() == null ? List.of() : req.applications().stream()
                .map(s -> s == null ? "" : s.trim()).filter(s -> !s.isBlank()).distinct().toList();
        List<String> notOnboarded = named.stream()
                .filter(s -> !applications.existsBySlugAndActiveTrue(s)).toList();
        if (!notOnboarded.isEmpty()) {
            throw ApiException.badRequest(notOnboarded.stream()
                    .map(s -> "Application '" + s + "' is not onboarded — onboard it before running the scheduler.")
                    .collect(java.util.stream.Collectors.joining(" ")));
        }
        List<String> sources = req.sources() == null || req.sources().isEmpty()
                ? defaultSources : req.sources();
        if (sources.isEmpty()) {
            throw ApiException.badRequest("no sources given and no default sources configured");
        }
        SchedulerRun run = new SchedulerRun(UUID.randomUUID(), trigger, req.requestedBy(),
                req.applications(), req.frameworks(), sources, clock.instant());
        runs.save(run);
        if (dispatch) {
            executor.executeAsync(run.getId());
        }
        return RunView.from(run);
    }

    public RunView retry(UUID runId) {
        SchedulerRun previous = runs.findById(runId)
                .orElseThrow(() -> ApiException.notFound("unknown run: " + runId));
        if (previous.getStatus() == Status.RUNNING || previous.getStatus() == Status.PENDING) {
            throw ApiException.conflict("run " + runId + " is still " + previous.getStatus());
        }
        return trigger(new SchedulerDtos.RunRequest(
                splitList(previous.getApplications()),
                splitList(previous.getFrameworks()),
                splitList(previous.getSources()),
                previous.getRequestedBy()), Trigger.MANUAL);
    }

    @Transactional(readOnly = true)
    public RunView get(UUID runId) {
        return runs.findById(runId).map(RunView::from)
                .orElseThrow(() -> ApiException.notFound("unknown run: " + runId));
    }

    @Transactional(readOnly = true)
    public PageResponse<RunView> list(int page, int size) {
        return PageResponse.of(runs.findAllByOrderByCreatedAtDesc(
                PageRequest.of(Math.max(page, 0), size <= 0 ? 20 : Math.min(size, 200)))
                .map(RunView::from));
    }

    private static List<String> splitList(String csv) {
        return csv == null || csv.isBlank() ? List.of() : List.of(csv.split("\\s*,\\s*"));
    }
}
