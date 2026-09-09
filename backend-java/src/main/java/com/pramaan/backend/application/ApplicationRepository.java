package com.pramaan.backend.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicationRepository extends JpaRepository<ApplicationEntity, UUID> {

    Optional<ApplicationEntity> findBySlug(String slug);

    boolean existsBySlug(String slug);

    boolean existsByActiveTrue();

    boolean existsBySlugAndActiveTrue(String slug);

    List<ApplicationEntity> findAllByOrderBySlugAsc();

    List<ApplicationEntity> findByActiveTrueOrderBySlugAsc();
}
