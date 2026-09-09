package com.pramaan.backend.scheduler;

import com.pramaan.backend.scheduler.SchedulerDtos.RunRequest;
import com.pramaan.backend.scheduler.domain.SchedulerRun.Trigger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Optional background trigger. Disabled unless {@code pramaan.scheduler.auto-enabled=true};
 * runs are normally started via {@code POST /api/v1/scheduler/runs}.
 */
@Component
@ConditionalOnProperty(prefix = "pramaan.scheduler", name = "auto-enabled", havingValue = "true")
public class ScheduledCollectionTrigger {

    private static final Logger log = LoggerFactory.getLogger(ScheduledCollectionTrigger.class);

    private final SchedulerService scheduler;

    public ScheduledCollectionTrigger(SchedulerService scheduler) {
        this.scheduler = scheduler;
    }

    @Scheduled(cron = "${pramaan.scheduler.cron:0 0 * * * *}")
    public void collect() {
        log.info("scheduled collection tick");
        scheduler.trigger(new RunRequest(null, null, null, "scheduler"), Trigger.SCHEDULED);
    }
}
