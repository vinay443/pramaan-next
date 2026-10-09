package com.pramaan.backend.insight.summary;

import com.pramaan.backend.insight.summary.ContentProbe.Format;
import java.io.ByteArrayInputStream;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.springframework.stereotype.Component;

/**
 * PNG / JPEG / GIF evidence: width x height from the image header via javax.imageio. No pixel decoding,
 * no OCR — the content of a screenshot is for a human to look at.
 */
@Component
public class ImageSummarizer implements ContentSummarizer {

    public static final String LIMIT =
            "Image evidence — contents are not machine-readable here (no OCR); confirm visually.";

    @Override
    public int order() {
        return 60;
    }

    @Override
    public boolean supports(ContentProbe probe) {
        return probe.format() == Format.PNG || probe.format() == Format.JPEG || probe.format() == Format.GIF;
    }

    @Override
    public Extraction summarize(SummaryContext ctx) {
        FactSink sink = ctx.sink();
        ContentProbe p = ctx.probe();
        String detail = null;
        // memory-cache stream: never touches the disk (ImageIO's default cache may use a temp file)
        try (ImageInputStream in = new MemoryCacheImageInputStream(new ByteArrayInputStream(p.bytes()))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (readers.hasNext()) {
                ImageReader reader = readers.next();
                try {
                    reader.setInput(in, true, true);
                    int w = reader.getWidth(0);
                    int h = reader.getHeight(0);
                    detail = w + "x" + h + " px";
                    sink.fact("format", reader.getFormatName().toUpperCase(java.util.Locale.ROOT), "image header");
                    sink.fact("dimensions", w + " x " + h + " px", "image header");
                } finally {
                    reader.dispose();
                }
            }
        } catch (Exception e) {
            detail = null;
        }
        if (detail == null) {
            sink.limit("The image header could not be read, so its dimensions are unknown.");
        }
        sink.fact("size", SummaryTemplates.humanSize(p.totalSize()), "content");
        sink.limit(LIMIT);
        String label = p.detectedLabel();
        return sink.build(ContentKind.IMAGE, SummaryTemplates.documentSentence(
                Character.toUpperCase(label.charAt(0)) + label.substring(1), p.totalSize(), detail, null, null));
    }
}
