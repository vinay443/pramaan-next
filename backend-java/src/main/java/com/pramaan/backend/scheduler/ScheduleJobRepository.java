package com.pramaan.backend.scheduler;

import com.pramaan.backend.scheduler.domain.ScheduleJob;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScheduleJobRepository extends JpaRepository<ScheduleJob, UUID> {

    List<ScheduleJob> findAllByOrderByCreatedAtDesc();

    List<ScheduleJob> findByActiveTrueAndNextRunAtLessThanEqual(Instant now);

    long countByActiveTrue();
}
