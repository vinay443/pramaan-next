package com.pramaan.backend.insight.summary;

import com.pramaan.backend.insight.InsightDtos.SummaryFact;
import com.pramaan.backend.insight.InsightDtos.SummaryFinding;
import com.pramaan.backend.insight.summary.SummaryEnums.Severity;
import java.util.ArrayList;
import java.util.List;

/**
 * Collects facts, findings and limits for one summary and enforces the hard caps: a fact ceiling,
 * 200-character values, and redaction of anything that names or looks like a secret.
 */
public final class FactSink {

    /** Content facts kept; record-level facts (at most 6) bring the response to the 60-fact ceiling. */
    public static final int CONTENT_FACT_CAP = 50;
    public static final int FINDING_CAP = 30;

    private final int maxFacts;
    private final List<SummaryFact> facts = new ArrayList<>();
    private final List<SummaryFinding> findings = new ArrayList<>();
    private final List<String> limits = new ArrayList<>();
    private int offered;
    private int droppedFindings;

    public FactSink() {
        this(CONTENT_FACT_CAP);
    }

    public FactSink(int maxFacts) {
        this.maxFacts = maxFacts;
    }

    /** True once no more facts will be kept — lets walkers stop early. */
    public boolean full() {
        return facts.size() >= maxFacts;
    }

    /**
     * Add a fact. A label that names a secret hides the value; other values are scrubbed and clipped.
     * Blank values are skipped so no fact ever prints "null".
     */
    public void fact(String label, String rawValue, String pointer) {
        if (SummaryTemplates.blank(rawValue) || SummaryTemplates.blank(label)) {
            return;
        }
        offered++;
        if (facts.size() >= maxFacts) {
            return;
        }
        facts.add(new SummaryFact(label, value(label, rawValue), pointer));
    }

    /** The value as it may be shown for a key/label: redacted if the key is sensitive, else scrubbed and clipped. */
    public static String value(String key, String raw) {
        if (raw == null) {
            return null;
        }
        if (Redactor.isSensitiveKey(key) || Redactor.looksLikePrivateKey(raw)) {
            return Redactor.REDACTED;
        }
        return SummaryTemplates.clip(Redactor.redactText(raw));
    }

    /** Scrubbed, clipped text for embedding in a sentence (no key involved). */
    public static String text(String raw) {
        return raw == null ? null : SummaryTemplates.clip(Redactor.redactText(raw));
    }

    public void finding(Severity severity, String message, String pointer) {
        if (SummaryTemplates.blank(message)) {
            return;
        }
        if (findings.size() >= FINDING_CAP) {
            droppedFindings++;
            return;
        }
        findings.add(new SummaryFinding(severity, Redactor.redactText(message), pointer));
    }

    public void limit(String message) {
        if (!SummaryTemplates.blank(message) && !limits.contains(message)) {
            limits.add(message);
        }
    }

    public Extraction build(ContentKind kind, String contentSentence) {
        List<String> outLimits = new ArrayList<>(limits);
        if (offered > facts.size()) {
            outLimits.add("Showing " + facts.size() + " of " + offered
                    + " facts; the rest were omitted to keep the summary short.");
        }
        if (droppedFindings > 0) {
            outLimits.add(droppedFindings + " further findings were omitted.");
        }
        return new Extraction(kind, List.copyOf(facts), List.copyOf(findings), List.copyOf(outLimits),
                contentSentence == null ? "" : contentSentence);
    }
}
