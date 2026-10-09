package com.pramaan.backend.insight.summary;

import static com.pramaan.backend.insight.summary.Summarizing.bytes;
import static com.pramaan.backend.insight.summary.Summarizing.fixture;
import static com.pramaan.backend.insight.summary.Summarizing.probe;
import static com.pramaan.backend.insight.summary.Summarizing.run;
import static org.assertj.core.api.Assertions.assertThat;

import com.pramaan.backend.insight.InsightDtos.SummaryFact;
import com.pramaan.backend.insight.InsightDtos.SummaryFinding;
import com.pramaan.backend.insight.summary.SummaryEnums.Severity;
import org.junit.jupiter.api.Test;

class IntegrationObjectSummarizerTest {

    private final IntegrationObjectSummarizer summarizer = new IntegrationObjectSummarizer();

    @Test
    void mockConnectorObjectIsDescribedFromItsRealFields() {
        Extraction e = run(summarizer, fixture("mock-change-ticket.json"));

        assertThat(e.kind()).isEqualTo(ContentKind.INTEGRATION_OBJECT);
        assertThat(e.contentSentence()).isEqualTo("MOCK_JIRA change ticket mock_jira-pci-c6 is COLLECTED.");
        assertThat(e.facts()).extracting(SummaryFact::label).containsExactly(
                "sourceSystem", "objectType", "reference", "status", "asOf", "application", "control", "framework");
        assertThat(e.facts()).contains(new SummaryFact("reference", "mock_jira-pci-c6", "/reference"));
        assertThat(e.findings()).isEmpty();
    }

    @Test
    void approverDateAndLinkedItemsAreExtractedWhenPresent() {
        Extraction e = run(summarizer, fixture("live-change-ticket.json"));

        assertThat(e.contentSentence())
                .isEqualTo("JIRA change ticket CHG-1042 is APPROVED; approved by Priya Nair on 2026-08-30.");
        assertThat(e.facts()).contains(
                new SummaryFact("key", "CHG-1042", "/key"),
                new SummaryFact("approvedBy", "Priya Nair", "/approvedBy"),
                new SummaryFact("approvedAt", "2026-08-30T12:15:00Z", "/approvedAt"),
                new SummaryFact("linkedItems", "2 items: PR-311, INC-77", "/linkedItems"),
                new SummaryFact("summary", "Rotate TLS certificates", "/summary"));
    }

    @Test
    void aRejectedStatusRaisesAWarning() {
        Extraction e = run(summarizer, bytes("{\"objectType\":\"incident_record\",\"id\":\"INC-9\",\"status\":\"REJECTED\"}"));

        assertThat(e.contentSentence()).isEqualTo("incident record INC-9 is REJECTED.");
        assertThat(e.findings()).containsExactly(
                new SummaryFinding(Severity.WARNING, "The incident record status is REJECTED.", "/status"));
    }

    @Test
    void doesNotClaimObjectsWithoutAnObjectType() {
        assertThat(summarizer.supports(probe(bytes("{\"status\":\"OK\"}"), "application/json", null))).isFalse();
        assertThat(summarizer.supports(probe(bytes("[{\"objectType\":\"x\"}]"), "application/json", null))).isFalse();
    }
}
