package com.pramaan.backend.rules;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pramaan.backend.rules.CheckDtos.CheckDefView;
import com.pramaan.backend.rules.CheckDtos.RuleDef;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/**
 * Loads the declarative Phase 1 rule set from {@code classpath:rules/phase1-rules.json}.
 * Rules are data-driven keyword/expectation checks — 100% deterministic, no LLM
 * (behavioural reference: ECS {@code evidence_validation.py}).
 */
@Component
public class RuleCatalog {

    private final List<RuleDef> rules;

    public RuleCatalog(ObjectMapper mapper,
                       @org.springframework.beans.factory.annotation.Value("classpath:rules/phase1-rules.json")
                       Resource resource) {
        try (InputStream in = resource.getInputStream()) {
            this.rules = List.of(mapper.readValue(in, RuleDef[].class));
        } catch (IOException e) {
            throw new UncheckedIOException("cannot load rules/phase1-rules.json", e);
        }
    }

    public List<RuleDef> rules() {
        return rules;
    }

    /** Rules whose framework and controlId match (a rule value of {@code null}/blank/{@code *} is a wildcard). */
    public List<RuleDef> rulesFor(String framework, String controlId) {
        return rules.stream()
                .filter(r -> wildcardMatch(r.framework(), framework)
                        && wildcardMatch(r.controlId(), controlId))
                .toList();
    }

    public List<CheckDefView> catalog() {
        return rules.stream()
                .map(r -> new CheckDefView(r.checkId(), r.collector(), r.technology(),
                        r.controlId(), r.framework(), r.title()))
                .toList();
    }

    private static boolean wildcardMatch(String ruleValue, String actual) {
        if (ruleValue == null || ruleValue.isBlank() || "*".equals(ruleValue)) {
            return true;
        }
        return actual != null && ruleValue.trim().equalsIgnoreCase(actual.trim());
    }
}
