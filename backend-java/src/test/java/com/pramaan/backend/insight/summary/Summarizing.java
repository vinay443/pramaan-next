package com.pramaan.backend.insight.summary;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/** Test helper: probe bytes and run one summarizer the way the engine does. */
final class Summarizing {

    static final ObjectMapper MAPPER = new ObjectMapper();

    private Summarizing() {}

    static byte[] fixture(String name) {
        try (InputStream in = Summarizing.class.getResourceAsStream("/summary/" + name)) {
            assertThat(in).as("fixture " + name).isNotNull();
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    static ContentProbe probe(byte[] bytes, String contentType, String fileName) {
        return ContentProbe.of(bytes, false, bytes.length, contentType, fileName, MAPPER);
    }

    /** Asserts the summarizer claims the content, then summarizes it. */
    static Extraction run(ContentSummarizer summarizer, byte[] bytes, String contentType, String fileName) {
        ContentProbe p = probe(bytes, contentType, fileName);
        assertThat(summarizer.supports(p)).as(summarizer.getClass().getSimpleName() + " supports").isTrue();
        return summarizer.summarize(new SummaryContext(p, "SRC", "app", new FactSink()));
    }

    /** Like run(), with the injected clock and the control text a document summary can use. */
    static Extraction runDoc(ContentSummarizer summarizer, byte[] bytes, String fileName, String now, String controlName,
                             String controlDescription) {
        ContentProbe p = probe(bytes, "application/octet-stream", fileName);
        assertThat(summarizer.supports(p)).as(summarizer.getClass().getSimpleName() + " supports " + p.format()).isTrue();
        return summarizer.summarize(new SummaryContext(p, "SRC", "app", new FactSink(), java.time.Instant.parse(now),
                controlName, controlDescription));
    }

    static Extraction runDoc(ContentSummarizer summarizer, byte[] bytes) {
        return runDoc(summarizer, bytes, null, "2026-09-30T00:00:00Z", null, null);
    }

    static Extraction run(ContentSummarizer summarizer, byte[] bytes) {
        return run(summarizer, bytes, "application/json", null);
    }
}
