package com.pramaan.backend.insight;

import com.pramaan.backend.application.ApplicationService;
import com.pramaan.backend.config.PramaanProperties;
import com.pramaan.backend.insight.InsightDtos.AuditScheduleReport;
import com.pramaan.backend.insight.InsightDtos.AuditScheduleRow;
import com.pramaan.backend.insight.domain.AuditSchedule;
import com.pramaan.backend.insight.repo.AuditScheduleRepository;
import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Upcoming audit schedule. Each scheduled audit's {@code readyCount}/{@code totalCount}
 * is computed from its in-scope applications' current compliance % for that audit's
 * framework (reusing {@link ComplianceService#forApplication}, no new scoring) against
 * the configurable {@code pramaan.audit-schedule.readiness-threshold-pct} (default 80).
 * An application slug in scope that no longer exists is counted out of the total, not
 * treated as not-ready.
 */
@Service
public class AuditScheduleService {

    private final AuditScheduleRepository schedules;
    private final ComplianceService compliance;
    private final ApplicationService applications;
    private final PramaanProperties props;
    private final Clock clock;

    public AuditScheduleService(AuditScheduleRepository schedules, ComplianceService compliance,
                                ApplicationService applications, PramaanProperties props, Clock clock) {
        this.schedules = schedules;
        this.compliance = compliance;
        this.applications = applications;
        this.props = props;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AuditScheduleReport upcoming() {
        int threshold = props.auditScheduleOrDefault().readinessThresholdPctOrDefault();
        Set<String> validSlugs = applications.list().stream()
                .map(a -> a.slug())
                .collect(Collectors.toSet());
        List<AuditScheduleRow> rows = schedules.findAllByOrderByScheduledDateAsc().stream()
                .map(s -> toRow(s, threshold, validSlugs))
                .toList();
        return new AuditScheduleReport(clock.instant(), threshold, rows);
    }

    private AuditScheduleRow toRow(AuditSchedule s, int threshold, Set<String> validSlugs) {
        int ready = 0;
        int total = 0;
        for (String slug : s.getApplicationSlugs()) {
            if (!validSlugs.contains(slug)) {
                continue;
            }
            total++;
            double pct = compliance.forApplication(slug, s.getFramework()).compliancePct();
            if (pct >= threshold) {
                ready++;
            }
        }
        return new AuditScheduleRow(s.getId().toString(), s.getFramework(), s.getAuditName(), s.getScheduledDate(),
                s.getApplicationSlugs(), ready, total, threshold);
    }
}
