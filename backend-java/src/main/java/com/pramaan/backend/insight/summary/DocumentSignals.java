package com.pramaan.backend.insight.summary;

import com.pramaan.backend.insight.InsightDtos.SummaryFinding;
import com.pramaan.backend.insight.summary.SummaryEnums.Severity;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure, regex-based signals over text extracted from a document (PDF / DOCX): the first heading-like
 * line, dates, version markers, ownership/approval markers and review-cycle markers, plus an
 * informational keyword overlap with the control description. Nothing is inferred beyond what the
 * text literally says, and nothing here claims a document proves a control.
 */
public final class DocumentSignals {

    /** A piece of extracted text and where it came from ("page 3", "word/document.xml (paragraph 4)"). */
    public record Unit(String pointer, String text) {}

    /** One extracted signal; {@code pointer} may carry an "(as written: …)" note. */
    public record Signal(String label, String value, String pointer) {}

    public record Result(List<Signal> signals, List<SummaryFinding> findings, String approver,
                         String firstHeading, boolean ownerMarkerFound) {}

    public static final int MAX_HEADING_CHARS = 120;
    public static final int MAX_DATES = 5;
    public static final int MAX_VERSIONS = 3;
    public static final int MAX_NAME_CHARS = 60;
    public static final int MAX_TERMS = 8;
    public static final String OVERLAP_LABEL = "Keyword overlap with the control description (informational)";

    private static final String MONTHS = "jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|jun(?:e)?|jul(?:y)?|"
            + "aug(?:ust)?|sep(?:t(?:ember)?)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?";

    private static final Pattern ISO = Pattern.compile("(?<!\\d)(\\d{4})-(\\d{2})-(\\d{2})(?!\\d)");
    private static final Pattern DAY_FIRST = Pattern.compile("(?<!\\d)(\\d{1,2})[/.](\\d{1,2})[/.](\\d{4})(?!\\d)");
    private static final Pattern D_MON_Y = Pattern.compile(
            "(?i)(?<!\\d)(\\d{1,2})(?:st|nd|rd|th)?\\s+(" + MONTHS + ")\\.?,?\\s+(\\d{4})(?!\\d)");
    private static final Pattern MON_D_Y = Pattern.compile(
            "(?i)\\b(" + MONTHS + ")\\.?\\s+(\\d{1,2})(?:st|nd|rd|th)?,?\\s+(\\d{4})(?!\\d)");

    private static final Pattern VERSION = Pattern.compile(
            "\\b(?i:version|ver\\.?)\\s*(\\d+(?:\\.\\d+){0,3})\\b"
                    + "|(?<![\\w.])(?i:v)(\\d+(?:\\.\\d+){1,3}|\\d+)\\b"
                    + "|\\b(?i:rev(?:ision)?)(?:\\.|\\s)\\s*([A-Z0-9]{1,3})\\b");

    private static final Pattern OWNER = Pattern.compile(
            "\\b(?i:approved by|reviewed by|prepared by|owner|signed(?: by)?)\\b[ \\t]*[:\\-–]?[ \\t]*"
                    + "([A-Z][\\p{L}.'’-]*(?:[ \\t]+[A-Z][\\p{L}.'’-]*){0,4})");

    private static final Pattern REVIEW_MARKER = Pattern.compile(
            "(?i)\\b(next review(?: date)?|review date|review due|valid until|valid till|expiry date|expires on|"
                    + "effective date)\\b[ \\t]*[:\\-–]?[ \\t]*");

    private static final Set<String> NO_WARNING_MARKERS = Set.of("effective date");

    private static final Set<String> STOPWORDS = Set.of(
            "that", "with", "this", "from", "must", "shall", "have", "should", "which", "their", "there",
            "been", "will", "when", "such", "other", "into", "only", "also", "than", "each", "these",
            "those", "your", "they", "them", "about", "after", "before", "under", "over", "between",
            "being", "where", "while", "would", "could", "does", "made", "make", "more", "most", "some",
            "used", "using", "well", "were", "what", "whether", "within", "without");

    private DocumentSignals() {}

    // ---- dates ----------------------------------------------------------------------------

    /** A date found in text: normalised ISO value, the text as written, and its position. */
    public record DateHit(String iso, String written, int start, int end) {}

    public static List<DateHit> findDates(String line) {
        List<DateHit> hits = new ArrayList<>();
        collect(ISO, line, hits, m -> LocalDate.of(i(m, 1), i(m, 2), i(m, 3)));
        collect(DAY_FIRST, line, hits, m -> LocalDate.of(i(m, 3), i(m, 2), i(m, 1)));
        collect(D_MON_Y, line, hits, m -> LocalDate.of(i(m, 3), month(m.group(2)), i(m, 1)));
        collect(MON_D_Y, line, hits, m -> LocalDate.of(i(m, 3), month(m.group(1)), i(m, 2)));
        hits.sort((a, b) -> Integer.compare(a.start(), b.start()));
        List<DateHit> out = new ArrayList<>();
        int lastEnd = -1;
        for (DateHit h : hits) {
            if (h.start() >= lastEnd) {
                out.add(h);
                lastEnd = h.end();
            }
        }
        return out;
    }

    private interface Parse {
        LocalDate apply(Matcher m);
    }

    private static void collect(Pattern p, String line, List<DateHit> out, Parse parse) {
        Matcher m = p.matcher(line);
        while (m.find()) {
            try {
                out.add(new DateHit(parse.apply(m).toString(), m.group(), m.start(), m.end()));
            } catch (RuntimeException notARealDate) {
                // e.g. 31/02/2026 — not a calendar date, ignore
            }
        }
    }

    private static int i(Matcher m, int g) {
        return Integer.parseInt(m.group(g));
    }

    private static int month(String name) {
        String k = name.toLowerCase(Locale.ROOT).substring(0, 3);
        return List.of("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec").indexOf(k) + 1;
    }

    // ---- signals ---------------------------------------------------------------------------

    /**
     * @param today      the injected clock's current day (for the past-review-date warning)
     * @param policyLike whether the document looks like a policy/procedure (drives the no-owner INFO)
     */
    public static Result analyse(List<Unit> units, LocalDate today, boolean policyLike) {
        List<Signal> signals = new ArrayList<>();
        List<SummaryFinding> findings = new ArrayList<>();
        String firstHeading = null;
        String firstHeadingPointer = null;
        Set<String> seenDates = new LinkedHashSet<>();
        Set<String> seenVersions = new LinkedHashSet<>();
        Map<String, String> owners = new LinkedHashMap<>();
        Set<String> seenReview = new LinkedHashSet<>();
        int dateCount = 0;
        int versionCount = 0;

        for (Unit u : units) {
            if (u.text() == null) {
                continue;
            }
            for (String raw : u.text().split("\\R")) {
                String line = raw.strip();
                if (line.isEmpty()) {
                    continue;
                }
                if (firstHeading == null && line.length() <= MAX_HEADING_CHARS) {
                    firstHeading = line;
                    firstHeadingPointer = u.pointer();
                }
                for (DateHit h : findDates(line)) {
                    if (dateCount < MAX_DATES && seenDates.add(h.iso())) {
                        dateCount++;
                        signals.add(new Signal("date", h.iso(), u.pointer() + " (as written: " + h.written() + ")"));
                    }
                }
                Matcher rm = REVIEW_MARKER.matcher(line);
                while (rm.find()) {
                    String marker = rm.group(1).toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
                    List<DateHit> after = findDates(line.substring(rm.end()));
                    if (after.isEmpty() || after.get(0).start() != 0) {
                        continue;
                    }
                    DateHit h = after.get(0);
                    if (!seenReview.add(marker + "|" + h.iso())) {
                        continue;
                    }
                    signals.add(new Signal(marker, h.iso(), u.pointer() + " (as written: " + h.written() + ")"));
                    if (!NO_WARNING_MARKERS.contains(marker) && LocalDate.parse(h.iso()).isBefore(today)) {
                        findings.add(new SummaryFinding(Severity.WARNING,
                                "Document states it was due for review on " + h.iso() + ".", u.pointer()));
                    }
                }
                Matcher vm = VERSION.matcher(line);
                while (vm.find() && versionCount < MAX_VERSIONS) {
                    String v = vm.group(1) != null ? vm.group(1) : vm.group(2) != null ? vm.group(2)
                            : "Rev " + vm.group(3);
                    if (seenVersions.add(v)) {
                        versionCount++;
                        signals.add(new Signal("version", v, u.pointer()));
                    }
                }
                Matcher om = OWNER.matcher(line);
                while (om.find()) {
                    String kind = markerOf(om.group());
                    String name = om.group(1).strip();
                    if (name.length() > MAX_NAME_CHARS) {
                        name = name.substring(0, MAX_NAME_CHARS).strip();
                    }
                    if (owners.putIfAbsent(kind, name) == null) {
                        signals.add(new Signal(kind, name, u.pointer()));
                    }
                }
            }
        }

        if (firstHeading != null) {
            signals.add(0, new Signal("first heading", firstHeading, firstHeadingPointer));
        }
        String approver = null;
        for (String k : List.of("approved by", "reviewed by", "signed", "owner", "prepared by")) {
            if (owners.containsKey(k)) {
                approver = k + " " + owners.get(k);
                break;
            }
        }
        if (policyLike && owners.isEmpty()) {
            findings.add(new SummaryFinding(Severity.INFO, "No approver/owner marker found in the text.", "text"));
        }
        return new Result(signals, findings, approver, firstHeading, !owners.isEmpty());
    }

    private static String markerOf(String matched) {
        String m = matched.toLowerCase(Locale.ROOT);
        if (m.startsWith("approved by")) return "approved by";
        if (m.startsWith("reviewed by")) return "reviewed by";
        if (m.startsWith("prepared by")) return "prepared by";
        if (m.startsWith("owner")) return "owner";
        return "signed";
    }

    // ---- keyword overlap ---------------------------------------------------------------------

    /** Terms of the control name + description (>= 4 letters, no stopwords) that occur in {@code text}; at most 8. */
    public static List<String> keywordOverlap(String controlName, String controlDescription, String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String haystack = text.toLowerCase(Locale.ROOT);
        Set<String> terms = new LinkedHashSet<>();
        String source = ((controlName == null ? "" : controlName) + " "
                + (controlDescription == null ? "" : controlDescription)).toLowerCase(Locale.ROOT);
        for (String t : source.split("[^\\p{L}]+")) {
            if (t.length() >= 4 && !STOPWORDS.contains(t)) {
                terms.add(t);
            }
        }
        List<String> hits = new ArrayList<>();
        for (String t : terms) {
            if (hits.size() == MAX_TERMS) {
                break;
            }
            if (Pattern.compile("(?<!\\p{L})" + Pattern.quote(t)).matcher(haystack).find()) {
                hits.add(t);
            }
        }
        return hits;
    }

    /** The fact value shown for {@link #OVERLAP_LABEL}. */
    public static String overlapValue(List<String> hits) {
        return hits.isEmpty() ? "Mentions: none of the control's key terms" : "Mentions: " + String.join(", ", hits);
    }
}
