package com.pramaan.backend.insight.summary;

import com.fasterxml.jackson.databind.JsonNode;
import com.pramaan.backend.insight.summary.SummaryEnums.Severity;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Evidence written by a predefined query run ({@code collector: "predefined-query"}). Real shapes:
 * simulated/RHEL/NGINX-style {@code command + output (text)}, JDBC-style {@code command + rowCount +
 * rows[]}, and Aerospike/NGINX-style {@code executed + result (array or object)}.
 */
@Component
public class QueryResultSummarizer implements ContentSummarizer {

    private static final String[] HEADER_KEYS = {
            "technology", "application", "controlId", "controlFamily", "asOf", "live", "simulated",
            "status", "command", "executed", "target", "node"};
    private static final Set<String> FLAG_KEYS = Set.of("status", "result", "state", "outcome",
            "compliant", "compliance", "pass", "passed");
    private static final Pattern TEXT_FLAG = Pattern.compile(
            "(?i)compliant\\s*[=:]\\s*false|non-?compliant|\\bfail(?:ed)?\\b|\\berror\\b");
    private static final int FIRST_ROWS = 5;
    private static final int MAX_COLUMNS = 4;
    private static final int MAX_OUTPUT_LINES_SHOWN = 3;

    @Override
    public int order() {
        return 30;
    }

    @Override
    public boolean supports(ContentProbe probe) {
        JsonNode j = probe.json();
        if (j == null || !j.isObject()) {
            return false;
        }
        String collector = JsonSupport.text(j, "collector");
        return (collector != null && collector.equalsIgnoreCase("predefined-query"))
                || (j.has("rowCount") && j.get("rows") != null && j.get("rows").isArray());
    }

    @Override
    public Extraction summarize(SummaryContext ctx) {
        FactSink sink = ctx.sink();
        JsonNode root = ctx.probe().json();
        String controlName = JsonSupport.text(root, "controlName");
        String name = FactSink.text(controlName != null ? controlName : JsonSupport.text(root, "controlId"));

        sink.fact("controlName", controlName, JsonSupport.pointer("", "controlName"));
        for (String key : HEADER_KEYS) {
            sink.fact(key, JsonSupport.text(root, key), JsonSupport.pointer("", key));
        }
        String status = JsonSupport.text(root, "status");
        if (status != null && JsonSupport.isNegative(status)) {
            sink.finding(Severity.FAIL, "The query run reported status " + FactSink.text(status) + ".",
                    JsonSupport.pointer("", "status"));
        }

        JsonNode rows = root.get("rows");
        JsonNode result = root.get("result");
        String output = JsonSupport.text(root, "output");
        String sentence;
        if (rows != null && rows.isArray()) {
            sentence = rowsSentence(sink, name, rows, JsonSupport.pointer("", "rows"), root.get("rowCount"));
        } else if (result != null && result.isArray()) {
            sentence = rowsSentence(sink, name, result, JsonSupport.pointer("", "result"), null);
        } else if (output != null) {
            sentence = outputSentence(sink, name, output);
        } else {
            if (result != null && result.isObject()) {
                int n = 0;
                for (String key : JsonSupport.sortedFieldNames(result)) {
                    if (n++ == 10) {
                        break;
                    }
                    sink.fact("result." + key, JsonSupport.scalar(result.get(key)),
                            JsonSupport.pointer(JsonSupport.pointer("", "result"), key));
                }
            }
            sentence = SummaryTemplates.queryEmptySentence(name);
        }
        return sink.build(ContentKind.QUERY_RESULT, sentence);
    }

    private String rowsSentence(FactSink sink, String name, JsonNode rows, String arrayPtr, JsonNode declaredCount) {
        if (declaredCount != null && declaredCount.isNumber()) {
            sink.fact("rowCount", declaredCount.asText(), JsonSupport.pointer("", "rowCount"));
            if (declaredCount.asInt() != rows.size()) {
                sink.finding(Severity.WARNING, "rowCount says " + declaredCount.asInt() + " but "
                        + rows.size() + " rows are stored.", JsonSupport.pointer("", "rowCount"));
            }
        }
        int flagged = 0;
        for (int i = 0; i < rows.size(); i++) {
            JsonNode row = rows.get(i);
            String ptr = JsonSupport.pointer(arrayPtr, i);
            boolean bad = row.isObject() && isFlagged(row);
            if (bad) {
                flagged++;
            }
            if (i < FIRST_ROWS) {
                sink.fact("row " + (i + 1), describeRow(row), ptr);
            }
            if (bad && flagged <= FIRST_ROWS) {
                sink.finding(Severity.WARNING, "Row " + (i + 1) + " is flagged: " + describeRow(row) + ".", ptr);
            }
        }
        return SummaryTemplates.querySentence(name, rows.size(), flagged);
    }

    private static boolean isFlagged(JsonNode row) {
        for (String key : JsonSupport.sortedFieldNames(row)) {
            if (FLAG_KEYS.contains(key.toLowerCase(Locale.ROOT)) && JsonSupport.isNegative(JsonSupport.scalar(row.get(key)))) {
                return true;
            }
        }
        return false;
    }

    private static String describeRow(JsonNode row) {
        if (!row.isObject()) {
            String s = JsonSupport.scalar(row);
            return s == null ? null : FactSink.text(s);
        }
        List<String> cols = new ArrayList<>();
        var names = row.fieldNames();
        while (names.hasNext() && cols.size() < MAX_COLUMNS) {
            String key = names.next();
            JsonNode v = row.get(key);
            String raw = v.isContainerNode() ? (v.isArray() ? "[" + v.size() + " items]" : "{…}") : JsonSupport.scalar(v);
            if (raw != null) {
                cols.add(key + "=" + FactSink.value(key, raw));
            }
        }
        return cols.isEmpty() ? null : String.join(", ", cols);
    }

    private String outputSentence(FactSink sink, String name, String output) {
        String ptr = JsonSupport.pointer("", "output");
        String[] lines = output.split("\r\n|\n|\r");
        int nonEmpty = 0;
        int flagged = 0;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.isBlank()) {
                continue;
            }
            nonEmpty++;
            if (TEXT_FLAG.matcher(line).find()) {
                flagged++;
                if (flagged <= FIRST_ROWS) {
                    sink.finding(Severity.WARNING, "Output line " + (i + 1) + " is flagged: "
                            + FactSink.text(line.strip()) + ".", ptr + " (line " + (i + 1) + ")");
                }
            }
            if (nonEmpty <= MAX_OUTPUT_LINES_SHOWN) {
                sink.fact("output line " + (i + 1), line.strip(), ptr + " (line " + (i + 1) + ")");
            }
        }
        return SummaryTemplates.queryOutputSentence(name, nonEmpty, flagged);
    }
}
