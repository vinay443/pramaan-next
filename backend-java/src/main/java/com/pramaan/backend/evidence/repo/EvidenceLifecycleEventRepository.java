package com.pramaan.backend.evidence.repo;

import com.pramaan.backend.evidence.domain.EvidenceLifecycleEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvidenceLifecycleEventRepository extends JpaRepository<EvidenceLifecycleEvent, UUID> {

    List<EvidenceLifecycleEvent> findByEvidenceRecordIdOrderByOccurredAtAsc(UUID evidenceRecordId);
}
