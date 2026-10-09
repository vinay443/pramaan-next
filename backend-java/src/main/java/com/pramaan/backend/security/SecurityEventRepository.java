package com.pramaan.backend.security;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SecurityEventRepository extends JpaRepository<SecurityEvent, UUID> {

    List<SecurityEvent> findAllByOrderByOccurredAtDesc(Pageable page);

    List<SecurityEvent> findByEventTypeOrderByOccurredAtDesc(SecurityEvent.Type type, Pageable page);
}
