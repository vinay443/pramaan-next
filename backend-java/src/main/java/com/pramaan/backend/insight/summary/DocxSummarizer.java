package com.pramaan.backend.insight.summary;

import com.pramaan.backend.insight.summary.ContentProbe.Format;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import org.springframework.stereotype.Component;

/**
 * Word documents, read with the JDK only (zip + StAX): document properties, paragraph and table counts,
 * Heading 1-3 paragraphs, and the body text for {@link DocumentSignals}. Nothing is written to disk and
 * DOCTYPE declarations are refused.
 */
@Component
public class DocxSummarizer implements ContentSummarizer {

    static final String DOCUMENT = "word/document.xml";
    private static final Set<String> PARTS = Set.of(DocumentFacts.CORE, DocumentFacts.APP, DOCUMENT);
    private static final Pattern HEADING = Pattern.compile("(?i)heading\\s*([1-3])");
    private static final int MAX_HEADINGS = 10;

    @Override
    public int order() {
        return 70;
    }

    @Override
    public boolean supports(ContentProbe probe) {
        return probe.format() == Format.DOCX;
    }

    @Override
    public Extraction summarize(SummaryContext ctx) {
        FactSink sink = ctx.sink();
        ContentProbe p = ctx.probe();
        OoxmlPackage pkg;
        try {
            pkg = OoxmlPackage.read(p.bytes(), PARTS::contains);
        } catch (OoxmlPackage.Rejected e) {
            return BinaryMetadataSummarizer.degrade(ctx, "The DOCX was not read: " + e.getMessage() + ".");
        } catch (IOException | RuntimeException e) {
            return BinaryMetadataSummarizer.degrade(ctx, "The DOCX could not be read (corrupt or truncated).");
        }

        DocumentFacts.Core core = DocumentFacts.readCore(pkg, sink);
        Map<String, String> app = DocumentFacts.readApp(pkg, sink, "Pages", "Words");

        int paragraphs = 0;
        int tables = 0;
        List<DocumentSignals.Unit> units = new ArrayList<>();
        List<String[]> headings = new ArrayList<>();
        boolean capped = false;
        try {
            XMLStreamReader r = pkg.xml(DOCUMENT);
            if (r == null) {
                return BinaryMetadataSummarizer.degrade(ctx, "The DOCX has no " + DOCUMENT + " part.");
            }
            StringBuilder cur = new StringBuilder();
            String style = null;
            long chars = 0;
            while (r.hasNext()) {
                int ev = r.next();
                if (ev == XMLStreamConstants.START_ELEMENT) {
                    switch (r.getLocalName()) {
                        case "p" -> {
                            paragraphs++;
                            cur.setLength(0);
                            style = null;
                        }
                        case "pStyle" -> style = attr(r, "val");
                        case "tbl" -> tables++;
                        case "t" -> cur.append(r.getElementText());
                        case "tab" -> cur.append('\t');
                        case "br" -> cur.append(' ');
                        default -> { }
                    }
                } else if (ev == XMLStreamConstants.END_ELEMENT && r.getLocalName().equals("p")) {
                    String text = cur.toString().strip();
                    if (!text.isEmpty()) {
                        String ptr = DOCUMENT + " (paragraph " + paragraphs + ")";
                        if (chars + text.length() <= DocumentFacts.MAX_TEXT_CHARS) {
                            units.add(new DocumentSignals.Unit(ptr, text));
                            chars += text.length();
                        } else {
                            capped = true;
                        }
                        Matcher m = style == null ? null : HEADING.matcher(style);
                        if (m != null && m.find() && headings.size() < MAX_HEADINGS) {
                            headings.add(new String[]{"heading H" + m.group(1), text, ptr});
                        }
                    }
                    cur.setLength(0);
                }
            }
            r.close();
        } catch (OoxmlPackage.XmlRefused e) {
            DocumentFacts.refused(sink, e);
        } catch (XMLStreamException | RuntimeException e) {
            DocumentFacts.unparsable(sink, DOCUMENT, e);
        }
        if (capped) {
            sink.limit("Text was read up to " + (DocumentFacts.MAX_TEXT_CHARS / 1000) + " KB; the rest of the document was not analysed.");
        }

        sink.fact("paragraphs", String.valueOf(paragraphs), DOCUMENT);
        sink.fact("tables", String.valueOf(tables), DOCUMENT);
        for (String[] h : headings) {
            sink.fact(h[0], h[1], h[2]);
        }
        DocumentFacts.Outcome out = DocumentFacts.signals(ctx, units, p.fileName(), core.title(),
                headings.isEmpty() ? null : headings.get(0)[1]);

        String title = core.title() != null ? core.title()
                : !headings.isEmpty() ? headings.get(0)[1] : out.firstHeading();
        String detail = app.containsKey("Pages")
                ? SummaryTemplates.plural(Long.parseLong(digits(app.get("Pages"))), "page", "pages")
                : SummaryTemplates.plural(paragraphs, "paragraph", "paragraphs");
        return sink.build(ContentKind.DOCUMENT, SummaryTemplates.fileSentence("DOCX", p.totalSize(), detail,
                FactSink.text(title), DocumentFacts.day(core.modified()), out.approver()));
    }

    private static String digits(String s) {
        String d = s.replaceAll("\\D", "");
        return d.isEmpty() ? "0" : d;
    }

    static String attr(XMLStreamReader r, String local) {
        for (int i = 0; i < r.getAttributeCount(); i++) {
            if (r.getAttributeLocalName(i).equals(local)) {
                return r.getAttributeValue(i);
            }
        }
        return null;
    }
}
