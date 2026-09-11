package com.pramaan.backend.onboarding;

import com.pramaan.backend.onboarding.OnboardingScanDtos.ScanRequest;
import com.pramaan.backend.onboarding.OnboardingScanDtos.ScanView;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Staged onboarding scan: register app, resolve frameworks/controls, validate sources,
 *  trigger baseline collection, compute initial posture. Poll {@code GET /scans/{id}}. */
@RestController
@RequestMapping("/api/v1/onboarding/scans")
public class OnboardingScanController {

    private final OnboardingScanService service;

    public OnboardingScanController(OnboardingScanService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ScanView start(@Valid @RequestBody ScanRequest req) {
        return service.trigger(req);
    }

    @GetMapping("/{scanId}")
    public ScanView get(@PathVariable UUID scanId) {
        return service.get(scanId);
    }
}
