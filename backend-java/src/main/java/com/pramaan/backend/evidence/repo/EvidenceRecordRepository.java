package com.pramaan.backend.evidence.repo;

import com.pramaan.backend.evidence.domain.EvidenceLifecycleState;
import com.pramaan.backend.evidence.domain.EvidenceRecord;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface EvidenceRecordRepository
        extends JpaRepository<EvidenceRecord, UUID>, JpaSpecificationExecutor<EvidenceRecord> {

    Optional<EvidenceRecord> findByEvidenceKey(String evidenceKey);

    /** Cheap change marker for derived-result caches: moves on every ingest, new version and lifecycle change. */
    @Query("select max(r.updatedAt) from EvidenceRecord r")
    java.time.Instant maxUpdatedAt();

    /**
     * Titles of the application's other records that start with a name prefix ({@code pattern} from
     * {@code EvidenceNaming.titlePattern}). All the sequence allocator needs; avoids loading every record.
     */
    @Query("select r.title from EvidenceRecord r where r.applicationSlug = :slug and r.id <> :excludeId "
            + "and r.title like :pattern escape '!'")
    List<String> titlesWithPrefix(@org.springframework.data.repository.query.Param("slug") String slug,
                                  @org.springframework.data.repository.query.Param("excludeId") UUID excludeId,
                                  @org.springframework.data.repository.query.Param("pattern") String pattern);

    long countByLifecycleState(EvidenceLifecycleState state);

    /** App Owner dashboard — scope evidence/closure/lifecycle metrics to one application. */
    List<EvidenceRecord> findByApplicationSlug(String applicationSlug);
}
