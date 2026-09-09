package com.pramaan.backend.rules;

import com.pramaan.backend.common.PageResponse;
import com.pramaan.backend.rules.CheckDtos.CheckDefView;
import com.pramaan.backend.rules.CheckDtos.CheckResultView;
import com.pramaan.backend.rules.CheckDtos.EvaluateRequest;
import com.pramaan.backend.rules.CheckDtos.EvaluationSummary;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Predefined technical checks (deterministic rule evaluation) and their results. */
@RestController
@RequestMapping("/api/v1")
public class CheckController {

    private final RuleCatalog catalog;
    private final RuleEvaluationService evaluation;

    public CheckController(RuleCatalog catalog, RuleEvaluationService evaluation) {
        this.catalog = catalog;
        this.evaluation = evaluation;
    }

    @GetMapping("/checks")
    public List<CheckDefView> checks() {
        return catalog.catalog();
    }

    @PostMapping("/checks/evaluate")
    public EvaluationSummary evaluate(@RequestBody(required = false) EvaluateRequest req) {
        return evaluation.evaluate(req);
    }

    @GetMapping("/check-results")
    public PageResponse<CheckResultView> results(
            @RequestParam(required = false) String applicationSlug,
            @RequestParam(required = false) String framework,
            @RequestParam(required = false) String controlId,
            @RequestParam(required = false) String sourceSystem,
            @RequestParam(required = false) CheckStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return evaluation.list(
                new EvaluateRequest(applicationSlug, framework, controlId, sourceSystem),
                status, page, size);
    }
}
