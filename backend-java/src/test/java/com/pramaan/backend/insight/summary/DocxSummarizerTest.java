package com.pramaan.backend.insight.summary;

import static com.pramaan.backend.insight.summary.Summarizing.probe;
import static com.pramaan.backend.insight.summary.Summarizing.runDoc;
import static com.pramaan.backend.insight.summary.TestFiles.appXml;
import static com.pramaan.backend.insight.summary.TestFiles.b;
import static com.pramaan.backend.insight.summary.TestFiles.coreXml;
import static com.pramaan.backend.insight.summary.TestFiles.docx;
import static com.pramaan.backend.insight.summary.TestFiles.documentXml;
import static org.assertj.core.api.Assertions.assertThat;

import com.pramaan.backend.insight.InsightDtos.SummaryFact;
import com.pramaan.backend.insight.InsightDtos.SummaryFinding;
import com.pramaan.backend.insight.summary.ContentProbe.Format;
import com.pramaan.backend.insight.summary.SummaryEnums.Severity;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DocxSummarizerTest {

    private static final String NOW = "2026-09-30T00:00:00Z";
    private final DocxSummarizer summarizer = new DocxSummarizer();

    private static byte[] policyDocx() {
        return docx(
                coreXml("Access Control Policy", "Priya Nair", "Arun Rao", "2026-08-01T09:00:00Z", "2026-09-01T09:30:00Z"),
                appXml("Pages", "3", "Words", "420"),
                documentXml("H1:Access Control Policy", "Version 2.1", "Approved by: Priya Nair",
                        "Next review: 01/03/2026", "TABLE", "H2:Scope", "All systems must use encryption in transit."));
    }

    @Test
    void propertiesStructureHeadingsAndSignalsAreExtractedWithPointers() {
        byte[] file = policyDocx();
        Extraction e = runDoc(summarizer, file, "policy.docx", NOW, "Encryption in Transit",
                "Data must be encrypted in transit using approved algorithms.");

        assertThat(e.kind()).isEqualTo(ContentKind.DOCUMENT);
        assertThat(e.contentSentence()).isEqualTo("DOCX of " + SummaryTemplates.humanSize(file.length)
                + ", 3 pages; title \"Access Control Policy\"; last modified 2026-09-01; approved by Priya Nair.");
        assertThat(e.facts()).contains(
                new SummaryFact("title", "Access Control Policy", "docProps/core.xml"),
                new SummaryFact("creator", "Priya Nair", "docProps/core.xml"),
                new SummaryFact("last modified by", "Arun Rao", "docProps/core.xml"),
                new SummaryFact("created", "2026-08-01T09:00:00Z", "docProps/core.xml"),
                new SummaryFact("modified", "2026-09-01T09:30:00Z", "docProps/core.xml"),
                new SummaryFact("pages", "3", "docProps/app.xml"),
                new SummaryFact("words", "420", "docProps/app.xml"),
                new SummaryFact("paragraphs", "7", "word/document.xml"),
                new SummaryFact("tables", "1", "word/document.xml"),
                new SummaryFact("heading H1", "Access Control Policy", "word/document.xml (paragraph 1)"),
                new SummaryFact("heading H2", "Scope", "word/document.xml (paragraph 6)"),
                new SummaryFact("version", "2.1", "word/document.xml (paragraph 2)"),
                new SummaryFact("approved by", "Priya Nair", "word/document.xml (paragraph 3)"),
                new SummaryFact("next review", "2026-03-01",
                        "word/document.xml (paragraph 4) (as written: 01/03/2026)"),
                new SummaryFact(DocumentSignals.OVERLAP_LABEL, "Mentions: encryption, transit, approved", "text"));
        assertThat(e.findings()).containsExactly(new SummaryFinding(Severity.WARNING,
                "Document states it was due for review on 2026-03-01.", "word/document.xml (paragraph 4)"));
    }

    @Test
    void aFutureReviewDateRaisesNothingAndAMissingOwnerInAPolicyIsOnlyInfo() {
        byte[] file = docx(coreXml("Password Standard", null, null, null, null), null,
                documentXml("H1:Password Standard", "Next review: 2027-01-01", "All accounts need MFA."));
        Extraction e = runDoc(summarizer, file, "standard.docx", NOW, "MFA", "Multi-factor authentication.");

        assertThat(e.findings()).containsExactly(new SummaryFinding(Severity.INFO,
                "No approver/owner marker found in the text.", "text"));
        assertThat(e.contentSentence()).endsWith("title \"Password Standard\".");
        assertThat(e.contentSentence()).startsWith("DOCX of ").contains("3 paragraphs");
    }

    @Test
    void headingsAreCappedAtTenAndTextAtTwoHundredKilobytes() {
        String[] paragraphs = new String[15];
        for (int i = 0; i < 15; i++) paragraphs[i] = "H1:Heading " + i;
        Extraction many = runDoc(summarizer, docx(null, null, documentXml(paragraphs)));
        assertThat(many.facts()).filteredOn(f -> f.label().equals("heading H1")).hasSize(10);

        String[] big = new String[2500];
        Arrays.fill(big, "x".repeat(150));
        Extraction capped = runDoc(summarizer, docx(null, null, documentXml(big)));
        assertThat(capped.limits()).contains("Text was read up to 200 KB; the rest of the document was not analysed.");
        assertThat(capped.facts().size()).isLessThanOrEqualTo(FactSink.CONTENT_FACT_CAP);
        assertThat(capped.facts()).contains(new SummaryFact("paragraphs", "2500", "word/document.xml"));
    }

    @Test
    void secretsInExtractedTextAreRedacted() {
        byte[] file = docx(coreXml("Runbook", null, null, null, null), null,
                documentXml("Rotate the service account password=hunter2 quarterly", "Owner: Meera Iyer"));
        Extraction e = runDoc(summarizer, file);

        assertThat(e.facts()).noneMatch(f -> f.value().contains("hunter2"));
        assertThat(e.facts()).anyMatch(f -> f.label().equals("first heading") && f.value().contains("[REDACTED]"));
        assertThat(e.contentSentence()).doesNotContain("hunter2");
    }

    @Test
    void anExternalEntityDoctypeIsRefusedWithAWarningAndNeverResolved() {
        String xxe = "<?xml version=\"1.0\"?><!DOCTYPE d [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]>"
                + "<w:document xmlns:w=\"" + TestFiles.W_NS + "\"><w:body><w:p><w:r><w:t>&xxe;</w:t></w:r></w:p></w:body></w:document>";
        byte[] file = docx(coreXml("Evil", null, null, null, null), null, xxe);

        Extraction e = runDoc(summarizer, file);

        assertThat(e.kind()).isEqualTo(ContentKind.DOCUMENT);
        assertThat(e.findings()).anyMatch(f -> f.severity() == Severity.WARNING && f.message().contains("DOCTYPE"));
        assertThat(e.limits()).anyMatch(l -> l.contains("DOCTYPE"));
        assertThat(e.facts()).contains(new SummaryFact("title", "Evil", "docProps/core.xml"));
        assertThat(e.facts()).noneMatch(f -> f.value().contains("root:"));
    }

    @Test
    void aDoctypeInTheCorePropertiesIsRefusedToo() {
        String core = "<?xml version=\"1.0\"?><!DOCTYPE x [<!ENTITY a \"b\">]><cp:coreProperties xmlns:cp=\"c\" "
                + "xmlns:dc=\"d\"><dc:title>&a;</dc:title></cp:coreProperties>";
        Extraction e = runDoc(summarizer, docx(core, null, documentXml("hello")));
        assertThat(e.findings()).anyMatch(f -> f.message().contains("docProps/core.xml contains a DOCTYPE"));
    }

    // ---- zip hardening: every failure degrades to BINARY + limit, never an exception -------------------------

    private static Extraction degraded(byte[] file) {
        assertThat(probe(file, null, null).format()).isEqualTo(Format.DOCX);
        return runDoc(new DocxSummarizer(), file);
    }

    @Test
    void aZipBombIsRejectedByCompressionRatio() {
        Map<String, byte[]> m = new LinkedHashMap<>();
        m.put("word/document.xml", b(documentXml("hi")));
        m.put("word/media/bomb.bin", new byte[15 * 1024 * 1024]); // 15 MB of zeros compresses ~1000:1
        Extraction e = degraded(TestFiles.zip(m));

        assertThat(e.kind()).isEqualTo(ContentKind.BINARY);
        assertThat(e.limits()).anyMatch(l -> l.contains("compression ratio above 100:1"));
        assertThat(e.findings()).anyMatch(f -> f.severity() == Severity.WARNING);
    }

    @Test
    void anEntryOverTwentyMegabytesIsRejected() {
        Map<String, byte[]> m = new LinkedHashMap<>();
        m.put("word/document.xml", b(documentXml("hi")));
        m.put("word/media/huge.bin", new byte[21 * 1024 * 1024]);
        Extraction e = degraded(TestFiles.zip(m));

        assertThat(e.kind()).isEqualTo(ContentKind.BINARY);
        assertThat(e.limits()).anyMatch(l -> l.contains("expands to more than 20 MB"));
    }

    @Test
    void moreThanTwoThousandEntriesIsRejected() {
        Map<String, byte[]> m = new LinkedHashMap<>();
        m.put("word/document.xml", b(documentXml("hi")));
        for (int i = 0; i < 2001; i++) m.put("word/parts/p" + i + ".xml", b("<a/>"));
        Extraction e = degraded(TestFiles.zip(m));

        assertThat(e.kind()).isEqualTo(ContentKind.BINARY);
        assertThat(e.limits()).anyMatch(l -> l.contains("more than 2000 entries"));
    }

    @Test
    void pathTraversalEntriesAreRejectedAndNothingIsWritten() {
        Map<String, byte[]> m = new LinkedHashMap<>();
        m.put("word/document.xml", b(documentXml("hi")));
        m.put("../../evil.txt", b("x"));
        Extraction e = degraded(TestFiles.zip(m));

        assertThat(e.kind()).isEqualTo(ContentKind.BINARY);
        assertThat(e.limits()).anyMatch(l -> l.contains("unsafe entry path"));
    }

    @Test
    void aTruncatedOrCorruptDocxDegradesCleanly() {
        byte[] whole = docx(coreXml("T", null, null, null, null), null, documentXml("a", "b", "c"));
        byte[] truncated = Arrays.copyOf(whole, whole.length * 6 / 10);
        Extraction e = degraded(truncated);
        assertThat(e.kind()).isEqualTo(ContentKind.BINARY);
        assertThat(e.limits()).anyMatch(l -> l.contains("corrupt or truncated"));

        byte[] corrupt = whole.clone();
        Arrays.fill(corrupt, 60, 200, (byte) 0x7);
        Extraction c = summarizer.summarize(new SummaryContext(probe(corrupt, null, null), "SRC", "app", new FactSink()));
        assertThat(c.kind()).isIn(ContentKind.BINARY, ContentKind.DOCUMENT); // never an exception
    }

    @Test
    void twoRunsGiveIdenticalResults() {
        byte[] file = policyDocx();
        assertThat(runDoc(summarizer, file, "p.docx", NOW, "Encryption in Transit", "in transit"))
                .isEqualTo(runDoc(summarizer, file, "p.docx", NOW, "Encryption in Transit", "in transit"));
    }
}
