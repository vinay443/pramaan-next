package com.pramaan.backend.appowner.repo;

import com.pramaan.backend.appowner.domain.AppOwnerAuditLogEntry;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppOwnerAuditLogRepository extends JpaRepository<AppOwnerAuditLogEntry, UUID> {
}
