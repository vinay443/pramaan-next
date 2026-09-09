package com.pramaan.backend.application;

import com.pramaan.backend.application.ApplicationDtos.UpsertRequest;
import com.pramaan.backend.application.OnboardingCatalog.OnboardingEntry;
import com.pramaan.backend.integrations.IntegrationRegistry;
import com.pramaan.backend.scheduler.SchedulerDtos.RunRequest;
import com.pramaan.backend.scheduler.SchedulerDtos.RunView;
import com.pramaan.backend.scheduler.SchedulerService;
import com.pramaan.backend.scheduler.domain.SchedulerRun.Trigger;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use Case 12 — applies the {@link OnboardingCatalog}. Idempotent: each entry is
 * upserted through {@link ApplicationService} (the existing application store) and
 * optionally an initial evidence collection is kicked off through the existing
 * {@link SchedulerService} — no new application store, no new ingestion path.
 */
@Service
public class OnboardingService {

    private static final Logger log = LoggerFactory.getLogger(OnboardingService.class);

    private final OnboardingCatalog catalog;
    private final ApplicationService applications;
    private final ApplicationRepository applicationRepo;
    private final IntegrationRegistry integrations;
    private final SchedulerService scheduler;

    public OnboardingService(OnboardingCatalog catalog, ApplicationService applications,
                             ApplicationRepository applicationRepo, IntegrationRegistry integrations,
                             SchedulerService scheduler) {
        this.catalog = catalog;
        this.applications = applications;
        this.applicationRepo = applicationRepo;
        this.integrations = integrations;
        this.scheduler = scheduler;
    }

    public record PlanItem(String slug, String name, String criticality, List<String> frameworks,
                           List<String> sources, List<String> unknownSources, String status) {}

    public record OnboardingPlan(int total, int toCreate, int existing, List<PlanItem> items) {}

    public record OnboardingResult(int applied, int created, int updated, List<String> slugs,
                                   String collectionRunId) {}

    @Transactional(readOnly = true)
    public OnboardingPlan plan() {
        Set<String> known = Set.copyOf(integrations.available());
        List<PlanItem> items = new ArrayList<>();
        int toCreate = 0;
        for (OnboardingEntry e : catalog.entries()) {
            boolean exists = applicationRepo.findBySlug(e.slug())
                    .map(ApplicationEntity::isActive).orElse(false);
            if (!exists) {
                toCreate++;
            }
            List<String> unknown = e.sources() == null ? List.of()
                    : e.sources().stream().filter(s -> !known.contains(s.toUpperCase())).toList();
            items.add(new PlanItem(e.slug(), e.name(), e.criticality(),
                    e.frameworks() == null ? List.of() : e.frameworks(),
                    e.sources() == null ? List.of() : e.sources(),
                    unknown, exists ? "EXISTS" : "NEW"));
        }
        return new OnboardingPlan(items.size(), toCreate, items.size() - toCreate, items);
    }

    /**
     * Onboard every catalogued application.
     *
     * @param collectEvidence when true, start one scheduler run over the union of
     *                        the catalogued sources for the onboarded applications
     */
    @Transactional
    public OnboardingResult apply(boolean collectEvidence) {
        return apply(null, collectEvidence);
    }

    /**
     * Onboard the catalogued applications named in {@code onlySlugs} (null/empty = all).
     * Unknown slugs are ignored. Idempotent — re-onboards a previously deboarded app.
     */
    @Transactional
    public OnboardingResult apply(java.util.Collection<String> onlySlugs, boolean collectEvidence) {
        Set<String> filter = onlySlugs == null || onlySlugs.isEmpty() ? null
                : onlySlugs.stream().map(String::trim).filter(s -> !s.isBlank()).collect(Collectors.toSet());
        int created = 0;
        int updated = 0;
        List<String> slugs = new ArrayList<>();
        for (OnboardingEntry e : catalog.entries()) {
            if (filter != null && !filter.contains(e.slug())) {
                continue;
            }
            boolean existed = applicationRepo.findBySlug(e.slug())
                    .map(ApplicationEntity::isActive).orElse(false);
            applications.upsert(new UpsertRequest(e.slug(), e.name(), e.businessUnit(),
                    e.criticality(), e.owner(), e.technology()));
            slugs.add(e.slug());
            if (existed) {
                updated++;
            } else {
                created++;
            }
        }
        log.info("onboarding applied: {} created, {} updated", created, updated);

        String runId = null;
        if (collectEvidence && !slugs.isEmpty()) {
            Set<String> known = Set.copyOf(integrations.available());
            List<String> onboarded = List.copyOf(slugs);
            List<String> sources = catalog.entries().stream()
                    .filter(e -> onboarded.contains(e.slug()))
                    .flatMap(e -> e.sources() == null ? java.util.stream.Stream.<String>empty()
                            : e.sources().stream())
                    .map(String::toUpperCase)
                    .filter(known::contains)
                    .distinct()
                    .collect(Collectors.toList());
            if (!sources.isEmpty()) {
                RunView run = scheduler.trigger(new RunRequest(slugs, List.of(), sources, "onboarding"),
                        Trigger.MANUAL);
                runId = run.runId();
            }
        }
        return new OnboardingResult(slugs.size(), created, updated, slugs, runId);
    }
}
