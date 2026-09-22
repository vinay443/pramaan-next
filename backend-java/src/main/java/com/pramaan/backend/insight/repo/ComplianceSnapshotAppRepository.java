package com.pramaan.backend.insight.repo;

import com.pramaan.backend.insight.domain.ComplianceSnapshotApp;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ComplianceSnapshotAppRepository extends JpaRepository<ComplianceSnapshotApp, UUID> {

    List<ComplianceSnapshotApp> findBySnapshotIdIn(Collection<UUID> snapshotIds);

    @Query("select distinct a.snapshotId from ComplianceSnapshotApp a")
    List<UUID> findSnapshotIdsWithApps();
}
