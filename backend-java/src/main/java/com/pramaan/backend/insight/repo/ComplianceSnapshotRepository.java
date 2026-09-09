package com.pramaan.backend.insight.repo;

import com.pramaan.backend.insight.domain.ComplianceSnapshot;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ComplianceSnapshotRepository extends JpaRepository<ComplianceSnapshot, UUID> {

    List<ComplianceSnapshot> findAllByOrderByTakenAtAsc();
}
