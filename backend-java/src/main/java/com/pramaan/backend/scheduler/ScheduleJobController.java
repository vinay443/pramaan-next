package com.pramaan.backend.scheduler;

import com.pramaan.backend.scheduler.SchedulerDtos.CreateScheduleRequest;
import com.pramaan.backend.scheduler.SchedulerDtos.RunView;
import com.pramaan.backend.scheduler.SchedulerDtos.ScheduleOptions;
import com.pramaan.backend.scheduler.SchedulerDtos.ScheduleView;
import com.pramaan.backend.scheduler.SchedulerDtos.ToggleScheduleRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/schedules")
public class ScheduleJobController {

    private final ScheduleJobService service;

    public ScheduleJobController(ScheduleJobService service) {
        this.service = service;
    }

    @GetMapping("/options")
    public ScheduleOptions options() {
        return service.options();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ScheduleView create(@RequestBody(required = false) CreateScheduleRequest req) {
        return service.create(req);
    }

    @GetMapping
    public List<ScheduleView> list() {
        return service.list();
    }

    @PostMapping("/{id}/run-now")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public RunView runNow(@PathVariable UUID id,
                          @RequestHeader(value = "X-Pramaan-User", required = false) String user) {
        return service.runNow(id, user);
    }

    @PatchMapping("/{id}")
    public ScheduleView toggle(@PathVariable UUID id, @RequestBody(required = false) ToggleScheduleRequest req) {
        return service.setActive(id, req == null ? null : req.active(), req == null ? null : req.version());
    }
}
