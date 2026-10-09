package com.pramaan.backend.retention;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RetentionRunRepository extends JpaRepository<RetentionRun, UUID> {

    List<RetentionRun> findAllByOrderByStartedAtDesc(Pageable page);
}
