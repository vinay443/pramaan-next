package com.pramaan.backend.scheduler;

import com.pramaan.backend.common.PageResponse;
import com.pramaan.backend.integrations.IntegrationRegistry;
import com.pramaan.backend.scheduler.SchedulerDtos.RunRequest;
import com.pramaan.backend.scheduler.SchedulerDtos.RunView;
import com.pramaan.backend.scheduler.domain.SchedulerRun.Trigger;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/scheduler")
public class SchedulerController {

    private final SchedulerService service;
    private final IntegrationRegistry integrations;

    public SchedulerController(SchedulerService service, IntegrationRegistry integrations) {
        this.service = service;
        this.integrations = integrations;
    }

    @GetMapping("/sources")
    public List<String> sources() {
        return integrations.available();
    }

    @PostMapping("/runs")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public RunView start(@RequestBody(required = false) RunRequest req) {
        return service.trigger(req == null
                ? new RunRequest(null, null, null, null) : req, Trigger.MANUAL);
    }

    @GetMapping("/runs")
    public PageResponse<RunView> list(@RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "20") int size) {
        return service.list(page, size);
    }

    @GetMapping("/runs/{runId}")
    public RunView get(@PathVariable UUID runId) {
        return service.get(runId);
    }

    @PostMapping("/runs/{runId}/retry")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public RunView retry(@PathVariable UUID runId) {
        return service.retry(runId);
    }
}
