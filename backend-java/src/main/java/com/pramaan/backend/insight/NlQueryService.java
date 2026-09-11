package com.pramaan.backend.insight;

import com.pramaan.backend.ai.ChatModel;
import com.pramaan.backend.application.ApplicationService;
import com.pramaan.backend.common.ApiException;
import com.pramaan.backend.evidence.EvidenceDtos.DeterministicQueryResult;
import com.pramaan.backend.evidence.EvidenceQueryService;
import com.pramaan.backend.evidence.EvidenceQueryService.EvidenceFilter;
import com.pramaan.backend.insight.InsightDtos.CompletenessReport;
import com.pramaan.backend.insight.InsightDtos.ComplianceReport;
import com.pramaan.backend.insight.InsightDtos.NlQueryResult;
import com.pramaan.backend.insight.InsightDtos.ReuseResult;
import com.pramaan.backend.insight.InsightDtos.SimilarEvidence;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Natural-language evidence queries. The mapping from question to a query is a
 * deterministic keyword router (no LLM in the loop). Structured questions are
 * answered from the deterministic query / completeness / compliance services;
 * open "what evidence do we have for X" questions are answered by
 * <b>RAG</b> — semantic retrieval over the embedding store (in-memory or
 * pgvector) via {@link EvidenceReuseService}, grounded strictly in the retrieved
 * records (evidence ids cited; "No evidence found in the ECS repository." when
 * retrieval is empty). The chat model only phrases the already-computed answer
 * and is deterministic under {@code pramaan.ai.mode=mock}.
 *
 * <p>A question that matches no intent returns {@code supported=false} plus
 * {@link #SUPPORTED_QUESTION_TYPES}. It is <b>not</b> routed to a generic fallback
 * query, and the model is not invoked — this router is a keyword matcher, not a
 * general-purpose assistant, and it says so rather than guessing.
 */
@Service
@Transactional(readOnly = true)
public class NlQueryService {

    private final EvidenceQueryService evidenceQueries;
    private final CompletenessService completeness;
    private final ComplianceService compliance;
    private final ApplicationService applications;
    private final EvidenceReuseService reuse;
    private final ChatModel chat;
    private final Clock clock;

    public NlQueryService(EvidenceQueryService evidenceQueries, CompletenessService completeness,
                          ComplianceService compliance, ApplicationService applications,
                          EvidenceReuseService reuse, ChatModel chat, Clock clock) {
        this.evidenceQueries = evidenceQueries;
        this.completeness = completeness;
        this.compliance = compliance;
        this.applications = applications;
        this.reuse = reuse;
        this.chat = chat;
        this.clock = clock;
    }

    public NlQueryResult answer(String question, String applicationSlug) {
        if (question == null || question.isBlank()) {
            throw ApiException.badRequest("question is required");
        }
        String q = question.toLowerCase();
        String app = applicationSlug == null || applicationSlug.isBlank() ? null : applicationSlug.trim();

        Intent intent = route(q);
        Map<String, Object> answer = new LinkedHashMap<>();
        String matched;

        // No intent matched. Say so plainly and stop — do NOT fall through to a
        // generic query, and do NOT let the model phrase an answer it has no data for.
        if (intent == Intent.UNSUPPORTED) {
            answer.put("supported", false);
            answer.put("supportedQuestionTypes", SUPPORTED_QUESTION_TYPES);
            answer.put("answerText", UNSUPPORTED_TEXT);
            return new NlQueryResult(question, intent.label, "unsupported", answer, UNSUPPORTED_TEXT,
                    chat.name(), chat.deterministic(), chat.modelGenerated(),
                    false, SUPPORTED_QUESTION_TYPES, clock.instant());
        }

        switch (intent) {
            case COMPLETENESS -> {
                matched = "completeness";
                answer.putAll(completenessAnswer(app));
            }
            case COMPLIANCE -> {
                matched = "compliance";
                answer.putAll(complianceAnswer(app));
            }
            case EVIDENCE_LOOKUP -> {
                matched = "evidence-lookup";
                answer.putAll(evidenceLookupAnswer(question, app));
            }
            default -> {
                matched = intent.query;
                DeterministicQueryResult r = evidenceQueries.run(intent.query,
                        new EvidenceFilter(app, null, null, null, null, null, null, 0, 100_000));
                answer.put("answerText", r.answerText());
                answer.put("counts", r.counts());
                answer.put("rowCount", r.rows().size());
            }
        }

        answer.put("supported", true);
        String narrative = narrate(question, intent, answer);
        return new NlQueryResult(question, intent.label, matched, answer, narrative,
                chat.name(), chat.deterministic(), chat.modelGenerated(),
                true, SUPPORTED_QUESTION_TYPES, clock.instant());
    }

    // ---- deterministic routing ------------------------------------------

    /**
     * What this router can actually answer. Surfaced verbatim to the caller when a
     * question matches nothing, so the UI never has to guess at the supported set.
     */
    static final List<String> SUPPORTED_QUESTION_TYPES = List.of(
            "Missing / incomplete control coverage — e.g. \"which controls are missing evidence?\"",
            "Overall compliance posture — e.g. \"what is our compliance posture?\"",
            "Stale evidence — e.g. \"what evidence is out of date?\"",
            "Evidence freshness — e.g. \"how recent is our evidence?\"",
            "Duplicate evidence — e.g. \"is any evidence duplicated?\"",
            "Latest evidence per control — e.g. \"what is the current evidence?\"",
            "Evidence source breakdown — e.g. \"where does our evidence come from?\"",
            "Evidence lookup by topic — e.g. \"what evidence do we have for SSH root login?\"");

    private static final String UNSUPPORTED_TEXT =
            "This question isn't supported. Question routing is a deterministic keyword "
                    + "router, not a general-purpose model — it answers only the question types "
                    + "listed in supportedQuestionTypes.";

    private enum Intent {
        STALE("evidence older than the freshness window", "stale-evidence"),
        FRESHNESS("evidence freshness distribution", "freshness"),
        SOURCES("which systems evidence comes from", "source-breakdown"),
        DUPLICATES("evidence content that is duplicated", "duplicates"),
        LATEST("the current evidence per control", "latest-per-control"),
        COMPLETENESS("which expected controls lack current evidence", null),
        COMPLIANCE("overall compliance posture", null),
        EVIDENCE_LOOKUP("evidence records matching the question (semantic retrieval)", null),
        UNSUPPORTED("no supported question type matched", null);

        final String label;
        final String query;

        Intent(String label, String query) {
            this.label = label;
            this.query = query;
        }
    }

    private static Intent route(String q) {
        if (contains(q, "missing", "gap", "not covered", "incomplete", "completeness", "coverage")) {
            return Intent.COMPLETENESS;
        }
        if (contains(q, "compliance", "compliant", "posture", "pass rate", "how are we doing", "audit ready")) {
            return Intent.COMPLIANCE;
        }
        if (contains(q, "stale", "out of date", "outdated", "expired", "too old")) {
            return Intent.STALE;
        }
        if (contains(q, "fresh", "how recent", "how old", "age of")) {
            return Intent.FRESHNESS;
        }
        if (contains(q, "duplicate", "identical", "same content")) {
            return Intent.DUPLICATES;
        }
        if (contains(q, "latest", "current evidence", "most recent")) {
            return Intent.LATEST;
        }
        if (contains(q, "what evidence", "which evidence", "have evidence", "any evidence",
                "show me evidence", "find evidence", "list evidence", "is there evidence",
                "evidence for ", "evidence about", "evidence on ", "evidence do we",
                "evidence supporting", "evidence showing")) {
            return Intent.EVIDENCE_LOOKUP;
        }
        // Source breakdown is now matched explicitly rather than being the catch-all,
        // so that a genuinely unrecognised question can be reported as unsupported.
        if (contains(q, "come from", "comes from", "came from", "where does", "where do",
                "source", "sources", "which system", "what system", "collected from", "origin")) {
            return Intent.SOURCES;
        }
        return Intent.UNSUPPORTED;
    }

    private static boolean contains(String haystack, String... needles) {
        for (String n : needles) {
            if (haystack.contains(n)) {
                return true;
            }
        }
        return false;
    }

    // ---- answers that need Phase 2 services -----------------------------

    private Map<String, Object> completenessAnswer(String app) {
        Map<String, Object> m = new LinkedHashMap<>();
        List<String> apps = app != null ? List.of(app)
                : applications.list().stream().map(a -> a.slug()).toList();
        int expected = 0;
        int covered = 0;
        int missing = 0;
        for (String slug : apps) {
            CompletenessReport r = completeness.forApplication(slug, null);
            expected += r.expected();
            covered += r.covered();
            missing += r.missing() + r.stale();
        }
        m.put("applications", apps);
        m.put("expectedControls", expected);
        m.put("withCurrentEvidence", covered);
        m.put("missingOrStale", missing);
        m.put("completenessPct", expected == 0 ? 0.0 : CompletenessService.round(100.0 * covered / expected));
        m.put("answerText", "%d of %d expected controls have current evidence across %d application(s)."
                .formatted(covered, expected, apps.size()));
        return m;
    }

    private Map<String, Object> complianceAnswer(String app) {
        Map<String, Object> m = new LinkedHashMap<>();
        List<String> apps = app != null ? List.of(app)
                : applications.list().stream().map(a -> a.slug()).toList();
        int expected = 0;
        int compliant = 0;
        for (String slug : apps) {
            ComplianceReport r = compliance.forApplication(slug, null);
            expected += r.expected();
            compliant += r.compliant();
        }
        m.put("applications", apps);
        m.put("expectedControls", expected);
        m.put("compliantControls", compliant);
        m.put("compliancePct", expected == 0 ? 0.0 : CompletenessService.round(100.0 * compliant / expected));
        m.put("answerText", "%d of %d expected controls are compliant across %d application(s)."
                .formatted(compliant, expected, apps.size()));
        return m;
    }

    /** RAG: retrieve evidence semantically, ground the answer in the retrieved records, cite ids. */
    private Map<String, Object> evidenceLookupAnswer(String question, String app) {
        // low floor: the mock embedding is a bag-of-tokens vectoriser, so on-topic
        // free-text queries score modestly against JSON-shaped stored evidence.
        ReuseResult rr = reuse.similarToText(question, 6, 0.1);
        List<Map<String, Object>> hits = rr.matches().stream()
                .filter(s -> app == null || app.equalsIgnoreCase(s.applicationSlug()))
                .map(NlQueryService::hit)
                .toList();

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("vectorStore", rr.vectorStore());
        m.put("embeddingModel", rr.embeddingModel());
        m.put("indexed", rr.indexed());
        m.put("retrieved", hits);
        m.put("evidenceCitations", hits.stream().map(h -> h.get("evidenceId")).toList());
        m.put("grounded", !hits.isEmpty());
        m.put("answerText", hits.isEmpty()
                ? "No evidence found in the ECS repository."
                : "%d evidence record(s) match: %s.".formatted(hits.size(),
                        hits.stream().map(h -> h.get("applicationSlug") + "/" + h.get("controlId"))
                                .distinct().toList()));
        return m;
    }

    private static Map<String, Object> hit(SimilarEvidence s) {
        Map<String, Object> h = new LinkedHashMap<>();
        h.put("evidenceId", s.evidenceId());
        h.put("applicationSlug", s.applicationSlug());
        h.put("framework", s.framework());
        h.put("controlId", s.controlId());
        h.put("score", s.score());
        return h;
    }

    private String narrate(String question, Intent intent, Map<String, Object> answer) {
        String system = intent == Intent.EVIDENCE_LOOKUP
                ? "You are an audit assistant. Answer ONLY from the retrieved evidence records listed. "
                + "Cite evidence by its id. If 'grounded' is false or no records are listed, reply "
                + "exactly: No evidence found in the ECS repository. Never state a fact that is not in a "
                + "retrieved record."
                : "You are an audit assistant. Answer the user's question using ONLY the "
                + "structured result. Do not add numbers or facts that are not present.";
        StringBuilder user = new StringBuilder("Question: ").append(question).append('\n')
                .append("Interpreted as: ").append(intent.label).append('\n')
                .append("Result:\n");
        answer.forEach((k, v) -> user.append("- ").append(k).append(": ").append(v).append('\n'));
        return chat.complete(system, user.toString());
    }
}
