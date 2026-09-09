package com.pramaan.backend.evidence.repo;

import com.pramaan.backend.evidence.domain.EvidenceLifecycleState;
import com.pramaan.backend.evidence.domain.EvidenceRecord;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface EvidenceRecordRepository
        extends JpaRepository<EvidenceRecord, UUID>, JpaSpecificationExecutor<EvidenceRecord> {

    Optional<EvidenceRecord> findByEvidenceKey(String evidenceKey);

    long countByLifecycleState(EvidenceLifecycleState state);
}
