package com.pramaan.backend.application;

import com.pramaan.backend.application.ApplicationDtos.ApplicationView;
import com.pramaan.backend.application.ApplicationDtos.UpsertRequest;
import com.pramaan.backend.common.ApiException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApplicationService {

    private static final Set<String> CRITICALITIES = Set.of("LOW", "MEDIUM", "HIGH", "CRITICAL");

    private final ApplicationRepository repository;
    private final Clock clock;

    public ApplicationService(ApplicationRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ApplicationView> list() {
        return repository.findAllByOrderBySlugAsc().stream().map(ApplicationView::from).toList();
    }

    @Transactional(readOnly = true)
    public ApplicationView get(String slug) {
        return ApplicationView.from(require(slug));
    }

    public ApplicationEntity require(String slug) {
        return repository.findBySlug(slug)
                .orElseThrow(() -> ApiException.notFound("Unknown application: " + slug));
    }

    @Transactional
    public ApplicationView upsert(UpsertRequest req) {
        Instant now = clock.instant();
        String criticality = normalizeCriticality(req.criticality());
        ApplicationEntity e = repository.findBySlug(req.slug())
                .orElseGet(() -> new ApplicationEntity(UUID.randomUUID(), req.slug(), req.name(), now));
        e.setName(req.name());
        e.setBusinessUnit(req.businessUnit());
        e.setCriticality(criticality);
        e.setOwner(req.owner());
        e.setTechnology(req.technology() == null ? null : String.join(",", req.technology()));
        e.setAutoCreated(false);
        e.setActive(true);
        e.touch(now);
        return ApplicationView.from(repository.save(e));
    }

    /** Onboard (active=true) or deboard (active=false) an existing application. */
    @Transactional
    public ApplicationView setActive(String slug, boolean active) {
        ApplicationEntity e = require(slug);
        e.setActive(active);
        e.touch(clock.instant());
        return ApplicationView.from(repository.save(e));
    }

    /** True when at least one application is currently onboarded. */
    @Transactional(readOnly = true)
    public boolean anyOnboarded() {
        return repository.existsByActiveTrue();
    }

    /** Used by ingestion/scheduler so evidence for an unknown app is never silently dropped. */
    @Transactional
    public ApplicationEntity getOrAutoCreate(String slug) {
        return repository.findBySlug(slug).orElseGet(() -> {
            Instant now = clock.instant();
            ApplicationEntity e = new ApplicationEntity(UUID.randomUUID(), slug, slug, now);
            e.setAutoCreated(true);
            return repository.save(e);
        });
    }

    private String normalizeCriticality(String value) {
        if (value == null || value.isBlank()) {
            return "MEDIUM";
        }
        String v = value.trim().toUpperCase();
        if (!CRITICALITIES.contains(v)) {
            throw ApiException.badRequest("criticality must be one of " + CRITICALITIES);
        }
        return v;
    }
}
