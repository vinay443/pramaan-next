package com.pramaan.backend.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ApplicationRepository extends JpaRepository<ApplicationEntity, UUID> {

    /** Row lock held until the caller's transaction ends: serialises evidence-name sequence allocation per application. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from ApplicationEntity a where a.slug = :slug")
    Optional<ApplicationEntity> lockBySlug(@Param("slug") String slug);

    Optional<ApplicationEntity> findBySlug(String slug);

    boolean existsBySlug(String slug);

    boolean existsByActiveTrue();

    boolean existsBySlugAndActiveTrue(String slug);

    List<ApplicationEntity> findAllByOrderBySlugAsc();

    List<ApplicationEntity> findByActiveTrueOrderBySlugAsc();
}
