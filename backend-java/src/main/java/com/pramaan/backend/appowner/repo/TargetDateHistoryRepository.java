package com.pramaan.backend.appowner.repo;

import com.pramaan.backend.appowner.domain.TargetDateHistoryEntry;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TargetDateHistoryRepository extends JpaRepository<TargetDateHistoryEntry, UUID> {

    List<TargetDateHistoryEntry> findByEvidenceRecordIdOrderByCreatedAtAsc(UUID evidenceRecordId);
}
