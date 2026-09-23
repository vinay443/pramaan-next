package com.pramaan.backend.insight;

import static org.assertj.core.api.Assertions.assertThat;

import com.pramaan.backend.evidence.EvidenceDtos.IngestRequest;
import com.pramaan.backend.evidence.EvidenceIngestionService;
import com.pramaan.backend.insight.InsightDtos.AuditScheduleReport;
import com.pramaan.backend.insight.domain.AuditSchedule;
import com.pramaan.backend.insight.repo.AuditScheduleRepository;
import com.pramaan.backend.rules.CheckDtos.EvaluateRequest;
import com.pramaan.backend.rules.RuleEvaluationService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/** Upcoming audit schedule — sort order, readiness threshold and app-scope join. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AuditScheduleServiceTest {

    @Autowired EvidenceIngestionService ingestion;
    @Autowired RuleEvaluationService rules;
    @Autowired AuditScheduleRepository schedules;
    @Autowired AuditScheduleService service;

    private void ingest(String app, String control, String framework, String body) {
        ingestion.ingest(new IngestRequest(app, control, framework, "SHAREPOINT",
                app + "/" + control, control, "application/json", null, body,
                Instant.now().minus(2, ChronoUnit.DAYS), "agent", Map.of(), Map.of()));
    }

    @Test
    void reportIsSortedByDateAndComputesReadyAndTotalCountsPerAudit() {
        ingest("net-banking", "OS-SSH-ROOT-LOGIN", "C-SITE",
                "{\"control\":\"OS-SSH-ROOT-LOGIN\",\"findings\":[{\"checkId\":\"OS-LNX-02\",\"status\":\"PASS\"}]}");
        rules.evaluate(new EvaluateRequest(null, null, null, null));

        LocalDate today = LocalDate.now();
        schedules.save(new AuditSchedule("C-SITE", "Later Audit", today.plusDays(60),
                List.of("net-banking", "does-not-exist")));
        schedules.save(new AuditSchedule("C-SITE", "Sooner Audit", today.plusDays(10),
                List.of("net-banking")));

        AuditScheduleReport r = service.upcoming();
        assertThat(r.readinessThresholdPct()).isEqualTo(80);
        assertThat(r.audits()).hasSize(2);
        // sorted ascending by scheduled date, not insertion order
        assertThat(r.audits().get(0).auditName()).isEqualTo("Sooner Audit");
        assertThat(r.audits().get(1).auditName()).isEqualTo("Later Audit");

        var later = r.audits().get(1);
        // "does-not-exist" is excluded from totalCount, not counted as not-ready
        assertThat(later.totalCount()).isEqualTo(1);
        // one compliant control among the whole C-SITE catalog is well under the 80% default
        assertThat(later.readyCount()).isEqualTo(0);
        assertThat(later.readinessThresholdPct()).isEqualTo(80);
    }
}
