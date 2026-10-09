package com.pramaan.backend.insight.summary;

import com.fasterxml.jackson.databind.JsonNode;
import com.pramaan.backend.insight.summary.SummaryEnums.Severity;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Objects pulled from enterprise systems. The mock integrations write
 * {@code {sourceSystem, application, framework, control, objectType, asOf, status, reference}}
 * (objectType e.g. change_ticket, incident_record, pull_request_review, policy_page); a live
 * connector may add approver / date / linked-item fields, which are picked up when present.
 */
@Component
public class IntegrationObjectSummarizer implements ContentSummarizer {

    private static final String[] SYSTEM_KEYS = {"sourceSystem", "system", "source"};
    private static final String[] ID_KEYS = {"id", "key", "number", "reference", "ticketId", "issueKey",
            "incidentId", "prNumber", "sys_id"};
    private static final String[] STATUS_KEYS = {"status", "state"};
    private static final String[] APPROVER_KEYS = {"approvedBy", "approver", "reviewedBy", "reviewer"};
    private static final String[] APPROVAL_DATE_KEYS = {"approvedAt", "approvedOn", "approvalDate", "reviewedAt"};
    private static final String[] OTHER_DATE_KEYS = {"createdAt", "updatedAt", "resolvedAt", "closedAt", "mergedAt", "asOf"};
    private static final int MAX_LINKED_SHOWN = 5;

    @Override
    public int order() {
        return 20;
    }

    @Override
    public boolean supports(ContentProbe probe) {
        JsonNode j = probe.json();
        return j != null && j.isObject() && JsonSupport.text(j, "objectType") != null;
    }

    @Override
    public Extraction summarize(SummaryContext ctx) {
        FactSink sink = ctx.sink();
        JsonNode root = ctx.probe().json();
        Set<String> done = new HashSet<>();

        String[] system = JsonSupport.firstOf(root, SYSTEM_KEYS);
        String[] id = JsonSupport.firstOf(root, ID_KEYS);
        String[] status = JsonSupport.firstOf(root, STATUS_KEYS);
        String[] approver = JsonSupport.firstOf(root, APPROVER_KEYS);
        String[] approvedOn = JsonSupport.firstOf(root, APPROVAL_DATE_KEYS);
        String objectType = JsonSupport.text(root, "objectType");

        emit(sink, root, done, system);
        emit(sink, root, done, new String[]{"objectType", objectType});
        emit(sink, root, done, id);
        emit(sink, root, done, status);
        emit(sink, root, done, approver);
        emit(sink, root, done, approvedOn);
        for (String key : OTHER_DATE_KEYS) {
            emit(sink, root, done, new String[]{key, JsonSupport.text(root, key)});
        }
        for (String key : JsonSupport.sortedFieldNames(root)) {
            if (isLinkKey(key)) {
                emitLinked(sink, root, key);
                done.add(key);
            }
        }
        for (String key : JsonSupport.sortedFieldNames(root)) {
            if (done.contains(key)) {
                continue;
            }
            JsonNode v = root.get(key);
            String ptr = JsonSupport.pointer("", key);
            if (v.isArray()) {
                sink.fact(key, SummaryTemplates.plural(v.size(), "item", "items"), ptr);
            } else {
                sink.fact(key, JsonSupport.scalar(v), ptr);
            }
        }

        if (status != null && JsonSupport.isNegative(status[1])) {
            sink.finding(Severity.WARNING, "The " + SummaryTemplates.clip(objectType.replace('_', ' '))
                    + " status is " + FactSink.text(status[1]) + ".", JsonSupport.pointer("", status[0]));
        }

        String sentence = SummaryTemplates.integrationSentence(
                system == null ? null : FactSink.text(system[1]), FactSink.text(objectType),
                id == null ? null : FactSink.text(id[1]),
                status == null ? null : FactSink.text(status[1]),
                approver == null ? null : FactSink.text(approver[1]),
                approvedOn == null ? null : JsonSupport.day(FactSink.text(approvedOn[1])));
        return sink.build(ContentKind.INTEGRATION_OBJECT, sentence);
    }

    private static void emit(FactSink sink, JsonNode root, Set<String> done, String[] keyValue) {
        if (keyValue == null || keyValue[1] == null) {
            return;
        }
        done.add(keyValue[0]);
        sink.fact(keyValue[0], keyValue[1], JsonSupport.pointer("", keyValue[0]));
    }

    private static boolean isLinkKey(String key) {
        String k = key.toLowerCase(Locale.ROOT);
        return k.startsWith("linked") || k.startsWith("related") || k.equals("parent") || k.equals("links");
    }

    private static void emitLinked(FactSink sink, JsonNode root, String key) {
        JsonNode v = root.get(key);
        String ptr = JsonSupport.pointer("", key);
        if (v.isArray()) {
            List<String> shown = new ArrayList<>();
            for (JsonNode el : v) {
                if (shown.size() == MAX_LINKED_SHOWN) {
                    break;
                }
                String s = el.isObject() ? firstIdentifier(el) : JsonSupport.scalar(el);
                if (s != null) {
                    shown.add(FactSink.text(s));
                }
            }
            String count = SummaryTemplates.plural(v.size(), "item", "items");
            sink.fact(key, shown.isEmpty() ? count : count + ": " + String.join(", ", shown), ptr);
        } else if (v.isObject()) {
            String s = firstIdentifier(v);
            sink.fact(key, s == null ? null : FactSink.text(s), ptr);
        } else {
            sink.fact(key, JsonSupport.scalar(v), ptr);
        }
    }

    private static String firstIdentifier(JsonNode obj) {
        String[] hit = JsonSupport.firstOf(obj, ID_KEYS);
        return hit == null ? null : hit[1];
    }
}
