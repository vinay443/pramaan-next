package com.pramaan.backend.insight.summary;

import com.pramaan.backend.insight.summary.SummaryEnums.Severity;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Text, logs, markdown, XML, CSV and TSV — and the fallback for content that looks like JSON but
 * does not parse. Reports size, line count, the first non-empty line, CSV header and row count, and
 * any dates written in the text.
 */
@Component
public class PlainTextSummarizer implements ContentSummarizer {

    private static final Pattern ISO_DATE = Pattern.compile("(?<!\\d)(\\d{4})-(\\d{2})-(\\d{2})(?!\\d)");
    private static final Pattern SLASH_DATE = Pattern.compile("\\b\\d{1,2}/\\d{1,2}/\\d{4}\\b");
    private static final Pattern NAMED_DATE = Pattern.compile(
            "(?i)\\b\\d{1,2}\\s+(?:jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*\\.?,?\\s+\\d{4}\\b");
    private static final int MAX_DATES = 3;
    private static final int MAX_LINES_SCANNED = 5000;
    private static final int MAX_TITLE_CHARS = 120;

    @Override
    public int order() {
        return 50;
    }

    @Override
    public boolean supports(ContentProbe probe) {
        return probe.text() != null;
    }

    @Override
    public Extraction summarize(SummaryContext ctx) {
        FactSink sink = ctx.sink();
        ContentProbe p = ctx.probe();
        String text = p.text();
        String[] lines = text.isEmpty() ? new String[0] : text.split("\r\n|\n|\r", -1);
        int lineCount = lines.length;
        if (lineCount > 0 && lines[lineCount - 1].isEmpty()) {
            lineCount--;
        }

        if (p.jsonInvalid()) {
            sink.finding(Severity.WARNING, p.truncated()
                    ? "The content looks like JSON but was truncated at the read limit, so it could not be parsed; summarised as plain text."
                    : "The content looks like JSON but is not valid JSON; summarised as plain text.", "line 1");
        }
        if (text.isEmpty()) {
            sink.limit("The stored content is empty.");
        }

        sink.fact("size", SummaryTemplates.humanSize(p.totalSize()), "content");
        sink.fact("lines", String.valueOf(lineCount), "content");

        int firstIdx = -1;
        for (int i = 0; i < lineCount; i++) {
            if (!lines[i].isBlank()) {
                firstIdx = i;
                break;
            }
        }
        String firstLine = firstIdx < 0 ? null : lines[firstIdx].strip();

        String delimiter = delimiter(p, lines, lineCount);
        String kind = kindLabel(p, text, delimiter);
        String detail = SummaryTemplates.plural(lineCount, "line", "lines");
        String title = null;

        if (delimiter != null) {
            char d = delimiter.charAt(0);
            int headerCols = splitRow(lines[firstIdx], d).size();
            int dataRows = 0;
            int emptyCells = 0;
            int inconsistent = 0;
            int firstBad = -1;
            int firstBadCols = 0;
            for (int i = firstIdx + 1; i < lineCount; i++) {
                if (lines[i].isBlank()) continue;
                dataRows++;
                List<String> cells = splitRow(lines[i], d);
                for (String c : cells) {
                    if (c.isBlank()) emptyCells++;
                }
                if (cells.size() != headerCols) {
                    inconsistent++;
                    if (firstBad < 0) {
                        firstBad = i + 1;
                        firstBadCols = cells.size();
                    }
                }
            }
            sink.fact("header", firstLine, "line " + (firstIdx + 1));
            sink.fact("columns", String.valueOf(headerCols), "line " + (firstIdx + 1));
            sink.fact("data rows", String.valueOf(dataRows), "content");
            sink.fact("empty cells", String.valueOf(emptyCells), "content");
            if (inconsistent > 0) {
                sink.finding(Severity.WARNING, inconsistent + " of " + SummaryTemplates.plural(dataRows, "data row", "data rows")
                        + " do not have the header's " + SummaryTemplates.plural(headerCols, "column", "columns")
                        + " (first: line " + firstBad + " has " + firstBadCols + ").", "line " + firstBad);
            }
            sink.limit("Quoted fields that span several lines are not supported; such rows may be counted as inconsistent.");
            detail = SummaryTemplates.plural(dataRows, "data row", "data rows") + ", "
                    + SummaryTemplates.plural(headerCols, "column", "columns");
        } else if (firstLine != null) {
            sink.fact("first line", firstLine, "line " + (firstIdx + 1));
            if (kind.equals("Text") || kind.equals("Markdown document")) {
                String t = firstLine.replaceFirst("^#+\\s*", "").strip();
                if (!t.isEmpty() && t.length() <= MAX_TITLE_CHARS) {
                    title = FactSink.text(t);
                }
            }
        }

        List<String[]> dates = findDates(lines, lineCount);
        for (String[] d : dates) {
            sink.fact("date", d[0], "line " + d[1]);
        }
        String date = dates.isEmpty() ? null : dates.get(0)[0];

        return sink.build(ContentKind.TEXT,
                SummaryTemplates.documentSentence(kind, p.totalSize(), detail, title, date));
    }

    private static String kindLabel(ContentProbe p, String text, String delimiter) {
        if (delimiter != null) {
            return delimiter.equals("\t") ? "TSV" : "CSV";
        }
        String ext = p.extension();
        String head = text.stripLeading();
        if (head.startsWith("<?xml") || ext.equals("xml")) return "XML document";
        if (ext.equals("md") || ext.equals("markdown")) return "Markdown document";
        if (ext.equals("log")) return "Log";
        if (ext.equals("html") || ext.equals("htm")) return "HTML document";
        return "Text";
    }

    /** Delimiter when the content is a consistent CSV/TSV, else null. */
    private static String delimiter(ContentProbe p, String[] lines, int lineCount) {
        String ext = p.extension();
        String declared = p.declaredContentType() == null ? "" : p.declaredContentType().toLowerCase(Locale.ROOT);
        boolean hinted = ext.equals("csv") || ext.equals("tsv") || declared.contains("csv") || declared.contains("tab-separated");
        List<String> sample = new ArrayList<>();
        for (int i = 0; i < lineCount && sample.size() < 5; i++) {
            if (!lines[i].isBlank()) sample.add(lines[i]);
        }
        int nonEmpty = 0;
        for (int i = 0; i < lineCount; i++) {
            if (!lines[i].isBlank()) nonEmpty++;
        }
        if (sample.size() < 2 || (!hinted && nonEmpty < 3)) {
            return null;
        }
        for (String d : new String[]{ext.equals("tsv") ? "\t" : ",", ext.equals("tsv") ? "," : "\t"}) {
            int expected = count(sample.get(0), d);
            if (expected < 1) continue;
            boolean consistent = true;
            for (String s : sample) {
                if (count(s, d) != expected) {
                    consistent = false;
                    break;
                }
            }
            if (consistent || hinted) return d;
        }
        return null;
    }

    /** Splits one CSV/TSV row, honouring double-quoted fields (no multi-line fields). */
    static List<String> splitRow(String line, char delimiter) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quoted) {
                if (c == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cur.append('"');
                    i++;
                } else if (c == '"') {
                    quoted = false;
                } else {
                    cur.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == delimiter) {
                out.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        out.add(cur.toString());
        return out;
    }

    private static int count(String s, String d) {
        int n = 0;
        for (int i = s.indexOf(d); i >= 0; i = s.indexOf(d, i + 1)) n++;
        return n;
    }

    /** First few distinct dates as {text, lineNumber}, in order of appearance. */
    private static List<String[]> findDates(String[] lines, int lineCount) {
        Set<String> seen = new LinkedHashSet<>();
        List<String[]> out = new ArrayList<>();
        int limit = Math.min(lineCount, MAX_LINES_SCANNED);
        for (int i = 0; i < limit && out.size() < MAX_DATES; i++) {
            String line = lines[i];
            Matcher iso = ISO_DATE.matcher(line);
            while (iso.find() && out.size() < MAX_DATES) {
                try {
                    LocalDate.of(Integer.parseInt(iso.group(1)), Integer.parseInt(iso.group(2)), Integer.parseInt(iso.group(3)));
                    add(seen, out, iso.group(), i + 1);
                } catch (RuntimeException ignored) {
                    // not a real calendar date
                }
            }
            for (Pattern pat : new Pattern[]{NAMED_DATE, SLASH_DATE}) {
                Matcher m = pat.matcher(line);
                while (m.find() && out.size() < MAX_DATES) {
                    add(seen, out, m.group(), i + 1);
                }
            }
        }
        return out;
    }

    private static void add(Set<String> seen, List<String[]> out, String date, int line) {
        if (seen.add(date)) {
            out.add(new String[]{date, String.valueOf(line)});
        }
    }
}
