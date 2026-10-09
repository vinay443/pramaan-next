package com.pramaan.backend.insight.summary;

/**
 * One way of turning stored evidence bytes into facts. The engine asks each summarizer, in
 * {@link #order()}, whether it {@link #supports} the probe and lets the first one that does
 * summarize it. New content types (PDF, DOCX, XLSX, images) are added as further
 * implementations ordered before {@link BinaryMetadataSummarizer}; the engine is not touched.
 */
public interface ContentSummarizer {

    /** Lower runs first. The catch-all {@link BinaryMetadataSummarizer} is last. */
    int order();

    boolean supports(ContentProbe probe);

    /** Extract facts/findings/limits through {@code context.sink()} and return {@code sink.build(...)}. */
    Extraction summarize(SummaryContext context);
}
