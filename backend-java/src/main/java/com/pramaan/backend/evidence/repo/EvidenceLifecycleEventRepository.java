package com.pramaan.backend.evidence.repo;

import com.pramaan.backend.evidence.domain.EvidenceLifecycleEvent;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvidenceLifecycleEventRepository extends JpaRepository<EvidenceLifecycleEvent, UUID> {

    List<EvidenceLifecycleEvent> findByEvidenceRecordIdOrderByOccurredAtAsc(UUID evidenceRecordId);

    boolean existsByEvidenceRecordIdAndActionAndNoteContaining(UUID evidenceRecordId, String action, String note);

    /** App Owner dashboard — closure/rejection metrics scoped to one application's evidence. */
    List<EvidenceLifecycleEvent> findByEvidenceRecordIdIn(Collection<UUID> evidenceRecordIds);
}
