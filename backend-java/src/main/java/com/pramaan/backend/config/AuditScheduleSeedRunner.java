package com.pramaan.backend.config;

import com.pramaan.backend.application.ApplicationService;
import com.pramaan.backend.insight.domain.AuditSchedule;
import com.pramaan.backend.insight.repo.AuditScheduleRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Seeds a handful of realistic upcoming audits (spread over the next ~90 days) when the
 * audit_schedule table is empty — there is no real audit-calendar source locally.
 */
@Component
@Order(110)
@ConditionalOnProperty(prefix = "pramaan.seed", name = "audit-schedule-enabled", havingValue = "true",
        matchIfMissing = true)
public class AuditScheduleSeedRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AuditScheduleSeedRunner.class);

    private record Seed(String framework, String auditName, int daysOut) {}

    private static final List<Seed> SEEDS = List.of(
            new Seed("PCI_DSS", "PCI DSS Q1 Recertification Audit", 18),
            new Seed("C-SITE", "Cyber Security Baseline (C-SITE) Audit", 34),
            new Seed("ITPP", "IT Policy & Procedures Compliance Review", 52),
            new Seed("VAPT", "Annual VAPT Re-assessment", 76));

    private final AuditScheduleRepository schedules;
    private final ApplicationService applications;

    public AuditScheduleSeedRunner(AuditScheduleRepository schedules, ApplicationService applications) {
        this.schedules = schedules;
        this.applications = applications;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (schedules.count() > 0) {
            return;
        }
        List<String> slugs = applications.list().stream().map(a -> a.slug()).toList();
        if (slugs.isEmpty()) {
            return;
        }
        LocalDate today = LocalDate.now();
        List<AuditSchedule> rows = new ArrayList<>();
        for (int i = 0; i < SEEDS.size(); i++) {
            Seed sd = SEEDS.get(i);
            rows.add(new AuditSchedule(sd.framework(), sd.auditName(), today.plusDays(sd.daysOut()), scopeFor(slugs, i)));
        }
        schedules.saveAll(rows);
        log.info("seeded {} upcoming audit schedule rows", rows.size());
    }

    /** Rotates through the known applications so each seeded audit covers up to 3 of them
     *  (or all of them, if fewer than 3 exist) rather than every app in every audit. */
    private static List<String> scopeFor(List<String> slugs, int index) {
        if (slugs.size() <= 2) {
            return slugs;
        }
        List<String> out = new ArrayList<>();
        for (int k = 0; k < Math.min(3, slugs.size()); k++) {
            out.add(slugs.get((index + k) % slugs.size()));
        }
        return out;
    }
}
