package com.pramaan.backend.insight.summary;

import static com.pramaan.backend.insight.summary.Summarizing.bytes;
import static com.pramaan.backend.insight.summary.Summarizing.fixture;
import static com.pramaan.backend.insight.summary.Summarizing.probe;
import static com.pramaan.backend.insight.summary.Summarizing.run;
import static org.assertj.core.api.Assertions.assertThat;

import com.pramaan.backend.insight.InsightDtos.SummaryFact;
import com.pramaan.backend.insight.InsightDtos.SummaryFinding;
import com.pramaan.backend.insight.summary.SummaryEnums.Severity;
import org.junit.jupiter.api.Test;

class CheckBundleSummarizerTest {

    private final CheckBundleSummarizer summarizer = new CheckBundleSummarizer();

    @Test
    void goAgentBundleIsSummarisedCheckByCheck() {
        Extraction e = run(summarizer, fixture("go-check-bundle.json"));

        assertThat(e.kind()).isEqualTo(ContentKind.CHECK_BUNDLE);
        assertThat(e.contentSentence()).isEqualTo(
                "1 of 4 checks passed, 1 warning, 1 failed, 1 not applicable. "
                        + "OS-LNX-02: observed yes, expected no — PermitRootLogin is enabled. "
                        + "OS-LNX-03: observed 90 days, expected 30 days — Password max age exceeds baseline.");
        assertThat(e.facts()).extracting(SummaryFact::label).containsExactly(
                "collector", "technology", "target", "collectedAt", "simulated",
                "facts.db_password", "facts.os.release", "facts.ssh.permitrootlogin",
                "check OS-LNX-01", "check OS-LNX-02", "check OS-LNX-03", "check OS-LNX-04");
        assertThat(e.facts()).contains(
                new SummaryFact("check OS-LNX-01", "PASS; Supported OS version; observed RHEL 9.3; expected RHEL 8+",
                        "/findings/0"),
                new SummaryFact("check OS-LNX-04", "NOT_APPLICABLE; FIPS mode", "/findings/3"),
                new SummaryFact("target", "web-01", "/target"));
        assertThat(e.findings()).containsExactly(
                new SummaryFinding(Severity.FAIL,
                        "OS-LNX-02: observed yes, expected no — PermitRootLogin is enabled.", "/findings/1"),
                new SummaryFinding(Severity.WARNING,
                        "OS-LNX-03: observed 90 days, expected 30 days — Password max age exceeds baseline.",
                        "/findings/2"));
    }

    @Test
    void aSecretNamedFactIsRedacted() {
        Extraction e = run(summarizer, fixture("go-check-bundle.json"));
        assertThat(e.facts()).contains(new SummaryFact("facts.db_password", "[REDACTED]", "/facts/db_password"));
        assertThat(e.facts()).noneMatch(f -> f.value().contains("s3cr3t"));
    }

    @Test
    void simulatedTechnologyBundleUsesTheChecksArray() {
        Extraction e = run(summarizer, fixture("sim-check-bundle.json"));

        assertThat(e.contentSentence()).isEqualTo("2 of 2 checks passed.");
        assertThat(e.findings()).isEmpty();
        assertThat(e.facts()).extracting(SummaryFact::pointer).contains("/checks/0", "/checks/1", "/status");
        assertThat(e.facts()).contains(new SummaryFact("check DB-PG-02", "PASS; Database Audit Logging; observed on; expected on",
                "/checks/1"));
    }

    @Test
    void erroredChecksCountAsFailures() {
        Extraction e = run(summarizer, bytes(
                "{\"findings\":[{\"checkId\":\"A-1\",\"status\":\"ERROR\",\"detail\":\"could not connect\"},"
                        + "{\"checkId\":\"A-2\",\"status\":\"PASS\"}]}"));
        assertThat(e.contentSentence()).isEqualTo("1 of 2 checks passed, 1 failed. A-1: could not connect.");
        assertThat(e.findings()).extracting(SummaryFinding::severity).containsExactly(Severity.FAIL);
    }

    @Test
    void doesNotClaimContentWithoutCheckRows() {
        assertThat(summarizer.supports(probe(bytes("{\"findings\":[{\"status\":\"PASS\"}]}"), "application/json", null)))
                .isFalse();
        assertThat(summarizer.supports(probe(bytes("{\"checks\":[]}"), "application/json", null))).isFalse();
        assertThat(summarizer.supports(probe(bytes("[1,2]"), "application/json", null))).isFalse();
        assertThat(summarizer.supports(probe(bytes("plain text"), "text/plain", null))).isFalse();
    }
}
