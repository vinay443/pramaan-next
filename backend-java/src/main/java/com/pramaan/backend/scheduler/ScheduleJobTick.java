package com.pramaan.backend.scheduler;

import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Every 30s starts runs for due schedule jobs. Off only when {@code pramaan.scheduler.jobs-tick=false}. */
@Component
@ConditionalOnProperty(prefix = "pramaan.scheduler", name = "jobs-tick", havingValue = "true", matchIfMissing = true)
public class ScheduleJobTick {

    private final ScheduleJobService jobs;
    private final Clock clock;

    public ScheduleJobTick(ScheduleJobService jobs, Clock clock) {
        this.jobs = jobs;
        this.clock = clock;
    }

    @Scheduled(fixedDelay = 30_000)
    public void tick() {
        jobs.runDue(clock.instant());
    }
}
