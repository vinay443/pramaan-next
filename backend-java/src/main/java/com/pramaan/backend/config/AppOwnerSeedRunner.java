package com.pramaan.backend.config;

import com.pramaan.backend.appowner.domain.ApplicationOwner;
import com.pramaan.backend.appowner.repo.ApplicationOwnerRepository;
import com.pramaan.backend.application.ApplicationRepository;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Seeds which demo applications the {@code app.owner.demo} persona (from
 * {@link ApprovalRbacSeedRunner}) is authorized for — 2 of the 3 demo apps, so
 * cross-application scoping is actually exercised (payments is left unowned).
 * Runs after the RBAC seeder (users must exist first) and after the demo
 * applications are seeded by {@code V2__seed_reference.sql}; idempotent.
 */
@Component
@Order(51)
@ConditionalOnProperty(prefix = "pramaan.seed", name = "approval-rbac-enabled",
        havingValue = "true", matchIfMissing = true)
public class AppOwnerSeedRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AppOwnerSeedRunner.class);
    private static final String DEMO_APP_OWNER = "app.owner.demo";
    private static final List<String> OWNED_SLUGS = List.of("net-banking", "mobile-banking");

    private final ApplicationOwnerRepository owners;
    private final ApplicationRepository applications;
    private final Clock clock;

    public AppOwnerSeedRunner(ApplicationOwnerRepository owners, ApplicationRepository applications, Clock clock) {
        this.owners = owners;
        this.applications = applications;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (owners.count() > 0) {
            return;
        }
        var now = clock.instant();
        for (String slug : OWNED_SLUGS) {
            if (!applications.existsBySlug(slug)) {
                continue;
            }
            owners.save(new ApplicationOwner(UUID.randomUUID(), slug, DEMO_APP_OWNER, now));
        }
        log.info("seeded application ownership for {}: {}", DEMO_APP_OWNER, OWNED_SLUGS);
    }
}
