package com.pramaan.backend.application;

import com.pramaan.backend.application.OnboardingService.OnboardingPlan;
import com.pramaan.backend.application.OnboardingService.OnboardingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Use Case 12 — configuration-driven multi-application onboarding. */
@RestController
@RequestMapping("/api/v1/onboarding")
public class OnboardingController {

    private final OnboardingService service;

    public OnboardingController(OnboardingService service) {
        this.service = service;
    }

    /** What the onboarding catalogue would do (no writes). */
    @GetMapping("/plan")
    public OnboardingPlan plan() {
        return service.plan();
    }

    /**
     * Onboard catalogued applications (idempotent). {@code slugs} selects which ones
     * (comma-separated or repeated); omitted = the whole catalogue. {@code collect=true}
     * also starts an initial collection run for the onboarded set.
     */
    @PostMapping("/apply")
    public OnboardingResult apply(@RequestParam(required = false) java.util.List<String> slugs,
                                  @RequestParam(defaultValue = "false") boolean collect) {
        return service.apply(slugs, collect);
    }
}
