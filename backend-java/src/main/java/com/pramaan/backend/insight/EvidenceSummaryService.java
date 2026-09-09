package com.pramaan.backend.insight;

import com.pramaan.backend.ai.ChatModel;
import com.pramaan.backend.evidence.EvidenceDtos.EvidenceView;
import com.pramaan.backend.evidence.EvidenceQueryService;
import com.pramaan.backend.insight.InsightDtos.EvidenceSummary;
import com.pramaan.backend.rules.domain.CheckResult;
import com.pramaan.backend.rules.repo.CheckResultRepository;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AI evidence summaries. The prompt is grounded strictly in the stored evidence
 * (metadata + deterministic check verdicts + a content excerpt); the model is
 * instructed not to add anything. With {@code pramaan.ai.mode=mock} the summary
 * is deterministic.
 */
@Service
@Transactional(readOnly = true)
public class EvidenceSummaryService {

    private static final int CONTENT_EXCERPT = 1500;

    private final EvidenceQueryService evidence;
    private final CheckResultRepository checkResults;
    private final ChatModel chat;
    private final Clock clock;

    public EvidenceSummaryService(EvidenceQueryService evidence, CheckResultRepository checkResults,
                                  ChatModel chat, Clock clock) {
        this.evidence = evidence;
        this.checkResults = checkResults;
        this.chat = chat;
        this.clock = clock;
    }

    public EvidenceSummary summarize(UUID evidenceId) {
        EvidenceView ev = evidence.get(evidenceId); // 404 if unknown
        String content = evidence.latestContentText(evidenceId).orElse(null);
        List<CheckResult> verdicts = checkResults.findByEvidenceRecordId(evidenceId);

        List<String> grounded = new ArrayList<>();
        grounded.add("application: " + ev.applicationSlug());
        grounded.add("framework: " + ev.framework());
        grounded.add("control: " + ev.controlId());
        grounded.add("source system: " + ev.sourceSystem());
        if (ev.latest() != null) {
            grounded.add("collected at: " + ev.latest().collectedAt());
            grounded.add("current version: " + ev.currentVersion());
        }
        ev.tags().forEach((k, v) -> grounded.add("tag " + k + ": " + v));
        verdicts.forEach(cr -> grounded.add(
                "check " + cr.getCheckId() + " -> " + cr.getStatus() + " (" + cr.getDetail() + ")"));

        if (content == null && verdicts.isEmpty()) {
            return new EvidenceSummary(evidenceId.toString(), ev.applicationSlug(), ev.framework(),
                    ev.controlId(), chat.name(), chat.deterministic(),
                    "No evidence content is available to summarize.", grounded, clock.instant());
        }

        String system = "You are a compliance evidence summarizer. Summarize ONLY the facts below. "
                + "Do not infer, speculate, or add controls, dates, or outcomes that are not present.";
        StringBuilder user = new StringBuilder("Evidence facts:\n");
        grounded.forEach(g -> user.append("- ").append(g).append('\n'));
        if (content != null) {
            String excerpt = content.length() > CONTENT_EXCERPT ? content.substring(0, CONTENT_EXCERPT) : content;
            user.append("\nContent excerpt:\n").append(excerpt).append('\n');
        }
        user.append("\nWrite a 2-4 sentence summary of what this evidence shows for the control.");

        String summary = chat.complete(system, user.toString());
        return new EvidenceSummary(evidenceId.toString(), ev.applicationSlug(), ev.framework(),
                ev.controlId(), chat.name(), chat.deterministic(), summary, grounded, clock.instant());
    }
}
