package com.pramaan.backend.insight.summary;

import static com.pramaan.backend.insight.summary.Summarizing.runDoc;
import static com.pramaan.backend.insight.summary.TestFiles.b;
import static com.pramaan.backend.insight.summary.TestFiles.coreXml;
import static org.assertj.core.api.Assertions.assertThat;

import com.pramaan.backend.insight.InsightDtos.SummaryFact;
import com.pramaan.backend.insight.summary.SummaryEnums.Severity;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class XlsxPptxSummarizerTest {

    private final XlsxSummarizer xlsx = new XlsxSummarizer();
    private final PptxSummarizer pptx = new PptxSummarizer();

    @Test
    void workbookSheetsRowCountsUsedRangeAndHeadersAreExtracted() {
        byte[] file = TestFiles.xlsx(coreXml("Control Register", null, null, null, "2026-09-02T08:00:00Z"));
        Extraction e = runDoc(xlsx, file);

        assertThat(e.kind()).isEqualTo(ContentKind.DOCUMENT);
        assertThat(e.contentSentence()).isEqualTo("Spreadsheet of " + SummaryTemplates.humanSize(file.length)
                + ", 2 sheets; title \"Control Register\"; last modified 2026-09-02.");
        assertThat(e.facts()).contains(
                new SummaryFact("title", "Control Register", "docProps/core.xml"),
                // dimension element wins when present
                new SummaryFact("sheet Summary", "3 rows, used range A1:C3", "xl/worksheets/sheet1.xml"),
                // no dimension: computed from the cells (columns A..D, last row 5)
                new SummaryFact("sheet Detail", "2 rows, used range A1:D5", "xl/worksheets/sheet2.xml"),
                // shared strings resolved, a string split over runs is joined
                new SummaryFact("column headers (sheet Summary)", "Control ID, Control Name, Description",
                        "sheet Summary!row 1"));
    }

    @Test
    void xlsxFailuresAndDoctypesDegradeCleanly() {
        Extraction corrupt = runDoc(xlsx, Arrays.copyOf(TestFiles.xlsx(null), 300));
        assertThat(corrupt.kind()).isEqualTo(ContentKind.BINARY);
        assertThat(corrupt.limits()).anyMatch(l -> l.contains("corrupt or truncated"));

        Map<String, byte[]> m = new LinkedHashMap<>();
        m.put("xl/workbook.xml", b("<?xml version=\"1.0\"?><!DOCTYPE w [<!ENTITY x SYSTEM \"file:///etc/passwd\">]><workbook/>"));
        Extraction xxe = runDoc(xlsx, TestFiles.zip(m));
        assertThat(xxe.findings()).anyMatch(f -> f.severity() == Severity.WARNING && f.message().contains("DOCTYPE"));
    }

    @Test
    void headerRedactionStillApplies() {
        Map<String, byte[]> m = new LinkedHashMap<>();
        m.put("xl/workbook.xml", b("<workbook xmlns=\"" + TestFiles.SS_NS + "\"><sheets><sheet name=\"S\" sheetId=\"1\"/></sheets></workbook>"));
        m.put("xl/worksheets/sheet1.xml", b("<worksheet xmlns=\"" + TestFiles.SS_NS + "\"><sheetData><row r=\"1\">"
                + "<c r=\"A1\" t=\"inlineStr\"><is><t>password=hunter2</t></is></c></row></sheetData></worksheet>"));
        Extraction e = runDoc(xlsx, TestFiles.zip(m));
        assertThat(e.facts()).noneMatch(f -> f.value().contains("hunter2"));
    }

    @Test
    void pptxReportsPropertiesAndSlideCountOnly() {
        byte[] file = TestFiles.pptx(coreXml("Quarterly Review", null, null, null, "2026-07-15T00:00:00Z"), 3);
        Extraction e = runDoc(pptx, file);

        assertThat(e.contentSentence()).isEqualTo("Presentation of " + SummaryTemplates.humanSize(file.length)
                + ", 3 slides; title \"Quarterly Review\"; last modified 2026-07-15.");
        assertThat(e.facts()).contains(new SummaryFact("slides", "3", "ppt/slides"));
        assertThat(e.limits()).anyMatch(l -> l.contains("Slide text is not read"));
    }
}
