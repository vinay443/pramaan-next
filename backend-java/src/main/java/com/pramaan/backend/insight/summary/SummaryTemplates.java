package com.pramaan.backend.insight.summary;

import com.pramaan.backend.insight.InsightDtos.SummaryFact;
import com.pramaan.backend.insight.InsightDtos.SummaryFinding;
import com.pramaan.backend.insight.summary.SummaryEnums.Freshness;
import com.pramaan.backend.insight.summary.SummaryEnums.Integrity;
import com.pramaan.backend.insight.summary.SummaryEnums.Verdict;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Every sentence the deterministic evidence summary can print. Plain string assembly — no
 * template engine, no model. Nothing here ever prints "null" or an empty bracket: a missing
 * piece is simply left out of the sentence.
 */
public final class SummaryTemplates {

    public static final int MAX_VALUE_CHARS = 200;
    private static final int MAX_PROBLEM_LINES = 5;
    private static final int MAX_SUMMARY_FINDINGS = 3;
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);

    /** Framework key -> the sheet name used in docs/ECS_Control_Library.xlsx. Single place for the mapping. */
    private static final Map<String, String> FRAMEWORK_LABELS = Map.of(
            "PCI_DSS", "PCI DSS",
            "OS_BASELINING", "OS Baselining",
            "DB_BASELINING", "DB Baselining",
            "NGINX_BASELINING", "NGINX Baselining",
            "INTERNAL_AUDIT", "Internal Audit");

    private SummaryTemplates() {}

    // ---- primitives -------------------------------------------------------

    public static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    /** At most {@value #MAX_VALUE_CHARS} characters; an over-long value ends with an ellipsis. */
    public static String clip(String value) {
        if (value == null) {
            return null;
        }
        String v = value.strip();
        if (v.length() <= MAX_VALUE_CHARS) {
            return v;
        }
        return v.substring(0, MAX_VALUE_CHARS - 1) + "…";
    }

    public static String plural(long n, String one, String many) {
        return n + " " + (n == 1 ? one : many);
    }

    public static String humanSize(long bytes) {
        if (bytes < 1024) {
            return plural(bytes, "byte", "bytes");
        }
        if (bytes < 1024L * 1024) {
            return String.format(Locale.ROOT, "%.1f KiB", bytes / 1024.0);
        }
        return String.format(Locale.ROOT, "%.1f MiB", bytes / (1024.0 * 1024));
    }

    public static String frameworkLabel(String frameworkKey) {
        if (blank(frameworkKey)) {
            return "unspecified framework";
        }
        String k = frameworkKey.trim();
        String label = FRAMEWORK_LABELS.get(k.toUpperCase(Locale.ROOT));
        return label != null ? label : k.replace('_', ' ');
    }

    public static String verdictWord(Verdict v) {
        return switch (v) {
            case PASS -> "meets the automated checks";
            case WARNING -> "passes with warnings";
            case FAIL -> "fails an automated check";
            case NOT_APPLICABLE -> "not applicable to this target";
            case NOT_ASSESSED -> "collected, not assessed (no automated rule)";
        };
    }

    public static String integrityWord(Integrity i) {
        return switch (i) {
            case INTACT -> "intact";
            case MISMATCH -> "NOT intact";
            case UNVERIFIABLE -> "unverifiable";
        };
    }

    public static String freshnessWord(Freshness f) {
        return f == Freshness.CURRENT ? "current" : "stale";
    }

    public static String day(Instant when) {
        return when == null ? null : DAY.format(when);
    }

    // ---- headline ---------------------------------------------------------

    public static String headline(String controlName, String controlId, String frameworkKey, Verdict verdict,
                                  String contentSentence) {
        String head = controlName + " (" + controlId + ", " + frameworkLabel(frameworkKey) + ") — "
                + verdictWord(verdict) + ".";
        return blank(contentSentence) ? head : head + " " + contentSentence.strip();
    }

    /** Appended to the headline when the stored bytes no longer hash to the recorded SHA-256. */
    public static String integrityMismatchSentence() {
        return "The stored content does not match the recorded SHA-256.";
    }

    public static String mismatchFinding() {
        return "The stored content does not match the recorded SHA-256, so it may have been altered after collection.";
    }

    public static String noAutomatedRule() {
        return "No automated rule applies to this evidence.";
    }

    public static String unavailableSentence() {
        return "The stored content is unavailable, so only record-level facts are shown.";
    }

    // ---- content sentences --------------------------------------------------

    /** "{pass} of {total} checks passed{, {warn} warning(s)}{, {fail} failed}{, {na} not applicable}." */
    public static String bundleSentence(int pass, int total, int warn, int fail, int notApplicable) {
        StringBuilder sb = new StringBuilder();
        sb.append(pass).append(" of ").append(plural(total, "check", "checks")).append(" passed");
        if (warn > 0) {
            sb.append(", ").append(plural(warn, "warning", "warnings"));
        }
        if (fail > 0) {
            sb.append(", ").append(fail).append(" failed");
        }
        if (notApplicable > 0) {
            sb.append(", ").append(notApplicable).append(" not applicable");
        }
        return sb.append('.').toString();
    }

    /** "{checkId}: observed {observed}, expected {expected} — {detail}." with missing parts left out. */
    public static String checkProblem(String checkId, String observed, String expected, String detail) {
        List<String> parts = new ArrayList<>();
        if (!blank(observed)) {
            parts.add("observed " + observed.strip());
        }
        if (!blank(expected)) {
            parts.add("expected " + expected.strip());
        }
        String id = blank(checkId) ? "check" : checkId.strip();
        String d = blank(detail) ? null : stripTrailingPeriod(detail.strip());
        if (parts.isEmpty() && d == null) {
            return id + ": no detail recorded.";
        }
        StringBuilder sb = new StringBuilder(id).append(':');
        if (!parts.isEmpty()) {
            sb.append(' ').append(String.join(", ", parts));
        }
        if (d != null) {
            sb.append(parts.isEmpty() ? " " : " — ").append(d);
        }
        return sb.append('.').toString();
    }

    /** Bundle sentence followed by up to five problem lines. */
    public static String bundleWithProblems(String bundleSentence, List<String> problemLines) {
        StringBuilder sb = new StringBuilder(bundleSentence);
        int shown = Math.min(problemLines.size(), MAX_PROBLEM_LINES);
        for (int i = 0; i < shown; i++) {
            sb.append(' ').append(problemLines.get(i));
        }
        int more = problemLines.size() - shown;
        if (more > 0) {
            sb.append(" …and ").append(more).append(" more.");
        }
        return sb.toString();
    }

    /** "{System} {objectType} {id} is {status}{; approved by {who} on {date}}." */
    public static String integrationSentence(String system, String objectType, String id, String status,
                                             String approver, String approvedOn) {
        List<String> name = new ArrayList<>();
        if (!blank(system)) name.add(system.strip());
        if (!blank(objectType)) name.add(objectType.strip().replace('_', ' '));
        if (!blank(id)) name.add(id.strip());
        String subject = name.isEmpty() ? "Integration object" : String.join(" ", name);
        StringBuilder sb = new StringBuilder(subject);
        sb.append(blank(status) ? " has no status recorded" : " is " + status.strip());
        if (!blank(approver)) {
            sb.append("; approved by ").append(approver.strip());
            if (!blank(approvedOn)) {
                sb.append(" on ").append(approvedOn.strip());
            }
        }
        return sb.append('.').toString();
    }

    /** "Query {name} returned {n} row(s){; {k} flagged}." */
    public static String querySentence(String name, int rows, int flagged) {
        return queryLead(name) + " returned " + plural(rows, "row", "rows") + flaggedTail(flagged);
    }

    /** Query whose stored result is free-text command output rather than rows. */
    public static String queryOutputSentence(String name, int lines, int flagged) {
        return queryLead(name) + " returned " + plural(lines, "line", "lines") + " of output" + flaggedTail(flagged);
    }

    public static String queryEmptySentence(String name) {
        return queryLead(name) + " stored no result data.";
    }

    private static String queryLead(String name) {
        return blank(name) ? "Query" : "Query " + name.strip();
    }

    private static String flaggedTail(int flagged) {
        return (flagged > 0 ? "; " + flagged + " flagged" : "") + ".";
    }

    /** "{kind} of {size}{, {detail}}{; title "{title}"}{; dated {date}}." */
    public static String documentSentence(String kind, long sizeBytes, String detail, String title, String date) {
        StringBuilder sb = new StringBuilder(kind).append(" of ").append(humanSize(sizeBytes));
        if (!blank(detail)) {
            sb.append(", ").append(detail.strip());
        }
        if (!blank(title)) {
            sb.append("; title \"").append(title.strip()).append('"');
        }
        if (!blank(date)) {
            sb.append("; dated ").append(date.strip());
        }
        return sb.append('.').toString();
    }

    /**
     * "{DOCX|PDF|Spreadsheet} of {size}{, {pages|sheets}}{; title "{title}"}{; last modified {date}}{; {approver marker}}."
     * for file evidence. A missing piece is left out; nothing here says the file proves a control.
     */
    public static String fileSentence(String kind, long sizeBytes, String detail, String title, String lastModified,
                                      String approverMarker) {
        StringBuilder sb = new StringBuilder(kind).append(" of ").append(humanSize(sizeBytes));
        if (!blank(detail)) {
            sb.append(", ").append(detail.strip());
        }
        if (!blank(title)) {
            sb.append("; title \"").append(title.strip()).append('"');
        }
        if (!blank(lastModified)) {
            sb.append("; last modified ").append(lastModified.strip());
        }
        if (!blank(approverMarker)) {
            sb.append("; ").append(approverMarker.strip());
        }
        return sb.append('.').toString();
    }

    // ---- legacy fields ----------------------------------------------------------

    /**
     * headline + up to 3 finding sentences + the collection tail. Findings whose text the headline
     * already contains are skipped, most severe first.
     */
    public static String summary(String headline, List<SummaryFinding> findings, Instant collectedAt,
                                 String sourceSystem, String application, int version, Integrity integrity,
                                 Freshness freshness) {
        StringBuilder sb = new StringBuilder(headline);
        int used = 0;
        for (SummaryFinding f : findings) {
            if (used == MAX_SUMMARY_FINDINGS) {
                break;
            }
            String sentence = sentence(f.message());
            if (blank(sentence) || headline.contains(sentence)) {
                continue;
            }
            sb.append(' ').append(sentence);
            used++;
        }
        sb.append(" Collected ").append(collectedAt == null ? "on an unknown date" : day(collectedAt))
                .append(" from ").append(blank(sourceSystem) ? "an unknown source" : sourceSystem)
                .append(" for ").append(blank(application) ? "an unknown application" : application)
                .append(" (v").append(version).append("); integrity ").append(integrityWord(integrity))
                .append("; ").append(freshnessWord(freshness)).append('.');
        return sb.toString();
    }

    /** "{label}: {value} [{pointer}]" */
    public static String groundedLine(SummaryFact fact) {
        return fact.label() + ": " + fact.value() + " [" + fact.pointer() + "]";
    }

    // ---- helpers ------------------------------------------------------------------

    private static String sentence(String message) {
        if (blank(message)) {
            return null;
        }
        String m = message.strip();
        char last = m.charAt(m.length() - 1);
        return last == '.' || last == '!' || last == '?' ? m : m + ".";
    }

    private static String stripTrailingPeriod(String s) {
        return s.endsWith(".") ? s.substring(0, s.length() - 1) : s;
    }
}
