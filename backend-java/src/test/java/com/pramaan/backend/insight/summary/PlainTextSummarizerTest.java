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

class PlainTextSummarizerTest {

    private final PlainTextSummarizer summarizer = new PlainTextSummarizer();

    private static String size(byte[] b) {
        return SummaryTemplates.humanSize(b.length);
    }

    @Test
    void textReportsSizeLinesFirstLineAndDates() {
        byte[] text = fixture("notes.txt");
        Extraction e = run(summarizer, text, "text/plain", "notes.txt");

        assertThat(e.kind()).isEqualTo(ContentKind.TEXT);
        assertThat(e.contentSentence()).isEqualTo("Text of " + size(text)
                + ", 3 lines; title \"Meeting notes - Access review\"; dated 2026-08-15.");
        assertThat(e.facts()).contains(
                new SummaryFact("lines", "3", "content"),
                new SummaryFact("first line", "Meeting notes - Access review", "line 1"),
                new SummaryFact("date", "2026-08-15", "line 2"),
                new SummaryFact("date", "30 Sep 2026", "line 3"));
    }

    @Test
    void csvReportsHeaderColumnsAndRowCount() {
        byte[] csv = fixture("table.csv");
        Extraction e = run(summarizer, csv, "text/csv", "table.csv");

        assertThat(e.contentSentence()).isEqualTo("CSV of " + size(csv) + ", 2 data rows, 3 columns.");
        assertThat(e.facts()).contains(
                new SummaryFact("header", "control,status,owner", "line 1"),
                new SummaryFact("columns", "3", "line 1"),
                new SummaryFact("data rows", "2", "content"));
    }

    @Test
    void csvReportsEmptyCellsAndWarnsAboutInconsistentRows() {
        byte[] csv = bytes("a,b,c\n1,2\n3,4,5\n,,\n\"x,y\",2,3\n");
        Extraction e = run(summarizer, csv, "text/csv", "t.csv");

        assertThat(e.contentSentence()).isEqualTo("CSV of " + size(csv) + ", 4 data rows, 3 columns.");
        assertThat(e.facts()).contains(new SummaryFact("empty cells", "3", "content"),
                new SummaryFact("data rows", "4", "content"));
        assertThat(e.findings()).containsExactly(new SummaryFinding(Severity.WARNING,
                "1 of 4 data rows do not have the header's 3 columns (first: line 2 has 2).", "line 2"));
    }

    @Test
    void tsvIsDetectedFromTheBytesToo() {
        byte[] tsv = bytes("a\tb\n1\t2\n3\t4\n");
        Extraction e = run(summarizer, tsv, "application/octet-stream", null);
        assertThat(e.contentSentence()).isEqualTo("TSV of " + size(tsv) + ", 2 data rows, 2 columns.");
    }

    @Test
    void markdownXmlAndLogsAreLabelled() {
        byte[] md = bytes("# Policy\n\nBody");
        assertThat(run(summarizer, md, "text/markdown", "p.md").contentSentence())
                .isEqualTo("Markdown document of " + size(md) + ", 3 lines; title \"Policy\".");

        byte[] xml = bytes("<?xml version=\"1.0\"?><a/>");
        assertThat(run(summarizer, xml, "application/xml", null).contentSentence())
                .isEqualTo("XML document of " + size(xml) + ", 1 line.");

        byte[] log = bytes("2026-09-01T10:00:00Z INFO started\n2026-09-01T10:00:05Z ERROR boom\n");
        Extraction e = run(summarizer, log, "text/plain", "app.log");
        assertThat(e.contentSentence()).isEqualTo("Log of " + size(log) + ", 2 lines; dated 2026-09-01.");
    }

    @Test
    void invalidJsonFallsBackToTextWithAWarning() {
        byte[] broken = bytes("{\"status\": \"ok\",");
        assertThat(new GenericJsonSummarizer().supports(probe(broken, "application/json", null))).isFalse();

        Extraction e = run(summarizer, broken, "application/json", "x.json");
        assertThat(e.kind()).isEqualTo(ContentKind.TEXT);
        assertThat(e.findings()).containsExactly(new SummaryFinding(Severity.WARNING,
                "The content looks like JSON but is not valid JSON; summarised as plain text.", "line 1"));
    }

    @Test
    void badUtf8IsDecodedWithReplacementNeverACrash() {
        byte[] bad = {'a', 'b', (byte) 0xFF, (byte) 0xFE, ' ', 'c'};
        Extraction e = run(summarizer, bad, "text/plain", null);
        assertThat(e.kind()).isEqualTo(ContentKind.TEXT);
        assertThat(e.facts()).contains(new SummaryFact("first line", "ab�� c", "line 1"));
    }

    @Test
    void emptyContentIsReportedAsEmpty() {
        Extraction e = run(summarizer, new byte[0], "text/plain", null);
        assertThat(e.contentSentence()).isEqualTo("Text of 0 bytes, 0 lines.");
        assertThat(e.limits()).contains("The stored content is empty.");
    }

    @Test
    void doesNotClaimBinaryOrValidJson() {
        assertThat(summarizer.supports(probe(new byte[]{0, 1, 2, 3}, "application/octet-stream", null))).isFalse();
        assertThat(summarizer.supports(probe(bytes("{\"a\":1}"), "application/json", null))).isTrue(); // text; engine order sends JSON elsewhere first
    }
}
