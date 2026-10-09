package com.pramaan.backend.insight.summary;

import static com.pramaan.backend.insight.summary.Summarizing.probe;
import static com.pramaan.backend.insight.summary.Summarizing.runDoc;
import static org.assertj.core.api.Assertions.assertThat;

import com.pramaan.backend.insight.InsightDtos.SummaryFact;
import com.pramaan.backend.insight.InsightDtos.SummaryFinding;
import com.pramaan.backend.insight.summary.ContentProbe.Format;
import com.pramaan.backend.insight.summary.SummaryEnums.Severity;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class PdfImageSummarizerTest {

    private static final String NOW = "2026-09-30T00:00:00Z";
    private final PdfSummarizer pdf = new PdfSummarizer();
    private final ImageSummarizer image = new ImageSummarizer();

    private static byte[] policyPdf() {
        return TestFiles.pdf("Information Security Policy", "Meera Iyer", "2026-09-01",
                "Information Security Policy\nVersion 1.3\nApproved by: Meera Iyer\nValid until: 30/06/2026",
                "Encryption in transit is required for all customer data.");
    }

    @Test
    void metadataTextSignalsAndPointersAreExtracted() {
        byte[] file = policyPdf();
        assertThat(probe(file, "application/octet-stream", null).format()).isEqualTo(Format.PDF);

        Extraction e = runDoc(pdf, file, "policy.pdf", NOW, "Encryption in Transit",
                "Data must be encrypted in transit using approved algorithms.");

        assertThat(e.kind()).isEqualTo(ContentKind.DOCUMENT);
        assertThat(e.contentSentence()).isEqualTo("PDF of " + SummaryTemplates.humanSize(file.length)
                + ", 2 pages; title \"Information Security Policy\"; last modified 2026-09-01; approved by Meera Iyer.");
        assertThat(e.facts()).contains(
                new SummaryFact("pages", "2", "PDF document information"),
                new SummaryFact("title", "Information Security Policy", "PDF document information"),
                new SummaryFact("author", "Meera Iyer", "PDF document information"),
                new SummaryFact("producer", "summary-test", "PDF document information"),
                new SummaryFact("created", "2026-08-01T10:00:00Z", "PDF document information"),
                new SummaryFact("modified", "2026-09-01T10:00:00Z", "PDF document information"),
                new SummaryFact("encrypted", "no", "PDF document information"),
                new SummaryFact("first heading", "Information Security Policy", "page 1"),
                new SummaryFact("version", "1.3", "page 1"),
                new SummaryFact("approved by", "Meera Iyer", "page 1"),
                new SummaryFact("valid until", "2026-06-30", "page 1 (as written: 30/06/2026)"),
                new SummaryFact(DocumentSignals.OVERLAP_LABEL, "Mentions: encryption, transit, data, approved", "text"));
        assertThat(e.findings()).containsExactly(new SummaryFinding(Severity.WARNING,
                "Document states it was due for review on 2026-06-30.", "page 1"));
    }

    @Test
    void anEncryptedPdfIsNeverReadForText() {
        byte[] needsPassword = TestFiles.pdf("Secret", null, null, new String[]{"Approved by: Meera Iyer"}, "user-pw", "owner-pw", 12);
        Extraction locked = runDoc(pdf, needsPassword);
        assertThat(locked.facts()).contains(new SummaryFact("encrypted", "yes", "PDF document information"));
        assertThat(locked.limits()).anyMatch(l -> l.contains("encrypted"));
        assertThat(locked.facts()).noneMatch(f -> f.label().equals("approved by"));

        byte[] ownerOnly = TestFiles.pdf("Restricted", null, null, new String[]{"Approved by: Meera Iyer"}, null, "owner-pw", 12);
        Extraction restricted = runDoc(pdf, ownerOnly);
        assertThat(restricted.facts()).contains(new SummaryFact("encrypted", "yes", "PDF document information"));
        assertThat(restricted.limits()).anyMatch(l -> l.equals("The PDF is encrypted; its text was not read."));
        assertThat(restricted.facts()).noneMatch(f -> f.label().equals("approved by") || f.label().equals("first heading"));
    }

    @Test
    void onlyTheFirstTwentyPagesAreRead() {
        String[] pages = new String[25];
        for (int i = 0; i < 25; i++) pages[i] = "Page number " + (i + 1);
        Extraction e = runDoc(pdf, TestFiles.pdf("Long", null, null, pages));
        assertThat(e.limits()).contains("Text was read from the first 20 of 25 pages.");
        assertThat(e.facts()).contains(new SummaryFact("pages", "25", "PDF document information"));
    }

    @Test
    void extractedTextIsCappedAtTwoHundredKilobytes() {
        String line = "lorem ipsum dolor sit amet consectetur adipiscing elit sed do eiusmod tempor incididunt ut labore et dolore";
        String page = (line + "\n").repeat(500);
        String[] pages = new String[10];
        Arrays.fill(pages, page);
        Extraction e = runDoc(pdf, TestFiles.pdf("Big", null, null, pages, null, null, 2f));
        assertThat(e.limits()).anyMatch(l -> l.startsWith("Text was read up to 200 KB"));
        assertThat(e.facts().size()).isLessThanOrEqualTo(FactSink.CONTENT_FACT_CAP);
    }

    @Test
    void theWallClockGuardStopsExtraction() {
        Extraction e = runDoc(new PdfSummarizer(Duration.ZERO), policyPdf());
        assertThat(e.limits()).anyMatch(l -> l.startsWith("Text extraction stopped after"));
        assertThat(e.facts()).contains(new SummaryFact("pages", "2", "PDF document information"));
        assertThat(e.facts()).noneMatch(f -> f.label().equals("approved by"));
    }

    @Test
    void corruptAndTruncatedPdfsDegradeCleanly() {
        Extraction junk = runDoc(pdf, "%PDF-1.4 this is not really a pdf at all".getBytes(StandardCharsets.US_ASCII));
        assertThat(junk.kind()).isEqualTo(ContentKind.BINARY);
        assertThat(junk.limits()).anyMatch(l -> l.contains("could not be read"));

        byte[] whole = policyPdf();
        Extraction cut = runDoc(pdf, Arrays.copyOf(whole, whole.length / 3));
        assertThat(cut.kind()).isIn(ContentKind.BINARY, ContentKind.DOCUMENT);
    }

    @Test
    void secretsInPdfTextAreRedacted() {
        Extraction e = runDoc(pdf, TestFiles.pdf("Runbook", null, null, "Service password=hunter2 rotates\nOwner: Meera Iyer"));
        assertThat(e.facts()).noneMatch(f -> f.value().contains("hunter2"));
        assertThat(e.facts()).anyMatch(f -> f.label().equals("first heading") && f.value().contains("[REDACTED]"));
    }

    @Test
    void twoRunsGiveIdenticalResults() {
        byte[] file = policyPdf();
        assertThat(runDoc(pdf, file)).isEqualTo(runDoc(pdf, file));
    }

    // ---- images ----------------------------------------------------------------------------------------

    @Test
    void imageDimensionsComeFromTheHeaderAndTheNoOcrLimitIsAlwaysThere() {
        for (String[] f : new String[][]{{"png", "PNG image"}, {"jpg", "JPEG image"}, {"gif", "GIF image"}}) {
            byte[] file = TestFiles.image(40, 30, f[0]);
            Extraction e = runDoc(image, file);
            assertThat(e.kind()).isEqualTo(ContentKind.IMAGE);
            assertThat(e.contentSentence()).isEqualTo(f[1] + " of " + SummaryTemplates.humanSize(file.length) + ", 40x30 px.");
            assertThat(e.facts()).contains(new SummaryFact("dimensions", "40 x 30 px", "image header"));
            assertThat(e.limits()).contains(ImageSummarizer.LIMIT);
        }
        assertThat(ImageSummarizer.LIMIT)
                .isEqualTo("Image evidence — contents are not machine-readable here (no OCR); confirm visually.");
    }

    @Test
    void anUnreadableImageHeaderStillKeepsTheImageLimit() {
        byte[] bad = new byte[64];
        bad[0] = (byte) 0x89;
        bad[1] = 'P';
        bad[2] = 'N';
        bad[3] = 'G';
        Extraction e = runDoc(image, bad);
        assertThat(e.kind()).isEqualTo(ContentKind.IMAGE);
        assertThat(e.limits()).contains(ImageSummarizer.LIMIT);
        assertThat(e.limits()).anyMatch(l -> l.contains("dimensions are unknown"));
    }
}
