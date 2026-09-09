package com.pramaan.backend.scheduler;

import com.pramaan.backend.scheduler.domain.SchedulerRun;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SchedulerRunRepository extends JpaRepository<SchedulerRun, UUID> {

    Page<SchedulerRun> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
