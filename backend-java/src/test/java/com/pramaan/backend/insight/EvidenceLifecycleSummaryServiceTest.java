package com.pramaan.backend.insight;

import static org.assertj.core.api.Assertions.assertThat;

import com.pramaan.backend.evidence.EvidenceDtos.IngestRequest;
import com.pramaan.backend.evidence.EvidenceDtos.LifecycleAction;
import com.pramaan.backend.evidence.EvidenceDtos.LifecycleTransitionRequest;
import com.pramaan.backend.evidence.EvidenceIngestionService;
import com.pramaan.backend.evidence.EvidenceLifecycleService;
import com.pramaan.backend.insight.InsightDtos.EvidenceLifecycleSummary;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/** App Owner dashboard — evidence lifecycle counts, rejection audit trail, Auditor SLA, aging. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class EvidenceLifecycleSummaryServiceTest {

    @Autowired EvidenceIngestionService ingestion;
    @Autowired EvidenceLifecycleService lifecycle;
    @Autowired EvidenceLifecycleSummaryService summary;
    @Autowired TrendService trend;

    private UUID ingest(String app, String control, String framework) {
        return UUID.fromString(ingestion.ingest(new IngestRequest(app, control, framework, "SHAREPOINT",
                app + "/" + control, control, "application/json", null, "{\"v\":1}",
                Instant.now().minus(2, ChronoUnit.DAYS), "agent", Map.of(), Map.of())).evidenceId());
    }

    @Test
    void countsAndRejectionsAreScopedToOneApplication() {
        UUID approved = ingest("net-banking", "TLS-CERT-EXPIRY", "C-SITE");
        lifecycle.transition(approved, new LifecycleTransitionRequest(LifecycleAction.SUBMIT, "o", null), "APP_OWNER", null);
        lifecycle.transition(approved, new LifecycleTransitionRequest(LifecycleAction.APPROVE, "a", null), "AUDITOR", null);

        UUID rejected = ingest("net-banking", "OS-SSH-ROOT-LOGIN", "C-SITE");
        lifecycle.transition(rejected, new LifecycleTransitionRequest(LifecycleAction.SUBMIT, "o", null), "APP_OWNER", null);
        lifecycle.transition(rejected, new LifecycleTransitionRequest(LifecycleAction.REJECT, "auditor-1", "missing attestation"),
                "AUDITOR", null);

        UUID otherApp = ingest("payments", "OS-SSH-ROOT-LOGIN", "C-SITE");
        lifecycle.transition(otherApp, new LifecycleTransitionRequest(LifecycleAction.SUBMIT, "o", null), "APP_OWNER", null);
        lifecycle.transition(otherApp, new LifecycleTransitionRequest(LifecycleAction.REJECT, "auditor-1", "wrong app"),
                "AUDITOR", null);

        EvidenceLifecycleSummary s = summary.forApplication("net-banking");
        assertThat(s.applicationSlug()).isEqualTo("net-banking");
        assertThat(s.counts().approved()).isEqualTo(1);
        assertThat(s.counts().rejected()).isEqualTo(1);
        assertThat(s.rejections()).hasSize(1);
        assertThat(s.rejections().get(0).controlId()).isEqualTo("OS-SSH-ROOT-LOGIN");
        assertThat(s.rejections().get(0).reason()).isEqualTo("missing attestation");
        assertThat(s.rejections().get(0).workflowState()).isEqualTo("Re-upload Requested");
        assertThat(s.auditorSla().totalReviewed()).isEqualTo(2);
        assertThat(s.auditorSla().pct()).isNotNull();
        assertThat(s.auditorSla().targetDays()).isEqualTo(5);

        // the other application's rejection must not leak into net-banking's summary
        assertThat(s.rejections()).noneMatch(r -> r.controlId().equals("OS-SSH-ROOT-LOGIN") && r.applicationSlug().equals("payments"));
    }

    @Test
    void trendClosureScopedToApplicationExcludesOtherApplicationsEvents() {
        UUID a = ingest("net-banking", "TLS-CERT-EXPIRY", "C-SITE");
        lifecycle.transition(a, new LifecycleTransitionRequest(LifecycleAction.SUBMIT, "o", null), "APP_OWNER", null);
        lifecycle.transition(a, new LifecycleTransitionRequest(LifecycleAction.APPROVE, "auditor", null), "AUDITOR", null);

        UUID b = ingest("payments", "OS-SSH-ROOT-LOGIN", "C-SITE");
        lifecycle.transition(b, new LifecycleTransitionRequest(LifecycleAction.SUBMIT, "o", null), "APP_OWNER", null);
        lifecycle.transition(b, new LifecycleTransitionRequest(LifecycleAction.REJECT, "auditor", "no"), "AUDITOR", null);

        var scoped = trend.trendForApplication("net-banking");
        assertThat(scoped.closure().approvals()).isEqualTo(1);
        assertThat(scoped.closure().rejections()).isEqualTo(0);
    }
}
