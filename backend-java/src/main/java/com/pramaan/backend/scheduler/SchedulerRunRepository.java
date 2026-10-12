package com.pramaan.backend.scheduler;

import com.pramaan.backend.scheduler.domain.SchedulerRun;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SchedulerRunRepository extends JpaRepository<SchedulerRun, UUID> {

    Page<SchedulerRun> findAllByOrderByCreatedAtDesc(Pageable pageable);

    java.util.List<SchedulerRun> findByStatusIn(java.util.Collection<SchedulerRun.Status> statuses);

    /** A run of this schedule that is queued or running (and not an orphan older than {@code since}). */
    java.util.Optional<SchedulerRun> findFirstByScheduleIdAndStatusInAndCreatedAtAfter(
            UUID scheduleId, java.util.Collection<SchedulerRun.Status> statuses, java.time.Instant since);
}
