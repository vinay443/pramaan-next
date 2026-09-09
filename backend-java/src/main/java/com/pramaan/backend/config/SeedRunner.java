package com.pramaan.backend.config;

import com.pramaan.backend.application.ApplicationRepository;
import com.pramaan.backend.application.OnboardingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Seeds demo applications on startup when the table is empty — by applying the
 * Use Case 12 onboarding catalogue (single source of truth), not a hardcoded list.
 * Idempotent; complements Flyway V2.
 */
@Component
@ConditionalOnProperty(prefix = "pramaan.seed", name = "applications-enabled", havingValue = "true")
public class SeedRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SeedRunner.class);

    private final ApplicationRepository repository;
    private final OnboardingService onboarding;

    public SeedRunner(ApplicationRepository repository, OnboardingService onboarding) {
        this.repository = repository;
        this.onboarding = onboarding;
    }

    @Override
    public void run(org.springframework.boot.ApplicationArguments args) {
        if (repository.count() > 0) {
            return;
        }
        var result = onboarding.apply(false);
        log.info("seeded {} applications from the onboarding catalogue", result.applied());
    }
}
