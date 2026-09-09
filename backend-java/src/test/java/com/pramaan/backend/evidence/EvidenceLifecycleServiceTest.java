package com.pramaan.backend.evidence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pramaan.backend.common.ApiException;
import com.pramaan.backend.evidence.EvidenceDtos.EvidenceLifecycleView;
import com.pramaan.backend.evidence.EvidenceDtos.IngestRequest;
import com.pramaan.backend.evidence.EvidenceDtos.LifecycleAction;
import com.pramaan.backend.evidence.EvidenceDtos.LifecycleEventView;
import com.pramaan.backend.evidence.EvidenceDtos.LifecycleTransitionRequest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class EvidenceLifecycleServiceTest {

    @Autowired EvidenceIngestionService ingestion;
    @Autowired EvidenceLifecycleService lifecycle;

    private UUID ingest(String control, String body, Instant collectedAt) {
        return UUID.fromString(ingestion.ingest(new IngestRequest("net-banking", control, "C-SITE",
                "AGENT_OS_LINUX", "net-banking/" + control, control, "application/json", null, body,
                collectedAt, "agent", Map.of(), Map.of())).evidenceId());
    }

    @Test
    void ingestStartsInDraftWithAnAuditTrailEntry() {
        UUID id = ingest("OS-SSH-ROOT-LOGIN", "{\"status\":\"PASS\"}", Instant.now());
        EvidenceLifecycleView v = lifecycle.get(id);
        assertThat(v.state()).isEqualTo("DRAFT");
        assertThat(v.history()).extracting(LifecycleEventView::action).contains("INGESTED");
    }

    @Test
    void draftSubmitApproveWithReviewerAndTrail() {
        UUID id = ingest("OS-AUDIT-LOGGING", "{\"status\":\"PASS\"}", Instant.now());
        lifecycle.transition(id, new LifecycleTransitionRequest(LifecycleAction.SUBMIT, "owner", "ready for review"));
        EvidenceLifecycleView v = lifecycle.transition(id,
                new LifecycleTransitionRequest(LifecycleAction.APPROVE, "auditor", "looks good"));
        assertThat(v.state()).isEqualTo("APPROVED");
        assertThat(v.reviewedBy()).isEqualTo("auditor");
        assertThat(v.history()).hasSize(3);

        assertThatThrownBy(() -> lifecycle.transition(id,
                new LifecycleTransitionRequest(LifecycleAction.APPROVE, "x", null)))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void newVersionOfApprovedEvidenceForcesReReview() {
        UUID id = ingest("MW-HSTS", "{\"v\":1}", Instant.now());
        lifecycle.transition(id, new LifecycleTransitionRequest(LifecycleAction.SUBMIT, "o", null));
        lifecycle.transition(id, new LifecycleTransitionRequest(LifecycleAction.APPROVE, "a", null));
        // same evidence key, different content -> new version
        UUID same = ingest("MW-HSTS", "{\"v\":2}", Instant.now());
        assertThat(same).isEqualTo(id);
        assertThat(lifecycle.get(id).state()).isEqualTo("SUBMITTED");
    }

    @Test
    void approvedEvidencePastRetentionReadsAsExpired() {
        UUID id = ingest("OS-AUTH-NO-TRUST", "{\"old\":true}",
                Instant.now().minus(500, ChronoUnit.DAYS));
        lifecycle.transition(id, new LifecycleTransitionRequest(LifecycleAction.SUBMIT, "o", null));
        lifecycle.transition(id, new LifecycleTransitionRequest(LifecycleAction.APPROVE, "a", null));
        EvidenceLifecycleView v = lifecycle.get(id);
        assertThat(v.effectiveState()).isEqualTo("EXPIRED");
        assertThat(v.expired()).isTrue();
    }
}
