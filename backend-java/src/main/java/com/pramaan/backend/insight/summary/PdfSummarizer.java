package com.pramaan.backend.insight.summary;

import com.pramaan.backend.insight.summary.ContentProbe.Format;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

/**
 * PDF evidence via Apache PDFBox: page count, document information, encryption, and the text of the
 * first {@value #MAX_PAGES} pages (at most 200 KB, within a 15-second wall-clock guard) for
 * {@link DocumentSignals}. Held entirely in memory. An encrypted PDF's text is never read.
 */
@Component
public class PdfSummarizer implements ContentSummarizer {

    static final int MAX_PAGES = 20;
    private static final String INFO = "PDF document information";
    private static final ExecutorService POOL = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "pdf-summary");
        t.setDaemon(true);
        return t;
    });

    private final Duration timeout;

    public PdfSummarizer() {
        this(Duration.ofSeconds(15));
    }

    /** For tests: a shorter (or zero) wall-clock guard. */
    PdfSummarizer(Duration timeout) {
        this.timeout = timeout;
    }

    @Override
    public int order() {
        return 65;
    }

    @Override
    public boolean supports(ContentProbe probe) {
        return probe.format() == Format.PDF;
    }

    private record Read(List<DocumentSignals.Unit> units, int pagesRead, boolean textCapped, boolean timedOut) {}

    @Override
    public Extraction summarize(SummaryContext ctx) {
        FactSink sink = ctx.sink();
        ContentProbe p = ctx.probe();
        PDDocument doc;
        try {
            doc = Loader.loadPDF(p.bytes(), "", null, null, IOUtils.createMemoryOnlyStreamCache());
        } catch (InvalidPasswordException e) {
            sink.fact("encrypted", "yes", INFO);
            sink.limit("The PDF is encrypted (password required); its text was not read.");
            return sink.build(ContentKind.DOCUMENT, SummaryTemplates.fileSentence("PDF", p.totalSize(), null, null,
                    null, null));
        } catch (IOException | RuntimeException e) {
            return BinaryMetadataSummarizer.degrade(ctx, "The PDF could not be read (corrupt or truncated).");
        }
        try (doc) {
            int pages = doc.getNumberOfPages();
            PDDocumentInformation info = doc.getDocumentInformation();
            String title = null;
            String modified = null;
            if (info != null) {
                title = DocumentFacts.clean(info.getTitle());
                modified = date(info.getModificationDate());
                sink.fact("pages", String.valueOf(pages), INFO);
                sink.fact("title", title, INFO);
                sink.fact("author", DocumentFacts.clean(info.getAuthor()), INFO);
                sink.fact("creator", DocumentFacts.clean(info.getCreator()), INFO);
                sink.fact("producer", DocumentFacts.clean(info.getProducer()), INFO);
                sink.fact("created", date(info.getCreationDate()), INFO);
                sink.fact("modified", modified, INFO);
            } else {
                sink.fact("pages", String.valueOf(pages), INFO);
            }
            boolean encrypted = doc.isEncrypted();
            sink.fact("encrypted", encrypted ? "yes" : "no", INFO);

            List<DocumentSignals.Unit> units = List.of();
            if (encrypted) {
                sink.limit("The PDF is encrypted; its text was not read.");
            } else {
                Read read = readText(doc, pages);
                units = read.units();
                if (read.timedOut()) {
                    sink.limit("Text extraction stopped after " + timeout.toSeconds() + " seconds; only part of the text was read.");
                }
                if (read.textCapped()) {
                    sink.limit("Text was read up to " + (DocumentFacts.MAX_TEXT_CHARS / 1000) + " KB; the rest was not analysed.");
                }
                if (pages > MAX_PAGES) {
                    sink.limit("Text was read from the first " + MAX_PAGES + " of " + pages + " pages.");
                }
            }
            DocumentFacts.Outcome out = encrypted ? new DocumentFacts.Outcome(null, null)
                    : DocumentFacts.signals(ctx, units, p.fileName(), title);
            String shownTitle = title != null ? title : out.firstHeading();
            return sink.build(ContentKind.DOCUMENT, SummaryTemplates.fileSentence("PDF", p.totalSize(),
                    SummaryTemplates.plural(pages, "page", "pages"), FactSink.text(shownTitle),
                    DocumentFacts.day(modified), out.approver()));
        } catch (IOException | RuntimeException e) {
            return BinaryMetadataSummarizer.degrade(ctx, "The PDF could not be read (corrupt or truncated).");
        }
    }

    private Read readText(PDDocument doc, int pages) {
        long deadline = System.nanoTime() + timeout.toNanos();
        Future<Read> f = POOL.submit(() -> extract(doc, pages, deadline));
        try {
            return f.get(timeout.toMillis() + 5_000, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            f.cancel(true);
            return new Read(List.of(), 0, false, true);
        } catch (InterruptedException e) {
            f.cancel(true);
            Thread.currentThread().interrupt();
            return new Read(List.of(), 0, false, true);
        } catch (ExecutionException e) {
            return new Read(List.of(), 0, false, false);
        }
    }

    private Read extract(PDDocument doc, int pages, long deadline) throws IOException {
        PDFTextStripper stripper = new PDFTextStripper();
        List<DocumentSignals.Unit> units = new ArrayList<>();
        long chars = 0;
        int read = 0;
        boolean capped = false;
        boolean timedOut = false;
        for (int page = 1; page <= Math.min(pages, MAX_PAGES); page++) {
            if (System.nanoTime() - deadline >= 0 || Thread.currentThread().isInterrupted()) {
                timedOut = true;
                break;
            }
            stripper.setStartPage(page);
            stripper.setEndPage(page);
            String text = stripper.getText(doc);
            read++;
            if (chars + text.length() > DocumentFacts.MAX_TEXT_CHARS) {
                text = text.substring(0, (int) (DocumentFacts.MAX_TEXT_CHARS - chars));
                capped = true;
            }
            chars += text.length();
            if (!text.isBlank()) {
                units.add(new DocumentSignals.Unit("page " + page, text));
            }
            if (capped) {
                break;
            }
        }
        return new Read(units, read, capped, timedOut);
    }

    private static String date(Calendar c) {
        return c == null ? null : c.toInstant().toString();
    }
}
