package com.pramaan.backend.config;

import com.pramaan.backend.evidence.domain.ApprovalRoleScope;
import com.pramaan.backend.evidence.domain.ApprovalUser;
import com.pramaan.backend.evidence.repo.ApprovalRoleScopeRepository;
import com.pramaan.backend.evidence.repo.ApprovalUserRepository;
import java.time.Clock;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Seeds the 5 evidence-approval RBAC roles (scope config — required for
 * {@code EvidenceApprovalAuthorizer} to function at all, so unlike the optional
 * demo-data seeders this defaults on everywhere) and one demo user per role, when
 * the roles table is empty. Idempotent; complements Flyway V9.
 */
@Component
@Order(50)
@ConditionalOnProperty(prefix = "pramaan.seed", name = "approval-rbac-enabled",
        havingValue = "true", matchIfMissing = true)
public class ApprovalRbacSeedRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ApprovalRbacSeedRunner.class);

    private final ApprovalRoleScopeRepository roleScopes;
    private final ApprovalUserRepository users;
    private final Clock clock;

    public ApprovalRbacSeedRunner(ApprovalRoleScopeRepository roleScopes, ApprovalUserRepository users,
                                  Clock clock) {
        this.roleScopes = roleScopes;
        this.users = users;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (roleScopes.count() > 0) {
            return;
        }
        roleScopes.saveAll(List.of(
                new ApprovalRoleScope("APP_OWNER", null, null, true, false,
                        "Submits evidence for review; cannot approve or reject"),
                new ApprovalRoleScope("AUDITOR", null, null, false, true,
                        "Approves or rejects evidence for any framework"),
                new ApprovalRoleScope("ISG_OFFICER", "ISG", "VAPT", false, true,
                        "Approves or rejects ISG evidence, or VAPT-family evidence in any framework"),
                new ApprovalRoleScope("PCIDSS_AUDITOR", "PCI_DSS", null, false, true,
                        "Approves or rejects PCI DSS evidence only"),
                new ApprovalRoleScope("DPSC_AUDITOR", "DPSC", null, false, true,
                        "Approves or rejects DPSC evidence only")));

        var now = clock.instant();
        users.saveAll(List.of(
                new ApprovalUser("app.owner.demo", "Demo App Owner", "APP_OWNER", now),
                new ApprovalUser("auditor.demo", "Demo Generic Auditor", "AUDITOR", now),
                new ApprovalUser("isg.officer.demo", "Demo ISG Officer", "ISG_OFFICER", now),
                new ApprovalUser("pcidss.auditor.demo", "Demo PCI DSS Auditor", "PCIDSS_AUDITOR", now),
                new ApprovalUser("dpsc.auditor.demo", "Demo DPSC Auditor", "DPSC_AUDITOR", now)));

        log.info("seeded 5 evidence-approval RBAC roles and 5 demo users");
    }
}
