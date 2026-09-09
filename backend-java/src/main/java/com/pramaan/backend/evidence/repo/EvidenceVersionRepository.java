package com.pramaan.backend.evidence.repo;

import com.pramaan.backend.evidence.domain.EvidenceRecord;
import com.pramaan.backend.evidence.domain.EvidenceVersion;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface EvidenceVersionRepository extends JpaRepository<EvidenceVersion, UUID> {

    List<EvidenceVersion> findByRecordIdOrderByVersionNumberAsc(UUID recordId);

    @Query("select v from EvidenceVersion v where v.versionNumber = v.record.currentVersion")
    List<EvidenceVersion> findAllLatest();

    @Query("""
            select v from EvidenceVersion v
            where v.versionNumber = v.record.currentVersion and v.collectedAt < :cutoff
            order by v.collectedAt asc, v.id asc
            """)
    List<EvidenceVersion> findLatestOlderThan(Instant cutoff);

    @Query("select v.sha256 as sha, count(v) as cnt from EvidenceVersion v group by v.sha256 having count(v) > 1")
    List<Object[]> findDuplicateHashes();

    /** Records whose CURRENT version has exactly this content hash (exact-duplicate detection). */
    @Query("""
            select v.record from EvidenceVersion v
            where v.sha256 = :sha256 and v.versionNumber = v.record.currentVersion
            order by v.record.createdAt asc
            """)
    List<EvidenceRecord> findRecordsWithCurrentSha256(String sha256);
}
