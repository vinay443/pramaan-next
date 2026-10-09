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

class GenericJsonSummarizerTest {

    private final GenericJsonSummarizer summarizer = new GenericJsonSummarizer();

    @Test
    void topLevelScalarsArrayLengthsAndStatusFieldsAreReported() {
        byte[] json = fixture("generic.json");
        Extraction e = run(summarizer, json);

        assertThat(e.kind()).isEqualTo(ContentKind.STRUCTURED_DATA);
        assertThat(e.contentSentence()).isEqualTo("JSON data of " + SummaryTemplates.humanSize(json.length)
                + ", 7 top-level fields; title \"Quarterly access review\".");
        assertThat(e.facts()).extracting(SummaryFact::label).containsExactly(
                "approved", "count", "items", "items[0].status", "items[1].status", "name",
                "nested.level.status", "owner", "reviewedAt");
        assertThat(e.facts()).contains(
                new SummaryFact("items", "array of 2 items", "/items"),
                new SummaryFact("count", "42", "/count"),
                new SummaryFact("items[1].status", "failed", "/items/1/status"));
        assertThat(e.findings()).containsExactly(
                new SummaryFinding(Severity.INFO, "Field items[0].status is ok.", "/items/0/status"),
                new SummaryFinding(Severity.WARNING, "Field items[1].status is failed.", "/items/1/status"),
                new SummaryFinding(Severity.INFO, "Field nested.level.status is pass.", "/nested/level/status"));
    }

    @Test
    void topLevelArrayIsCountedInItems() {
        byte[] json = bytes("[{\"a\":1},{\"a\":2},{\"a\":3}]");
        Extraction e = run(summarizer, json);
        assertThat(e.contentSentence()).isEqualTo("JSON data of " + SummaryTemplates.humanSize(json.length) + ", 3 items.");
    }

    @Test
    void depthIsCappedAtSixLevels() {
        // status sits in the innermost object: level 6 is inspected, level 8 is not
        Extraction shallow = run(summarizer, nested(6, "seen"));
        assertThat(shallow.findings()).extracting(SummaryFinding::message).anyMatch(m -> m.endsWith("is seen."));
        assertThat(shallow.limits()).noneMatch(l -> l.contains("deeper than 6"));

        Extraction deep = run(summarizer, nested(8, "hidden"));
        assertThat(deep.findings()).isEmpty();
        assertThat(deep.facts()).noneMatch(f -> f.value().equals("hidden"));
        assertThat(deep.limits()).contains("JSON nested deeper than 6 levels was not inspected.");
    }

    private static byte[] nested(int levels, String status) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < levels - 1; i++) {
            sb.append("{\"l\":");
        }
        sb.append("{\"status\":\"").append(status).append("\"}");
        sb.append("}".repeat(levels - 1));
        return bytes(sb.toString());
    }

    @Test
    void secretsAreRedactedInFacts() {
        Extraction e = run(summarizer, fixture("secrets.json"));

        assertThat(e.facts()).contains(
                new SummaryFact("apiKey", "[REDACTED]", "/apiKey"),
                new SummaryFact("db_password", "[REDACTED]", "/db_password"),
                new SummaryFact("pem", "[REDACTED]", "/pem"),
                new SummaryFact("service", "billing", "/service"));
        assertThat(e.facts()).filteredOn(f -> f.label().equals("notes"))
                .extracting(SummaryFact::value).allMatch(v -> v.contains("[REDACTED]") && !v.contains("abc.def.ghi"));
        assertThat(e.facts()).noneMatch(f -> f.value().contains("hunter2") || f.value().contains("SECRETBODY")
                || f.value().contains("abc-123-key"));
    }

    @Test
    void factsAreCappedAndTheOmissionIsDeclared() {
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < 80; i++) {
            sb.append(i == 0 ? "" : ",").append("\"f").append(String.format("%03d", i)).append("\":").append(i);
        }
        Extraction e = run(summarizer, bytes(sb.append("}").toString()));

        assertThat(e.facts()).hasSize(FactSink.CONTENT_FACT_CAP);
        assertThat(e.limits()).contains("Showing 50 of 80 facts; the rest were omitted to keep the summary short.");
    }

    @Test
    void longValuesAreClippedWithAnEllipsis() {
        Extraction e = run(summarizer, bytes("{\"note\":\"" + "z".repeat(500) + "\"}"));
        String value = e.facts().get(0).value();
        assertThat(value).hasSize(200).endsWith("…");
    }

    @Test
    void doesNotClaimInvalidJson() {
        assertThat(summarizer.supports(probe(bytes("{\"a\": 1,"), "application/json", null))).isFalse();
    }
}
