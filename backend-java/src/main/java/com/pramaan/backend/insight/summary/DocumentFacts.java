package com.pramaan.backend.insight.summary;

import com.pramaan.backend.insight.summary.SummaryEnums.Severity;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/** Shared pieces of the PDF / Office summarizers: document properties, XML failure reporting, signals. */
final class DocumentFacts {

    static final String CORE = "docProps/core.xml";
    static final String APP = "docProps/app.xml";
    static final int MAX_TEXT_CHARS = 200_000;
    private static final Pattern POLICY_LIKE = Pattern.compile("(?i)\\b(policy|procedure|standard|guideline|charter)\\b");

    private DocumentFacts() {}

    record Core(String title, String creator, String lastModifiedBy, String created, String modified) {}

    record Outcome(String approver, String firstHeading) {}

    /** docProps/core.xml: title, creator, lastModifiedBy, created, modified. Missing/unparsable parts give nulls. */
    static Core readCore(OoxmlPackage pkg, FactSink sink) {
        String title = null, creator = null, modifiedBy = null, created = null, modified = null;
        try {
            XMLStreamReader r = pkg.xml(CORE);
            if (r != null) {
                while (r.hasNext()) {
                    if (r.next() == XMLStreamConstants.START_ELEMENT) {
                        switch (r.getLocalName()) {
                            case "title" -> title = r.getElementText();
                            case "creator" -> creator = r.getElementText();
                            case "lastModifiedBy" -> modifiedBy = r.getElementText();
                            case "created" -> created = r.getElementText();
                            case "modified" -> modified = r.getElementText();
                            default -> { }
                        }
                    }
                }
                r.close();
            }
        } catch (OoxmlPackage.XmlRefused e) {
            refused(sink, e);
        } catch (XMLStreamException | RuntimeException e) {
            unparsable(sink, CORE, e);
        }
        sink.fact("title", clean(title), CORE);
        sink.fact("creator", clean(creator), CORE);
        sink.fact("last modified by", clean(modifiedBy), CORE);
        sink.fact("created", clean(created), CORE);
        sink.fact("modified", clean(modified), CORE);
        return new Core(clean(title), clean(creator), clean(modifiedBy), clean(created), clean(modified));
    }

    /** docProps/app.xml: the named integer-ish elements (e.g. Pages, Words, Slides) as facts; returns their values. */
    static java.util.Map<String, String> readApp(OoxmlPackage pkg, FactSink sink, String... elements) {
        java.util.Map<String, String> found = new java.util.LinkedHashMap<>();
        try {
            XMLStreamReader r = pkg.xml(APP);
            if (r != null) {
                List<String> wanted = List.of(elements);
                while (r.hasNext()) {
                    if (r.next() == XMLStreamConstants.START_ELEMENT && wanted.contains(r.getLocalName())) {
                        String v = clean(r.getElementText());
                        if (v != null) {
                            found.put(r.getLocalName(), v);
                        }
                    }
                }
                r.close();
            }
        } catch (OoxmlPackage.XmlRefused e) {
            refused(sink, e);
        } catch (XMLStreamException | RuntimeException e) {
            unparsable(sink, APP, e);
        }
        found.forEach((k, v) -> sink.fact(k.toLowerCase(Locale.ROOT), v, APP));
        return found;
    }

    static void refused(FactSink sink, OoxmlPackage.XmlRefused e) {
        sink.finding(Severity.WARNING, e.getMessage() + ".", "package");
        sink.limit("Part of the file was not read: " + e.getMessage() + ".");
    }

    static void unparsable(FactSink sink, String part, Exception e) {
        sink.finding(Severity.WARNING, part + " could not be parsed (" + e.getClass().getSimpleName() + ").", part);
        sink.limit(part + " could not be parsed, so its content is not shown.");
    }

    static String clean(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    /** "2026-09-01T10:00:00Z" -> "2026-09-01"; anything else unchanged. */
    static String day(String s) {
        return s != null && s.length() >= 10 && s.substring(0, 10).matches("\\d{4}-\\d{2}-\\d{2}") ? s.substring(0, 10) : s;
    }

    /**
     * Runs {@link DocumentSignals} over the text units and reports each signal, finding and the informational
     * keyword overlap into the sink. {@code hint} (file name, title, ...) only helps decide "policy-type".
     */
    static Outcome signals(SummaryContext ctx, List<DocumentSignals.Unit> units, String... hints) {
        FactSink sink = ctx.sink();
        StringBuilder hint = new StringBuilder(ctx.controlName() == null ? "" : ctx.controlName());
        for (String h : hints) {
            hint.append(' ').append(h == null ? "" : h);
        }
        LocalDate today = LocalDate.ofInstant(ctx.now(), ZoneOffset.UTC);
        DocumentSignals.Result res = DocumentSignals.analyse(units, today, POLICY_LIKE.matcher(hint).find());
        for (DocumentSignals.Signal s : res.signals()) {
            sink.fact(s.label(), s.value(), s.pointer());
        }
        res.findings().forEach(f -> sink.finding(f.severity(), f.message(), f.pointer()));

        StringBuilder text = new StringBuilder();
        for (DocumentSignals.Unit u : units) {
            if (u.text() != null) {
                text.append(u.text()).append('\n');
            }
        }
        if (text.length() == 0) {
            sink.limit("No text could be extracted, so no document signals are shown.");
        } else if (ctx.controlName() != null) {
            sink.fact(DocumentSignals.OVERLAP_LABEL, DocumentSignals.overlapValue(
                    DocumentSignals.keywordOverlap(ctx.controlName(), ctx.controlDescription(), text.toString())), "text");
        }
        return new Outcome(res.approver() == null ? null : FactSink.text(res.approver()),
                res.firstHeading() == null ? null : FactSink.text(res.firstHeading()));
    }
}
