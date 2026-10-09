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

class QueryResultSummarizerTest {

    private final QueryResultSummarizer summarizer = new QueryResultSummarizer();

    @Test
    void jdbcStyleRowsAreCountedAndFlagged() {
        Extraction e = run(summarizer, fixture("query-rows.json"));

        assertThat(e.kind()).isEqualTo(ContentKind.QUERY_RESULT);
        assertThat(e.contentSentence()).isEqualTo("Query Password Policy returned 3 rows; 1 flagged.");
        assertThat(e.facts()).contains(
                new SummaryFact("controlName", "Password Policy", "/controlName"),
                new SummaryFact("rowCount", "3", "/rowCount"),
                new SummaryFact("row 1", "role=app, method=scram-sha-256, compliant=true", "/rows/0"),
                new SummaryFact("row 2", "role=legacy, method=md5, compliant=false", "/rows/1"));
        assertThat(e.findings()).containsExactly(new SummaryFinding(Severity.WARNING,
                "Row 2 is flagged: role=legacy, method=md5, compliant=false.", "/rows/1"));
    }

    @Test
    void onlyTheFirstFiveRowsBecomeFacts() {
        StringBuilder rows = new StringBuilder();
        for (int i = 0; i < 9; i++) {
            rows.append(i == 0 ? "" : ",").append("{\"n\":").append(i).append("}");
        }
        Extraction e = run(summarizer, bytes("{\"collector\":\"predefined-query\",\"controlName\":\"Many\",\"rowCount\":9,\"rows\":["
                + rows + "]}"));

        assertThat(e.contentSentence()).isEqualTo("Query Many returned 9 rows.");
        assertThat(e.facts()).filteredOn(f -> f.label().startsWith("row ")).hasSize(5);
    }

    @Test
    void textOutputIsCountedInLinesAndFlaggedLinesAreReported() {
        Extraction e = run(summarizer, fixture("query-output.json"));

        assertThat(e.contentSentence()).isEqualTo("Query SELinux Status returned 3 lines of output; 1 flagged.");
        assertThat(e.facts()).contains(new SummaryFact("output line 1", "Enforcing", "/output (line 1)"));
        assertThat(e.findings()).containsExactly(new SummaryFinding(Severity.WARNING,
                "Output line 3 is flagged: compliant=false (audit mode).", "/output (line 3)"));
    }

    @Test
    void aFailingTopLevelStatusIsAFailFinding() {
        Extraction e = run(summarizer, bytes("{\"collector\":\"predefined-query\",\"controlName\":\"X\",\"status\":\"fail\","
                + "\"output\":\"ok\"}"));
        assertThat(e.findings()).extracting(SummaryFinding::severity).contains(Severity.FAIL);
    }

    @Test
    void queryWithoutAnyResultDataSaysSo() {
        Extraction e = run(summarizer, bytes("{\"collector\":\"predefined-query\",\"controlName\":\"Empty\"}"));
        assertThat(e.contentSentence()).isEqualTo("Query Empty stored no result data.");
    }

    @Test
    void aSensitiveColumnIsRedactedInRowFacts() {
        Extraction e = run(summarizer, bytes("{\"collector\":\"predefined-query\",\"controlName\":\"Users\",\"rows\":"
                + "[{\"name\":\"svc\",\"password\":\"hunter2\"}]}"));
        assertThat(e.facts()).contains(new SummaryFact("row 1", "name=svc, password=[REDACTED]", "/rows/0"));
    }

    @Test
    void doesNotClaimOtherJson() {
        assertThat(summarizer.supports(probe(bytes("{\"a\":1}"), "application/json", null))).isFalse();
    }
}
