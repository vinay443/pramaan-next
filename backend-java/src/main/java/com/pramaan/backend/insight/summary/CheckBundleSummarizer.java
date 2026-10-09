package com.pramaan.backend.insight.summary;

import com.fasterxml.jackson.databind.JsonNode;
import com.pramaan.backend.insight.summary.SummaryEnums.Severity;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * The technical-check bundle the collectors write. Two real shapes carry the same check rows:
 * the Go agent payload ({@code collector, technology, target, simulated, collectedAt, facts,
 * findings[]}) and the Java simulated-technology bundle ({@code collector, technology,
 * application, framework, control, asOf, simulated, status, checks[]}). Each row is
 * {@code {checkId, controlId, framework, title, status, observed, expected, detail}}.
 */
@Component
public class CheckBundleSummarizer implements ContentSummarizer {

    private static final String[] ARRAY_NAMES = {"findings", "checks"};
    private static final String[] HEADER_KEYS = {
            "collector", "technology", "target", "application", "framework", "control",
            "asOf", "collectedAt", "simulated", "status"};
    private static final int MAX_BUNDLE_FACTS = 15;

    @Override
    public int order() {
        return 10;
    }

    @Override
    public boolean supports(ContentProbe probe) {
        return probe.json() != null && probe.json().isObject() && checksName(probe.json()) != null;
    }

    private static String checksName(JsonNode root) {
        for (String name : ARRAY_NAMES) {
            JsonNode arr = root.get(name);
            if (arr != null && arr.isArray()) {
                for (JsonNode el : arr) {
                    if (el.isObject() && el.has("checkId")) {
                        return name;
                    }
                }
            }
        }
        return null;
    }

    private enum Bucket { PASS, WARN, FAIL, NOT_APPLICABLE, OTHER }

    private static Bucket bucket(String status) {
        if (status == null) {
            return Bucket.OTHER;
        }
        return switch (status.strip().toUpperCase(Locale.ROOT)) {
            case "PASS", "PASSED", "OK" -> Bucket.PASS;
            case "WARN", "WARNING" -> Bucket.WARN;
            case "FAIL", "FAILED", "ERROR" -> Bucket.FAIL;
            case "NOT_APPLICABLE", "N/A", "NA" -> Bucket.NOT_APPLICABLE;
            default -> Bucket.OTHER;
        };
    }

    @Override
    public Extraction summarize(SummaryContext ctx) {
        FactSink sink = ctx.sink();
        JsonNode root = ctx.probe().json();
        String arrayName = checksName(root);

        for (String key : HEADER_KEYS) {
            sink.fact(key, JsonSupport.text(root, key), JsonSupport.pointer("", key));
        }
        JsonNode facts = root.get("facts");
        if (facts != null && facts.isObject()) {
            int n = 0;
            for (String key : JsonSupport.sortedFieldNames(facts)) {
                if (n++ == MAX_BUNDLE_FACTS) {
                    break;
                }
                sink.fact("facts." + key, JsonSupport.scalar(facts.get(key)),
                        JsonSupport.pointer(JsonSupport.pointer("", "facts"), key));
            }
        }

        int pass = 0, warn = 0, fail = 0, na = 0, total = 0;
        List<String> problems = new ArrayList<>();
        JsonNode rows = root.get(arrayName);
        String arrayPtr = JsonSupport.pointer("", arrayName);
        for (int i = 0; i < rows.size(); i++) {
            JsonNode row = rows.get(i);
            if (!row.isObject()) {
                continue;
            }
            total++;
            String ptr = JsonSupport.pointer(arrayPtr, i);
            String checkId = FactSink.text(JsonSupport.text(row, "checkId"));
            String status = JsonSupport.text(row, "status");
            String title = FactSink.text(JsonSupport.text(row, "title"));
            String observed = FactSink.text(JsonSupport.text(row, "observed"));
            String expected = FactSink.text(JsonSupport.text(row, "expected"));
            String detail = FactSink.text(JsonSupport.text(row, "detail"));
            String id = checkId == null ? "#" + i : checkId;

            List<String> parts = new ArrayList<>();
            parts.add(status == null ? "no status" : status.strip().toUpperCase(Locale.ROOT));
            if (title != null) parts.add(title);
            if (observed != null) parts.add("observed " + observed);
            if (expected != null) parts.add("expected " + expected);
            sink.fact("check " + id, String.join("; ", parts), ptr);

            Bucket b = bucket(status);
            switch (b) {
                case PASS -> pass++;
                case WARN -> warn++;
                case FAIL -> fail++;
                case NOT_APPLICABLE -> na++;
                case OTHER -> { }
            }
            if (b == Bucket.FAIL || b == Bucket.WARN) {
                String problem = SummaryTemplates.checkProblem(id, observed, expected, detail);
                problems.add(problem);
                sink.finding(b == Bucket.FAIL ? Severity.FAIL : Severity.WARNING, problem, ptr);
            }
        }
        String sentence = SummaryTemplates.bundleWithProblems(
                SummaryTemplates.bundleSentence(pass, total, warn, fail, na), problems);
        return sink.build(ContentKind.CHECK_BUNDLE, sentence);
    }
}
