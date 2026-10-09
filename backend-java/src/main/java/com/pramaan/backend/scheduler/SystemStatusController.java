package com.pramaan.backend.scheduler;

import com.pramaan.backend.config.PramaanProperties;
import java.time.Clock;
import java.time.Instant;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Restart-durability check: when this backend instance started and how many schedules are active. */
@RestController
@RequestMapping("/api/v1/system")
public class SystemStatusController {

    public record SystemStatus(Instant serverStartedAt, Instant now, long activeSchedules, int slaSeconds) {}

    private final Instant startedAt;
    private final Clock clock;
    private final ScheduleJobRepository jobs;
    private final int slaSeconds;

    public SystemStatusController(Clock clock, ScheduleJobRepository jobs, PramaanProperties props) {
        this.slaSeconds = props.scheduler() == null ? 60 : props.scheduler().slaSecondsOrDefault();
        this.clock = clock;
        this.jobs = jobs;
        this.startedAt = clock.instant();
    }

    @GetMapping("/status")
    public SystemStatus status() {
        return new SystemStatus(startedAt, clock.instant(), jobs.countByActiveTrue(), slaSeconds);
    }
}
