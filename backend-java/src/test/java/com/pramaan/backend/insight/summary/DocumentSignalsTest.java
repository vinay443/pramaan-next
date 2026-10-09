package com.pramaan.backend.insight.summary;

import static org.assertj.core.api.Assertions.assertThat;

import com.pramaan.backend.insight.InsightDtos.SummaryFinding;
import com.pramaan.backend.insight.summary.DocumentSignals.DateHit;
import com.pramaan.backend.insight.summary.DocumentSignals.Result;
import com.pramaan.backend.insight.summary.DocumentSignals.Signal;
import com.pramaan.backend.insight.summary.DocumentSignals.Unit;
import com.pramaan.backend.insight.summary.SummaryEnums.Severity;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class DocumentSignalsTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 30); // the fixed "Clock"

    private static Result analyse(String text, boolean policyLike) {
        return DocumentSignals.analyse(List.of(new Unit("page 1", text)), TODAY, policyLike);
    }

    private static List<String> values(Result r, String label) {
        return r.signals().stream().filter(s -> s.label().equals(label)).map(Signal::value).toList();
    }

    @Test
    void datesInFourFormatsAreNormalisedToIsoAndTheOriginalIsKeptInThePointer() {
        Result r = analyse("Issued 2026-09-30, revised 30/09/2026 and 5 Oct 2026; final September 7, 2027.", false);

        assertThat(values(r, "date")).containsExactly("2026-09-30", "2026-10-05", "2027-09-07");
        // 30/09/2026 duplicates 2026-09-30 and is dropped as a repeat
        assertThat(r.signals()).contains(
                new Signal("date", "2026-09-30", "page 1 (as written: 2026-09-30)"),
                new Signal("date", "2026-10-05", "page 1 (as written: 5 Oct 2026)"),
                new Signal("date", "2027-09-07", "page 1 (as written: September 7, 2027)"));
    }

    @Test
    void dayFirstSlashDatesAndImpossibleDates() {
        assertThat(DocumentSignals.findDates("due 30/09/2026")).extracting(DateHit::iso).containsExactly("2026-09-30");
        assertThat(DocumentSignals.findDates("due 03/04/2026")).extracting(DateHit::iso).containsExactly("2026-04-03");
        assertThat(DocumentSignals.findDates("bad 31/02/2026 and 2026-13-40")).isEmpty();
    }

    @Test
    void atMostFiveDatesAreReported() {
        String text = "2026-01-01 2026-01-02 2026-01-03 2026-01-04 2026-01-05 2026-01-06 2026-01-07";
        assertThat(values(analyse(text, false), "date")).hasSize(5);
    }

    @Test
    void versionMarkers() {
        Result r = analyse("Access Policy\nVersion 2.1\nprevious v1.3 and Rev. B\nThe review process continues.", false);
        assertThat(values(r, "version")).containsExactly("2.1", "1.3", "Rev B");
    }

    @Test
    void ownershipAndApprovalMarkersCaptureAName() {
        Result r = analyse("Approved by: Priya Nair\nOwner - IT Security Team\nPrepared by Arun K. Rao\n"
                + "Signed: ______\nReviewed by meera", false);

        assertThat(values(r, "approved by")).containsExactly("Priya Nair");
        assertThat(values(r, "owner")).containsExactly("IT Security Team");
        assertThat(values(r, "prepared by")).containsExactly("Arun K. Rao");
        assertThat(values(r, "signed")).isEmpty();
        assertThat(values(r, "reviewed by")).isEmpty(); // lower-case word is not a name
        assertThat(r.approver()).isEqualTo("approved by Priya Nair");
        assertThat(r.ownerMarkerFound()).isTrue();
    }

    @Test
    void aNameIsCappedAtSixtyCharacters() {
        String longName = "Alexander Bartholomew Christopherson Davenport Eberhardt Fitzgerald";
        Result r = analyse("Owner: " + longName, false);
        assertThat(values(r, "owner").get(0).length()).isLessThanOrEqualTo(60);
    }

    @Test
    void aReviewDateInThePastRaisesAWarningRelativeToTheClock() {
        Result past = analyse("Next review: 01/03/2026", false);
        assertThat(values(past, "next review")).containsExactly("2026-03-01");
        assertThat(past.findings()).containsExactly(new SummaryFinding(Severity.WARNING,
                "Document states it was due for review on 2026-03-01.", "page 1"));

        Result validUntil = analyse("Valid until 30 Jun 2026", false);
        assertThat(validUntil.findings()).extracting(SummaryFinding::message)
                .containsExactly("Document states it was due for review on 2026-06-30.");

        assertThat(analyse("Next review: 2027-03-01", false).findings()).isEmpty();
        assertThat(analyse("Review date: 2026-09-30", false).findings()).isEmpty(); // today is not past
        assertThat(analyse("Effective date: 1 January 2020", false).findings()).isEmpty();
        assertThat(values(analyse("Effective date: 1 January 2020", false), "effective date")).containsExactly("2020-01-01");
    }

    @Test
    void aPolicyWithoutAnOwnerGetsAnInfoNeverAFail() {
        Result policy = analyse("Information Security Policy\nAll access must be reviewed.", true);
        assertThat(policy.findings()).containsExactly(new SummaryFinding(Severity.INFO,
                "No approver/owner marker found in the text.", "text"));

        assertThat(analyse("Meeting minutes\nNothing to see.", false).findings()).isEmpty();
        assertThat(analyse("Policy\nOwner: Meera Iyer", true).findings()).isEmpty();
    }

    @Test
    void firstHeadingIsTheFirstShortNonEmptyLine() {
        Result r = DocumentSignals.analyse(List.of(new Unit("page 1", "\n  Access Control Policy  \nbody"),
                new Unit("page 2", "Second")), TODAY, false);
        assertThat(r.signals().get(0)).isEqualTo(new Signal("first heading", "Access Control Policy", "page 1"));

        String tooLong = "x".repeat(150);
        Result long1 = analyse(tooLong + "\nShort title", false);
        assertThat(long1.firstHeading()).isEqualTo("Short title");
    }

    @Test
    void keywordOverlapListsControlTermsFoundInTheText() {
        List<String> hits = DocumentSignals.keywordOverlap("Encryption in Transit",
                "Data must be encrypted in transit using approved algorithms.",
                "All traffic uses Encryption in transit with approved TLS settings.");

        assertThat(hits).containsExactly("encryption", "transit", "approved");
        assertThat(DocumentSignals.overlapValue(hits)).isEqualTo("Mentions: encryption, transit, approved");
        assertThat(DocumentSignals.overlapValue(List.of())).isEqualTo("Mentions: none of the control's key terms");
        assertThat(DocumentSignals.keywordOverlap("Backup", "restore", "nothing here")).isEmpty();
    }

    @Test
    void keywordOverlapDropsShortWordsStopwordsAndCapsAtEightTerms() {
        String desc = "alpha bravo charlie delta echo foxtrot golf hotel india juliet abc that with this";
        List<String> hits = DocumentSignals.keywordOverlap("", desc, desc);
        assertThat(hits).hasSize(8).doesNotContain("abc", "that", "with", "this", "india", "juliet");
    }
}
