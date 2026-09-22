package com.pramaan.backend.config;

import com.pramaan.backend.insight.TrendService;
import com.pramaan.backend.insight.domain.ComplianceSnapshot;
import com.pramaan.backend.insight.domain.ComplianceSnapshotApp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * UC19 — seeds a synthetic weekly compliance-trend history when the snapshot table
 * is empty. Historical posture data is not available locally (mock), so this gives
 * the trend view shape; real snapshots accumulate from {@code POST /api/v1/insight/trend/snapshot}.
 */
@Component
@Order(100)
@ConditionalOnProperty(prefix = "pramaan.seed", name = "trend-enabled", havingValue = "true",
        matchIfMissing = true)
public class TrendSeedRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(TrendSeedRunner.class);

    private final TrendService trend;

    public TrendSeedRunner(TrendService trend) {
        this.trend = trend;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (trend.snapshotCount() > 0) {
            backfillAppRows();
            return;
        }
        Instant now = Instant.now();
        int weeks = 8;
        for (int i = weeks; i >= 1; i--) {
            double progress = (double) (weeks - i) / (weeks - 1); // 0 .. 1
            double compliancePct = round(42.0 + progress * 34.0);   // 42% -> 76%
            double completenessPct = round(55.0 + progress * 30.0); // 55% -> 85%
            int expected = 54; // 3 apps x 18 expected controls (matches the seeded catalogue)
            int compliant = (int) Math.round(expected * compliancePct / 100.0);
            int approved = (int) Math.round(6 + progress * 18);
            int open = expected - compliant;
            int evidenceCount = (int) Math.round(12 + progress * 60);   // 12 -> 72 records
            int integrityChecked = evidenceCount;
            int integrityIntact = (int) Math.round(evidenceCount * (0.90 + progress * 0.10)); // 90% -> 100%
            trend.saveRaw(new ComplianceSnapshot(UUID.randomUUID(),
                    now.minus(i * 7L, ChronoUnit.DAYS), 3, expected, compliant,
                    compliancePct, completenessPct, approved, open,
                    evidenceCount, integrityChecked, integrityIntact));
        }
        log.info("seeded {} synthetic compliance-trend snapshots", weeks);
        backfillAppRows();
    }

    /**
     * Synthetic per-application slices for snapshots that have none (the seeded history and
     * any pre-V12 snapshot), so the business-unit-scoped trend has shape. Each app is offset
     * around the snapshot's portfolio figure; real per-app rows come from
     * {@code POST /api/v1/insight/trend/snapshot}.
     */
    private void backfillAppRows() {
        var apps = trend.currentApps();
        if (apps.isEmpty()) {
            return;
        }
        Set<UUID> done = trend.snapshotIdsWithApps();
        List<ComplianceSnapshotApp> rows = new ArrayList<>();
        for (ComplianceSnapshot s : trend.allSnapshots()) {
            if (done.contains(s.getId())) {
                continue;
            }
            for (int i = 0; i < apps.size(); i++) {
                var a = apps.get(i);
                double offset = ((i % 3) - 1) * 8.0;
                int compliant = (int) Math.round(a.expected() * clamp(s.getCompliancePct() + offset) / 100.0);
                int covered = (int) Math.round(a.expected() * clamp(s.getCompletenessPct() + offset / 2) / 100.0);
                rows.add(new ComplianceSnapshotApp(s.getId(), a.slug(), a.businessUnit(),
                        a.expected(), compliant, covered));
            }
        }
        if (!rows.isEmpty()) {
            trend.saveAppRows(rows);
            log.info("backfilled {} synthetic per-application trend rows", rows.size());
        }
    }

    private static double clamp(double pct) {
        return Math.max(0.0, Math.min(100.0, pct));
    }

    private static double round(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
