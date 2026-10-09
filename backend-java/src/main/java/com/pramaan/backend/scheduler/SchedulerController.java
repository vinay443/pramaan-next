package com.pramaan.backend.scheduler;

import com.pramaan.backend.common.PageResponse;
import com.pramaan.backend.integrations.IntegrationRegistry;
import com.pramaan.backend.security.SecurityEventService;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/scheduler")
public class SchedulerController {

    private final SchedulerService service;
    private final IntegrationRegistry integrations;

    private final SecurityEventService security;

    public SchedulerController(SchedulerService service, IntegrationRegistry integrations,
                               SecurityEventService security) {
        this.security = security;
        this.service = service;
        this.integrations = integrations;
    }

    @GetMapping("/sources")
    public List<String> sources() {
        return integrations.available();
    }

    @PostMapping("/runs")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public RunView start(@RequestBody(required = false) RunRequest req,
                         @RequestHeader(value = "X-Pramaan-User", required = false) String user) {
        if (req != null) {
            security.requireSafeItems("applications", req.applications());
            security.requireSafeItems("sources", req.sources());
            security.requireSafeItems("frameworks", req.frameworks());
            if (req.requestedBy() != null) security.requireSafe("requestedBy", req.requestedBy());
        }
        return service.trigger(req == null
                ? new RunRequest(null, null, null, null) : req, Trigger.MANUAL, user);
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
    public RunView retry(@PathVariable UUID runId,
                         @RequestHeader(value = "X-Pramaan-User", required = false) String user) {
        return service.retry(runId, user);
    }
}
