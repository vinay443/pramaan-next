package com.pramaan.backend.rules;

import static org.assertj.core.api.Assertions.assertThat;

import com.pramaan.backend.evidence.EvidenceDtos.IngestRequest;
import com.pramaan.backend.evidence.EvidenceIngestionService;
import com.pramaan.backend.rules.CheckDtos.CheckResultView;
import com.pramaan.backend.rules.CheckDtos.EvaluateRequest;
import com.pramaan.backend.rules.CheckDtos.EvaluationSummary;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RuleEvaluationServiceTest {

    @Autowired EvidenceIngestionService ingestion;
    @Autowired RuleEvaluationService evaluation;

    private void ingest(String control, String framework, String source, String body) {
        ingestion.ingest(new IngestRequest("payments", control, framework, source,
                source.toLowerCase() + "-" + control.toLowerCase(), control, "application/json",
                null, body, Instant.parse("2026-09-01T00:00:00Z"), source, Map.of(), Map.of()));
    }

    @Test
    void evaluatesDeterministicVerdictsAndIsIdempotent() {
        ingest("ITPP-CHG-02", "ITPP", "MOCK_JIRA",
                "{ \"objectType\": \"change_ticket\", \"status\": \"COLLECTED\" }");
        ingest("ITPP-INC-01", "ITPP", "MOCK_SERVICENOW",
                "{ \"objectType\": \"incident_record\", \"status\": \"COLLECTED\" }");
        ingest("PCI-DSS-6.2", "PCI_DSS", "MOCK_GITHUB",
                "{ \"objectType\": \"branch_protection\", \"status\": \"FAILED\" }");

        EvaluationSummary summary = evaluation.evaluate(new EvaluateRequest("payments", null, null, null));
        assertThat(summary.evidenceEvaluated()).isEqualTo(3);
        // CHG-02: COLLECTED->PASS + change_ticket->PASS ; INC-01: COLLECTED->PASS + incident_record->WARNING ;
        // 6.2: FAILED->FAIL
        assertThat(summary.byStatus().getOrDefault("PASS", 0)).isEqualTo(3);
        assertThat(summary.byStatus().getOrDefault("WARNING", 0)).isEqualTo(1);
        assertThat(summary.byStatus().getOrDefault("FAIL", 0)).isEqualTo(1);

        var fail = evaluation.list(new EvaluateRequest("payments", null, null, null),
                CheckStatus.FAIL, 0, 50);
        assertThat(fail.items()).extracting(CheckResultView::controlId).containsExactly("PCI-DSS-6.2");

        // Re-evaluation overwrites, does not duplicate.
        evaluation.evaluate(new EvaluateRequest("payments", null, null, null));
        var all = evaluation.list(new EvaluateRequest("payments", null, null, null), null, 0, 100);
        assertThat(all.totalItems()).isEqualTo(5);
    }

    @Test
    void recognisesGoAgentEvidenceBundleShape() {
        // The agents-go EvidencePayload embeds per-check findings with a "status" field.
        ingest("OS-SSH-ROOT-LOGIN", "C-SITE", "AGENT_OS_LINUX",
                "{ \"collector\": \"os\", \"simulated\": true, "
                        + "\"findings\": [ { \"checkId\": \"OS-LNX-02\", \"status\": \"PASS\" } ] }");
        ingest("MW-HSTS", "DPSC", "AGENT_MIDDLEWARE_TOMCAT",
                "{ \"collector\": \"middleware\", \"simulated\": true, "
                        + "\"findings\": [ { \"checkId\": \"MW-03\", \"status\": \"FAIL\" } ] }");

        evaluation.evaluate(new EvaluateRequest("payments", null, null, null));
        var rows = evaluation.list(new EvaluateRequest("payments", null, null, null), null, 0, 50);
        assertThat(rows.items())
                .filteredOn(r -> r.checkId().equals("RULE-EVIDENCE-COLLECTED"))
                .extracting(r -> r.controlId() + "=" + r.status())
                .contains("OS-SSH-ROOT-LOGIN=PASS", "MW-HSTS=FAIL");
    }

    @Test
    void fallsThroughToDefaultVerdictWhenNoClauseMatches() {
        // control matches two rules but content omits every clause substring
        ingest("ITPP-CHG-02", "ITPP", "MOCK_JIRA", "{ \"objectType\": \"other\" }");
        evaluation.evaluate(new EvaluateRequest("payments", "ITPP", "ITPP-CHG-02", null));
        var rows = evaluation.list(new EvaluateRequest("payments", "ITPP", "ITPP-CHG-02", null),
                null, 0, 50);
        assertThat(rows.items()).extracting(CheckResultView::status)
                .containsExactlyInAnyOrder(CheckStatus.WARNING,  // RULE-EVIDENCE-COLLECTED default
                        CheckStatus.FAIL);                        // RULE-CHANGE-TICKET-PRESENT default
    }
}
