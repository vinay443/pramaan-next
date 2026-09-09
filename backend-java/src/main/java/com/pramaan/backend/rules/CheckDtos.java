package com.pramaan.backend.rules;

import com.pramaan.backend.rules.domain.CheckResult;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class CheckDtos {

    private CheckDtos() {}

    /** One predefined deterministic check (rule) the backend can evaluate. Matches contract {@code CheckDefView}. */
    public record CheckDefView(
            String checkId,
            String collector,
            String technology,
            String controlId,
            String framework,
            String title) {}

    /** Optional narrowing of which evidence to (re-)evaluate. */
    public record EvaluateRequest(
            String applicationSlug,
            String framework,
            String controlId,
            String sourceSystem) {}

    public record EvaluationSummary(
            Instant evaluatedAt,
            int evidenceEvaluated,
            int resultsWritten,
            Map<String, Integer> byStatus) {}

    /** Matches contract {@code CheckResultView}. */
    public record CheckResultView(
            String id,
            String evidenceId,
            int evidenceVersion,
            String applicationSlug,
            String checkId,
            String controlId,
            String framework,
            String sourceSystem,
            CheckStatus status,
            String observed,
            String expected,
            String detail,
            Instant collectedAt) {

        public static CheckResultView from(CheckResult r) {
            return new CheckResultView(r.getId().toString(), r.getEvidenceRecordId().toString(),
                    r.getEvidenceVersion(), r.getApplicationSlug(), r.getCheckId(), r.getControlId(),
                    r.getFramework(), r.getSourceSystem(), r.getStatus(), r.getObserved(),
                    r.getExpected(), r.getDetail(), r.getEvaluatedAt());
        }
    }

    /** A declarative rule loaded from {@code classpath:rules/phase1-rules.json}. */
    public record RuleDef(
            String checkId,
            String framework,
            String controlId,
            String collector,
            String technology,
            String title,
            String expected,
            CheckStatus defaultStatus,
            List<Clause> clauses) {

        /** First clause whose {@code contains} substring is present (case-insensitive) wins. */
        public record Clause(String contains, CheckStatus status, String detail) {}
    }
}
