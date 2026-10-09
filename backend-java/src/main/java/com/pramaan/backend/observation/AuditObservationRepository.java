package com.pramaan.backend.observation;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditObservationRepository extends JpaRepository<AuditObservation, UUID> {

    List<AuditObservation> findByEvidenceIdOrderByCreatedAtDesc(UUID evidenceId);

    List<AuditObservation> findByApplicationAndStatus(String application, AuditObservation.Status status);

    List<AuditObservation> findAllByOrderByCreatedAtDesc();
}
