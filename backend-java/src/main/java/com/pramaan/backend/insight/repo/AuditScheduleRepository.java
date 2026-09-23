package com.pramaan.backend.insight.repo;

import com.pramaan.backend.insight.domain.AuditSchedule;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditScheduleRepository extends JpaRepository<AuditSchedule, UUID> {

    List<AuditSchedule> findAllByOrderByScheduledDateAsc();
}
