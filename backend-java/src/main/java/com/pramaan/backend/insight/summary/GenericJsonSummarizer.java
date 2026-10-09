package com.pramaan.backend.insight.summary;

import com.fasterxml.jackson.databind.JsonNode;
import com.pramaan.backend.insight.summary.SummaryEnums.Severity;
import org.springframework.stereotype.Component;

/**
 * Fallback for any JSON the specific summarizers do not claim: top-level scalar fields, array
 * lengths, and status-like fields ({@code status/result/state/outcome}) down to a nesting depth of
 * {@value JsonSupport#MAX_DEPTH}.
 */
@Component
public class GenericJsonSummarizer implements ContentSummarizer {

    private static final int MAX_NODES = 20_000;
    private static final String[] DATE_KEYS = {"asOf", "collectedAt", "generatedAt", "date", "timestamp", "createdAt"};

    @Override
    public int order() {
        return 40;
    }

    @Override
    public boolean supports(ContentProbe probe) {
        return probe.json() != null;
    }

    private static final class Walk {
        final FactSink sink;
        int visited;
        boolean tooDeep;
        boolean budgetHit;

        Walk(FactSink sink) {
            this.sink = sink;
        }
    }

    @Override
    public Extraction summarize(SummaryContext ctx) {
        FactSink sink = ctx.sink();
        JsonNode root = ctx.probe().json();
        Walk w = new Walk(sink);
        visit(root, "", "", 1, w);
        if (w.tooDeep) {
            sink.limit("JSON nested deeper than " + JsonSupport.MAX_DEPTH + " levels was not inspected.");
        }
        if (w.budgetHit) {
            sink.limit("Only the first " + MAX_NODES + " JSON nodes were inspected.");
        }

        String detail = root.isObject()
                ? SummaryTemplates.plural(root.size(), "top-level field", "top-level fields")
                : SummaryTemplates.plural(root.size(), "item", "items");
        String title = null;
        String date = null;
        if (root.isObject()) {
            String[] t = JsonSupport.firstOf(root, "title", "name");
            title = t == null ? null : FactSink.text(t[1]);
            String[] d = JsonSupport.firstOf(root, DATE_KEYS);
            date = d == null ? null : JsonSupport.day(FactSink.text(d[1]));
        }
        String sentence = SummaryTemplates.documentSentence("JSON data", ctx.probe().totalSize(), detail, title, date);
        return sink.build(ContentKind.STRUCTURED_DATA, sentence);
    }

    /** {@code depth} is the nesting level of {@code node}; the root container is level 1. */
    private void visit(JsonNode node, String ptr, String path, int depth, Walk w) {
        if (++w.visited > MAX_NODES) {
            w.budgetHit = true;
            return;
        }
        if (node.isObject()) {
            for (String key : JsonSupport.sortedFieldNames(node)) {
                JsonNode child = node.get(key);
                String childPtr = JsonSupport.pointer(ptr, key);
                String childPath = path.isEmpty() ? key : path + "." + key;
                if (child.isContainerNode()) {
                    describeContainer(child, childPtr, childPath, depth, w);
                } else {
                    scalar(key, child, childPtr, childPath, depth, w);
                }
            }
        } else if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                JsonNode child = node.get(i);
                String childPtr = JsonSupport.pointer(ptr, i);
                String childPath = path + "[" + i + "]";
                if (child.isContainerNode()) {
                    describeContainer(child, childPtr, childPath, depth, w);
                }
            }
        }
    }

    private void describeContainer(JsonNode child, String ptr, String path, int depth, Walk w) {
        if (child.isArray()) {
            w.sink.fact(path, "array of " + SummaryTemplates.plural(child.size(), "item", "items"), ptr);
        }
        if (depth + 1 > JsonSupport.MAX_DEPTH) {
            w.tooDeep = true;
            return;
        }
        visit(child, ptr, path, depth + 1, w);
    }

    private void scalar(String key, JsonNode value, String ptr, String path, int depth, Walk w) {
        String text = JsonSupport.scalar(value);
        if (text == null) {
            return;
        }
        boolean status = JsonSupport.isStatusKey(key);
        if (depth == 1 || status) {
            w.sink.fact(path, text, ptr);
        }
        if (status && !Redactor.isSensitiveKey(key)) {
            Severity sev = JsonSupport.isNegative(text) ? Severity.WARNING : Severity.INFO;
            w.sink.finding(sev, "Field " + path + " is " + FactSink.text(text) + ".", ptr);
        }
    }
}
