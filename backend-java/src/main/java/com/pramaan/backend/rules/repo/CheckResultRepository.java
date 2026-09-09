package com.pramaan.backend.rules.repo;

import com.pramaan.backend.rules.domain.CheckResult;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CheckResultRepository
        extends JpaRepository<CheckResult, UUID>, JpaSpecificationExecutor<CheckResult> {

    Optional<CheckResult> findByEvidenceRecordIdAndCheckId(UUID evidenceRecordId, String checkId);

    List<CheckResult> findByEvidenceRecordId(UUID evidenceRecordId);
}
