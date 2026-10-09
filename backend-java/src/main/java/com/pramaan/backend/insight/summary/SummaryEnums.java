package com.pramaan.backend.insight.summary;

/** Small closed vocabularies used by the evidence-summary response. */
public final class SummaryEnums {

    private SummaryEnums() {}

    /** Worst-of over the persisted check results; NOT_ASSESSED when there are none. */
    public enum Verdict { PASS, WARNING, FAIL, NOT_APPLICABLE, NOT_ASSESSED }

    public enum Severity { INFO, WARNING, FAIL }

    public enum Integrity { INTACT, MISMATCH, UNVERIFIABLE }

    public enum Freshness { CURRENT, STALE }

    public enum ControlSource { LEGACY, LIBRARY, UNKNOWN }
}
