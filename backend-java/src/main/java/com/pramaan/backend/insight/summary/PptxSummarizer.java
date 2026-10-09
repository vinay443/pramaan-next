package com.pramaan.backend.insight.summary;

import com.pramaan.backend.insight.summary.ContentProbe.Format;
import java.io.IOException;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** PowerPoint decks: document properties and slide count only — slide text is not read. */
@Component
public class PptxSummarizer implements ContentSummarizer {

    private static final Pattern SLIDE = Pattern.compile("ppt/slides/slide\\d+\\.xml");
    private static final Set<String> PARTS = Set.of(DocumentFacts.CORE, DocumentFacts.APP);

    @Override
    public int order() {
        return 90;
    }

    @Override
    public boolean supports(ContentProbe probe) {
        return probe.format() == Format.PPTX;
    }

    @Override
    public Extraction summarize(SummaryContext ctx) {
        FactSink sink = ctx.sink();
        ContentProbe p = ctx.probe();
        OoxmlPackage pkg;
        try {
            pkg = OoxmlPackage.read(p.bytes(), PARTS::contains);
        } catch (OoxmlPackage.Rejected e) {
            return BinaryMetadataSummarizer.degrade(ctx, "The PPTX was not read: " + e.getMessage() + ".");
        } catch (IOException | RuntimeException e) {
            return BinaryMetadataSummarizer.degrade(ctx, "The PPTX could not be read (corrupt or truncated).");
        }
        DocumentFacts.Core core = DocumentFacts.readCore(pkg, sink);
        long slides = pkg.names().stream().filter(n -> SLIDE.matcher(n).matches()).count();
        sink.fact("slides", String.valueOf(slides), "ppt/slides");
        sink.limit("Slide text is not read; only document properties and the slide count are shown.");
        return sink.build(ContentKind.DOCUMENT, SummaryTemplates.fileSentence("Presentation", p.totalSize(),
                SummaryTemplates.plural(slides, "slide", "slides"), FactSink.text(core.title()),
                DocumentFacts.day(core.modified()), null));
    }
}
