package com.pramaan.backend.insight.summary;

/** What the stored bytes of an evidence version turned out to be (detected from the bytes, not the label). */
public enum ContentKind {
    CHECK_BUNDLE,
    INTEGRATION_OBJECT,
    QUERY_RESULT,
    STRUCTURED_DATA,
    TEXT,
    DOCUMENT,
    IMAGE,
    BINARY,
    UNAVAILABLE
}
