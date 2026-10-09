package com.pramaan.backend.insight.summary;

import com.pramaan.backend.insight.summary.SummaryEnums.Severity;
import org.springframework.stereotype.Component;

/**
 * Catch-all for anything no other summarizer claims (archives, legacy Office files, unknown binary):
 * reports the detected type and size and says plainly that the content cannot be read here. Also the
 * landing place when a specialised extractor (PDF, DOCX, XLSX, ...) cannot read a file.
 */
@Component
public class BinaryMetadataSummarizer implements ContentSummarizer {

    @Override
    public int order() {
        return 1000;
    }

    @Override
    public boolean supports(ContentProbe probe) {
        return true;
    }

    @Override
    public Extraction summarize(SummaryContext ctx) {
        FactSink sink = ctx.sink();
        ContentProbe p = ctx.probe();
        describe(sink, p);
        if (p.format() == ContentProbe.Format.OLE) {
            sink.limit("Legacy Office format; content not read.");
        }
        sink.limit("Content is not machine-readable here.");
        ContentKind kind = switch (p.format()) {
            case PDF, DOCX, XLSX, PPTX -> ContentKind.DOCUMENT;
            case PNG, JPEG, GIF -> ContentKind.IMAGE;
            default -> ContentKind.BINARY;
        };
        return sink.build(kind, sentence(p));
    }

    /**
     * A specialised extractor could not read the file (corrupt, truncated, refused as unsafe): fall back to the
     * type/size facts as BINARY, with a limit line and a WARNING — never an error.
     */
    static Extraction degrade(SummaryContext ctx, String limit) {
        FactSink sink = ctx.sink();
        describe(sink, ctx.probe());
        sink.limit(limit);
        sink.finding(Severity.WARNING, limit, "content");
        return sink.build(ContentKind.BINARY, sentence(ctx.probe()));
    }

    private static void describe(FactSink sink, ContentProbe p) {
        sink.fact("detected type", p.detectedLabel(), "content");
        sink.fact("size", SummaryTemplates.humanSize(p.totalSize()), "content");
        sink.fact("file name", p.fileName(), "record");
    }

    private static String sentence(ContentProbe p) {
        String label = p.detectedLabel();
        return SummaryTemplates.documentSentence(
                Character.toUpperCase(label.charAt(0)) + label.substring(1), p.totalSize(), null, null, null);
    }
}
