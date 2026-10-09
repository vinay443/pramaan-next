package com.pramaan.backend.insight.summary;

import static org.assertj.core.api.Assertions.assertThat;

import com.pramaan.backend.insight.InsightDtos.SummaryFact;
import com.pramaan.backend.insight.InsightDtos.SummaryFinding;
import com.pramaan.backend.insight.summary.SummaryEnums.Freshness;
import com.pramaan.backend.insight.summary.SummaryEnums.Integrity;
import com.pramaan.backend.insight.summary.SummaryEnums.Severity;
import com.pramaan.backend.insight.summary.SummaryEnums.Verdict;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class SummaryTemplatesTest {

    @Test
    void headlineIsControlNameIdFrameworkVerdictAndContentSentence() {
        assertThat(SummaryTemplates.headline("Secure System Configuration", "PCI-C2", "PCI_DSS", Verdict.PASS,
                "3 of 3 checks passed."))
                .isEqualTo("Secure System Configuration (PCI-C2, PCI DSS) — meets the automated checks. "
                        + "3 of 3 checks passed.");
    }

    @Test
    void headlineWithoutContentSentenceHasNoTrailingSpace() {
        assertThat(SummaryTemplates.headline("Audit Scope Coverage", "IA-C1", "INTERNAL_AUDIT", Verdict.FAIL, ""))
                .isEqualTo("Audit Scope Coverage (IA-C1, Internal Audit) — fails an automated check.");
    }

    @Test
    void everyVerdictHasItsFixedWording() {
        assertThat(SummaryTemplates.verdictWord(Verdict.PASS)).isEqualTo("meets the automated checks");
        assertThat(SummaryTemplates.verdictWord(Verdict.WARNING)).isEqualTo("passes with warnings");
        assertThat(SummaryTemplates.verdictWord(Verdict.FAIL)).isEqualTo("fails an automated check");
        assertThat(SummaryTemplates.verdictWord(Verdict.NOT_APPLICABLE)).isEqualTo("not applicable to this target");
        assertThat(SummaryTemplates.verdictWord(Verdict.NOT_ASSESSED))
                .isEqualTo("collected, not assessed (no automated rule)");
    }

    @Test
    void frameworkLabels() {
        assertThat(SummaryTemplates.frameworkLabel("PCI_DSS")).isEqualTo("PCI DSS");
        assertThat(SummaryTemplates.frameworkLabel("os_baselining")).isEqualTo("OS Baselining");
        assertThat(SummaryTemplates.frameworkLabel("NGINX_BASELINING")).isEqualTo("NGINX Baselining");
        assertThat(SummaryTemplates.frameworkLabel("INTERNAL_AUDIT")).isEqualTo("Internal Audit");
        assertThat(SummaryTemplates.frameworkLabel("C-SITE")).isEqualTo("C-SITE");
        assertThat(SummaryTemplates.frameworkLabel("ISO_27001")).isEqualTo("ISO 27001");
        assertThat(SummaryTemplates.frameworkLabel(null)).isEqualTo("unspecified framework");
    }

    @Test
    void bundleSentencePluralisesAndOmitsEmptyParts() {
        assertThat(SummaryTemplates.bundleSentence(3, 3, 0, 0, 0)).isEqualTo("3 of 3 checks passed.");
        assertThat(SummaryTemplates.bundleSentence(1, 1, 0, 0, 0)).isEqualTo("1 of 1 check passed.");
        assertThat(SummaryTemplates.bundleSentence(2, 4, 1, 1, 0)).isEqualTo("2 of 4 checks passed, 1 warning, 1 failed.");
        assertThat(SummaryTemplates.bundleSentence(1, 4, 2, 0, 1))
                .isEqualTo("1 of 4 checks passed, 2 warnings, 1 not applicable.");
    }

    @Test
    void checkProblemLine() {
        assertThat(SummaryTemplates.checkProblem("MW-03", "absent", "present", "HSTS header missing"))
                .isEqualTo("MW-03: observed absent, expected present — HSTS header missing.");
        assertThat(SummaryTemplates.checkProblem("MW-03", "absent", "present", "HSTS header missing."))
                .isEqualTo("MW-03: observed absent, expected present — HSTS header missing.");
        assertThat(SummaryTemplates.checkProblem("MW-03", "absent", "present", null))
                .isEqualTo("MW-03: observed absent, expected present.");
        assertThat(SummaryTemplates.checkProblem("MW-03", null, null, "HSTS header missing"))
                .isEqualTo("MW-03: HSTS header missing.");
        assertThat(SummaryTemplates.checkProblem("MW-03", " ", null, null)).isEqualTo("MW-03: no detail recorded.");
    }

    @Test
    void bundleWithProblemsCapsTheListedProblems() {
        List<String> problems = List.of("A: x.", "B: x.", "C: x.", "D: x.", "E: x.", "F: x.", "G: x.");
        assertThat(SummaryTemplates.bundleWithProblems("0 of 7 checks passed, 7 failed.", problems))
                .isEqualTo("0 of 7 checks passed, 7 failed. A: x. B: x. C: x. D: x. E: x. …and 2 more.");
        assertThat(SummaryTemplates.bundleWithProblems("3 of 3 checks passed.", List.of()))
                .isEqualTo("3 of 3 checks passed.");
    }

    @Test
    void integrationSentence() {
        assertThat(SummaryTemplates.integrationSentence("MOCK_JIRA", "change_ticket", "CHG-1", "APPROVED", "Priya",
                "2026-09-01")).isEqualTo("MOCK_JIRA change ticket CHG-1 is APPROVED; approved by Priya on 2026-09-01.");
        assertThat(SummaryTemplates.integrationSentence("MOCK_JIRA", "change_ticket", "CHG-1", "COLLECTED", null, null))
                .isEqualTo("MOCK_JIRA change ticket CHG-1 is COLLECTED.");
        assertThat(SummaryTemplates.integrationSentence("MOCK_JIRA", "change_ticket", "CHG-1", "APPROVED", "Priya", null))
                .isEqualTo("MOCK_JIRA change ticket CHG-1 is APPROVED; approved by Priya.");
        assertThat(SummaryTemplates.integrationSentence(null, "incident_record", null, null, null, null))
                .isEqualTo("incident record has no status recorded.");
        assertThat(SummaryTemplates.integrationSentence(null, null, null, "OPEN", null, null))
                .isEqualTo("Integration object is OPEN.");
    }

    @Test
    void querySentences() {
        assertThat(SummaryTemplates.querySentence("Password Policy", 3, 0))
                .isEqualTo("Query Password Policy returned 3 rows.");
        assertThat(SummaryTemplates.querySentence("Password Policy", 1, 1))
                .isEqualTo("Query Password Policy returned 1 row; 1 flagged.");
        assertThat(SummaryTemplates.querySentence(null, 0, 0)).isEqualTo("Query returned 0 rows.");
        assertThat(SummaryTemplates.queryOutputSentence("SELinux Status", 1, 0))
                .isEqualTo("Query SELinux Status returned 1 line of output.");
        assertThat(SummaryTemplates.queryOutputSentence("SELinux Status", 4, 2))
                .isEqualTo("Query SELinux Status returned 4 lines of output; 2 flagged.");
        assertThat(SummaryTemplates.queryEmptySentence("SELinux Status"))
                .isEqualTo("Query SELinux Status stored no result data.");
    }

    @Test
    void documentSentence() {
        assertThat(SummaryTemplates.documentSentence("Text", 1536, "12 lines", "Meeting notes", "2026-09-01"))
                .isEqualTo("Text of 1.5 KiB, 12 lines; title \"Meeting notes\"; dated 2026-09-01.");
        assertThat(SummaryTemplates.documentSentence("Text", 1, null, null, null)).isEqualTo("Text of 1 byte.");
        assertThat(SummaryTemplates.documentSentence("PDF document", 2 * 1024 * 1024, null, null, "2026-01-05"))
                .isEqualTo("PDF document of 2.0 MiB; dated 2026-01-05.");
    }

    @Test
    void fileSentence() {
        assertThat(SummaryTemplates.fileSentence("PDF", 2048, "3 pages", "Access Policy", "2026-09-01", "approved by Priya"))
                .isEqualTo("PDF of 2.0 KiB, 3 pages; title \"Access Policy\"; last modified 2026-09-01; approved by Priya.");
        assertThat(SummaryTemplates.fileSentence("Spreadsheet", 10, "2 sheets", null, null, null))
                .isEqualTo("Spreadsheet of 10 bytes, 2 sheets.");
        assertThat(SummaryTemplates.fileSentence("DOCX", 1, null, " ", "", null)).isEqualTo("DOCX of 1 byte.");
    }

    @Test
    void humanSizeAndClip() {
        assertThat(SummaryTemplates.humanSize(0)).isEqualTo("0 bytes");
        assertThat(SummaryTemplates.humanSize(1023)).isEqualTo("1023 bytes");
        assertThat(SummaryTemplates.humanSize(1024)).isEqualTo("1.0 KiB");
        assertThat(SummaryTemplates.humanSize(1024 * 1024)).isEqualTo("1.0 MiB");

        assertThat(SummaryTemplates.clip("x".repeat(200))).hasSize(200).doesNotContain("…");
        String clipped = SummaryTemplates.clip("y".repeat(500));
        assertThat(clipped).hasSize(200).endsWith("…");
    }

    @Test
    void legacySummaryIsHeadlinePlusUpToThreeFindingsAndTheCollectionTail() {
        List<SummaryFinding> findings = List.of(
                new SummaryFinding(Severity.FAIL, "A failed", "/a"),
                new SummaryFinding(Severity.WARNING, "B warned.", "/b"),
                new SummaryFinding(Severity.INFO, "C noted", "/c"),
                new SummaryFinding(Severity.INFO, "D noted", "/d"));
        assertThat(SummaryTemplates.summary("Head.", findings, Instant.parse("2026-09-01T10:00:00Z"), "MOCK_JIRA",
                "payments", 2, Integrity.INTACT, Freshness.CURRENT))
                .isEqualTo("Head. A failed. B warned. C noted. Collected 2026-09-01 from MOCK_JIRA for payments (v2); "
                        + "integrity intact; current.");
    }

    @Test
    void legacySummarySkipsFindingsAlreadyInTheHeadlineAndWordsMismatchAndStale() {
        List<SummaryFinding> findings = List.of(new SummaryFinding(Severity.FAIL, "MW-03: observed absent.", "/x"));
        assertThat(SummaryTemplates.summary("Head. MW-03: observed absent.", findings, null, null, null, 1,
                Integrity.MISMATCH, Freshness.STALE))
                .isEqualTo("Head. MW-03: observed absent. Collected on an unknown date from an unknown source "
                        + "for an unknown application (v1); integrity NOT intact; stale.");
        assertThat(SummaryTemplates.integrityWord(Integrity.UNVERIFIABLE)).isEqualTo("unverifiable");
    }

    @Test
    void groundedLine() {
        assertThat(SummaryTemplates.groundedLine(new SummaryFact("check MW-03", "FAIL; HSTS", "/findings/2")))
                .isEqualTo("check MW-03: FAIL; HSTS [/findings/2]");
    }

    @Test
    void noTemplateEverPrintsNullOrEmptyBrackets() {
        List<String> all = List.of(
                SummaryTemplates.integrationSentence(null, null, null, null, null, null),
                SummaryTemplates.checkProblem(null, null, null, null),
                SummaryTemplates.querySentence(null, 0, 0),
                SummaryTemplates.queryOutputSentence(null, 0, 0),
                SummaryTemplates.queryEmptySentence(null),
                SummaryTemplates.documentSentence("Text", 0, null, null, null),
                SummaryTemplates.headline("n", "c", null, Verdict.NOT_ASSESSED, null),
                SummaryTemplates.summary("H.", List.of(), null, null, null, 0, Integrity.UNVERIFIABLE, Freshness.STALE));
        assertThat(all).noneMatch(s -> s.contains("null") || s.contains("undefined") || s.contains("[]")
                || s.contains("()") || s.contains("{}"));
    }
}
