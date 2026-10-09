package com.pramaan.backend.insight.summary;

import static com.pramaan.backend.insight.summary.Summarizing.MAPPER;
import static com.pramaan.backend.insight.summary.Summarizing.bytes;
import static com.pramaan.backend.insight.summary.Summarizing.probe;
import static com.pramaan.backend.insight.summary.Summarizing.run;
import static org.assertj.core.api.Assertions.assertThat;

import com.pramaan.backend.insight.InsightDtos.SummaryFact;
import com.pramaan.backend.insight.summary.ContentProbe.Format;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class BinaryMetadataSummarizerTest {

    private final BinaryMetadataSummarizer summarizer = new BinaryMetadataSummarizer();

    private static byte[] withPrefix(String prefix, int total) {
        byte[] b = Arrays.copyOf(prefix.getBytes(StandardCharsets.ISO_8859_1), total);
        for (int i = prefix.length(); i < total; i++) {
            b[i] = (byte) (i % 7);
        }
        return b;
    }

    @Test
    void pdfIsDetectedFromMagicBytesAndReportedAsNotReadable() {
        byte[] pdf = withPrefix("%PDF-1.7\n", 2048);
        Extraction e = run(summarizer, pdf, "application/octet-stream", "evidence.bin");

        assertThat(e.kind()).isEqualTo(ContentKind.DOCUMENT);
        assertThat(e.contentSentence()).isEqualTo("PDF document of 2.0 KiB.");
        assertThat(e.facts()).contains(new SummaryFact("detected type", "PDF document", "content"),
                new SummaryFact("size", "2.0 KiB", "content"), new SummaryFact("file name", "evidence.bin", "record"));
        assertThat(e.limits()).containsExactly("Content is not machine-readable here.");
    }

    @Test
    void imagesAndUnknownBinaryGetTheirOwnKinds() {
        assertThat(run(summarizer, withPrefix("\u0089PNG\r\n", 64), "image/png", null).kind()).isEqualTo(ContentKind.IMAGE);
        assertThat(run(summarizer, withPrefix("ÿØÿ", 64), null, null).kind()).isEqualTo(ContentKind.IMAGE);
        assertThat(run(summarizer, new byte[]{0, 1, 2, 3, 4}, null, null).kind()).isEqualTo(ContentKind.BINARY);
        assertThat(run(summarizer, withPrefix("PK\u0003\u0004", 64), null, null).kind()).isEqualTo(ContentKind.BINARY);
    }

    @Test
    void officeContainersAreRecognised() {
        byte[] docx = withPrefix("PK\u0003\u0004word/document.xml", 128);
        assertThat(probe(docx, null, null).format()).isEqualTo(Format.DOCX);
        assertThat(run(summarizer, docx, null, null).kind()).isEqualTo(ContentKind.DOCUMENT);
        byte[] xlsx = withPrefix("PK\u0003\u0004xl/workbook.xml", 128);
        assertThat(probe(xlsx, null, null).format()).isEqualTo(Format.XLSX);
    }

    @Test
    void detectionIgnoresTheDeclaredTypeAndReportsDisagreement() {
        ContentProbe pdfLabelledJson = probe(withPrefix("%PDF-1.4\n", 64), "application/json", "x.json");
        assertThat(pdfLabelledJson.format()).isEqualTo(Format.PDF);
        assertThat(pdfLabelledJson.declaredDisagrees()).isTrue();

        ContentProbe jsonLabelledText = probe(bytes("{\"a\":1}"), "text/plain", null);
        assertThat(jsonLabelledText.format()).isEqualTo(Format.JSON);
        assertThat(jsonLabelledText.declaredDisagrees()).isFalse();

        assertThat(probe(bytes("hello"), "application/octet-stream", null).declaredDisagrees()).isFalse();
        assertThat(probe(bytes("hello"), null, "report.pdf").declaredDisagrees()).isTrue();
        assertThat(ContentProbe.of(new byte[0], false, 0, null, null, MAPPER).format()).isEqualTo(Format.EMPTY);
    }
}
