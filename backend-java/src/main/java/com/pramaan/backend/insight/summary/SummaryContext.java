package com.pramaan.backend.insight.summary;

import java.time.Instant;

/**
 * What a summarizer is given: the probed bytes, record-level hints, the sink to report into, the
 * (injected-clock) current instant, and the control's name/description for the informational
 * keyword overlap. {@code now} is the only clock reading a summarizer may use.
 */
public record SummaryContext(ContentProbe probe, String sourceSystem, String applicationSlug, FactSink sink,
                             Instant now, String controlName, String controlDescription) {

    /** Context with no control text and a fixed epoch clock — for summarizers that need neither. */
    public SummaryContext(ContentProbe probe, String sourceSystem, String applicationSlug, FactSink sink) {
        this(probe, sourceSystem, applicationSlug, sink, Instant.EPOCH, null, null);
    }
}
