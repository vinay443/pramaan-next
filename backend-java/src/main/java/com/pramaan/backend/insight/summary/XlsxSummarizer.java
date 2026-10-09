package com.pramaan.backend.insight.summary;

import com.pramaan.backend.insight.summary.ContentProbe.Format;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import org.springframework.stereotype.Component;

/**
 * Excel workbooks, read with the JDK only: sheet names, per sheet row count and used range, and the
 * first row of the first sheet as column headers (shared strings resolved, first 10 cells). Cell values
 * beyond that header row are not read.
 */
@Component
public class XlsxSummarizer implements ContentSummarizer {

    private static final String WORKBOOK = "xl/workbook.xml";
    private static final String RELS = "xl/_rels/workbook.xml.rels";
    private static final String SHARED = "xl/sharedStrings.xml";
    private static final String REL_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";
    private static final int MAX_SHEETS_SHOWN = 20;
    private static final int HEADER_CELLS = 10;

    @Override
    public int order() {
        return 80;
    }

    @Override
    public boolean supports(ContentProbe probe) {
        return probe.format() == Format.XLSX;
    }

    private record Sheet(String name, String part) {}

    private static final class SheetStats {
        int rows;
        long maxRow;
        int maxCol;
        String dimension;
        long firstRowNumber = -1;
        final List<String[]> firstRow = new ArrayList<>(); // {type, raw}
    }

    @Override
    public Extraction summarize(SummaryContext ctx) {
        FactSink sink = ctx.sink();
        ContentProbe p = ctx.probe();
        OoxmlPackage pkg;
        try {
            pkg = OoxmlPackage.read(p.bytes(), n -> n.equals(DocumentFacts.CORE) || n.equals(WORKBOOK) || n.equals(RELS)
                    || n.equals(SHARED) || n.startsWith("xl/worksheets/") && n.endsWith(".xml"));
        } catch (OoxmlPackage.Rejected e) {
            return BinaryMetadataSummarizer.degrade(ctx, "The XLSX was not read: " + e.getMessage() + ".");
        } catch (IOException | RuntimeException e) {
            return BinaryMetadataSummarizer.degrade(ctx, "The XLSX could not be read (corrupt or truncated).");
        }

        DocumentFacts.Core core = DocumentFacts.readCore(pkg, sink);
        List<Sheet> sheets = new ArrayList<>();
        try {
            sheets = sheets(pkg);
        } catch (OoxmlPackage.XmlRefused e) {
            DocumentFacts.refused(sink, e);
        } catch (XMLStreamException | RuntimeException e) {
            DocumentFacts.unparsable(sink, WORKBOOK, e);
        }

        boolean first = true;
        int shown = 0;
        for (Sheet s : sheets) {
            if (shown == MAX_SHEETS_SHOWN) {
                sink.limit("Only the first " + MAX_SHEETS_SHOWN + " of " + sheets.size() + " sheets are listed.");
                break;
            }
            try {
                XMLStreamReader r = pkg.xml(s.part());
                if (r == null) {
                    sink.fact("sheet " + s.name(), "part not found", WORKBOOK);
                    continue;
                }
                SheetStats st = scan(r);
                r.close();
                shown++;
                String range = st.dimension != null ? st.dimension
                        : st.rows == 0 ? "empty" : "A1:" + column(Math.max(st.maxCol, 1)) + st.maxRow;
                sink.fact("sheet " + s.name(), SummaryTemplates.plural(st.rows, "row", "rows") + ", used range " + range,
                        s.part());
                if (first) {
                    first = false;
                    headerFact(pkg, sink, s, st);
                }
            } catch (OoxmlPackage.XmlRefused e) {
                DocumentFacts.refused(sink, e);
            } catch (XMLStreamException | RuntimeException e) {
                DocumentFacts.unparsable(sink, s.part(), e);
            }
        }

        String detail = SummaryTemplates.plural(sheets.size(), "sheet", "sheets");
        return sink.build(ContentKind.DOCUMENT, SummaryTemplates.fileSentence("Spreadsheet", p.totalSize(), detail,
                FactSink.text(core.title()), DocumentFacts.day(core.modified()), null));
    }

    private static List<Sheet> sheets(OoxmlPackage pkg) throws OoxmlPackage.XmlRefused, XMLStreamException {
        Map<String, String> targets = new HashMap<>();
        XMLStreamReader rels = pkg.xml(RELS);
        if (rels != null) {
            while (rels.hasNext()) {
                if (rels.next() == XMLStreamConstants.START_ELEMENT && rels.getLocalName().equals("Relationship")) {
                    String id = DocxSummarizer.attr(rels, "Id");
                    String target = DocxSummarizer.attr(rels, "Target");
                    if (id != null && target != null) {
                        targets.put(id, target.startsWith("/") ? target.substring(1) : "xl/" + target);
                    }
                }
            }
            rels.close();
        }
        List<Sheet> out = new ArrayList<>();
        XMLStreamReader wb = pkg.xml(WORKBOOK);
        if (wb != null) {
            int n = 0;
            while (wb.hasNext()) {
                if (wb.next() == XMLStreamConstants.START_ELEMENT && wb.getLocalName().equals("sheet")) {
                    n++;
                    String name = DocxSummarizer.attr(wb, "name");
                    String rid = wb.getAttributeValue(REL_NS, "id");
                    String part = rid == null ? null : targets.get(rid);
                    out.add(new Sheet(name == null ? "#" + n : name, part == null ? "xl/worksheets/sheet" + n + ".xml" : part));
                }
            }
            wb.close();
        }
        return out;
    }

    private static SheetStats scan(XMLStreamReader r) throws XMLStreamException {
        SheetStats st = new SheetStats();
        long currentRow = -1;
        String cellType = null;
        while (r.hasNext()) {
            int ev = r.next();
            if (ev != XMLStreamConstants.START_ELEMENT) {
                continue;
            }
            switch (r.getLocalName()) {
                case "dimension" -> st.dimension = DocxSummarizer.attr(r, "ref");
                case "row" -> {
                    st.rows++;
                    String rn = DocxSummarizer.attr(r, "r");
                    currentRow = rn != null && rn.matches("\\d+") ? Long.parseLong(rn) : st.rows;
                    st.maxRow = Math.max(st.maxRow, currentRow);
                    if (st.firstRowNumber < 0) {
                        st.firstRowNumber = currentRow;
                    }
                }
                case "c" -> {
                    cellType = DocxSummarizer.attr(r, "t");
                    String ref = DocxSummarizer.attr(r, "r");
                    if (ref != null) {
                        st.maxCol = Math.max(st.maxCol, columnIndex(ref));
                    }
                }
                case "v", "t" -> {
                    if (currentRow == st.firstRowNumber && st.firstRow.size() < HEADER_CELLS) {
                        String raw = r.getElementText();
                        if (!raw.isBlank()) {
                            st.firstRow.add(new String[]{cellType == null ? "n" : cellType, raw});
                        }
                    }
                }
                default -> { }
            }
        }
        return st;
    }

    private static void headerFact(OoxmlPackage pkg, FactSink sink, Sheet s, SheetStats st) {
        if (st.firstRow.isEmpty()) {
            return;
        }
        int maxIndex = -1;
        for (String[] c : st.firstRow) {
            if (c[0].equals("s") && c[1].strip().matches("\\d+")) {
                maxIndex = Math.max(maxIndex, Integer.parseInt(c[1].strip()));
            }
        }
        List<String> strings = maxIndex >= 0 ? sharedStrings(pkg, sink, maxIndex) : List.of();
        List<String> cells = new ArrayList<>();
        for (String[] c : st.firstRow) {
            if (c[0].equals("s") && c[1].strip().matches("\\d+")) {
                int i = Integer.parseInt(c[1].strip());
                cells.add(i < strings.size() ? strings.get(i) : "?");
            } else {
                cells.add(c[1].strip());
            }
        }
        sink.fact("column headers (sheet " + s.name() + ")", String.join(", ", cells),
                "sheet " + s.name() + "!row " + st.firstRowNumber);
    }

    /** The first {@code maxIndex + 1} shared strings, in order. */
    private static List<String> sharedStrings(OoxmlPackage pkg, FactSink sink, int maxIndex) {
        List<String> out = new ArrayList<>();
        try {
            XMLStreamReader r = pkg.xml(SHARED);
            if (r == null) {
                return out;
            }
            StringBuilder cur = null;
            while (r.hasNext() && out.size() <= maxIndex) {
                int ev = r.next();
                if (ev == XMLStreamConstants.START_ELEMENT) {
                    if (r.getLocalName().equals("si")) {
                        cur = new StringBuilder();
                    } else if (r.getLocalName().equals("t") && cur != null) {
                        cur.append(r.getElementText());
                    }
                } else if (ev == XMLStreamConstants.END_ELEMENT && r.getLocalName().equals("si") && cur != null) {
                    out.add(cur.toString());
                    cur = null;
                }
            }
            r.close();
        } catch (OoxmlPackage.XmlRefused e) {
            DocumentFacts.refused(sink, e);
        } catch (XMLStreamException | RuntimeException e) {
            DocumentFacts.unparsable(sink, SHARED, e);
        }
        return out;
    }

    private static int columnIndex(String cellRef) {
        int idx = 0;
        for (char c : cellRef.toCharArray()) {
            if (c >= 'A' && c <= 'Z') idx = idx * 26 + (c - 'A' + 1);
            else if (c >= 'a' && c <= 'z') idx = idx * 26 + (c - 'a' + 1);
            else break;
        }
        return idx;
    }

    private static String column(int index) {
        StringBuilder sb = new StringBuilder();
        for (int i = index; i > 0; i = (i - 1) / 26) {
            sb.insert(0, (char) ('A' + (i - 1) % 26));
        }
        return sb.toString();
    }
}
