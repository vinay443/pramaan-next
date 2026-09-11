package com.pramaan.backend.onboarding;

import com.pramaan.backend.application.ApplicationDtos.UpsertRequest;
import com.pramaan.backend.application.ApplicationService;
import com.pramaan.backend.config.PramaanProperties;
import com.pramaan.backend.evidence.ControlFrameworkCatalog;
import com.pramaan.backend.insight.CompletenessService;
import com.pramaan.backend.insight.ComplianceService;
import com.pramaan.backend.insight.InsightDtos.ComplianceReport;
import com.pramaan.backend.insight.InsightDtos.CompletenessReport;
import com.pramaan.backend.integrations.IntegrationRegistry;
import com.pramaan.backend.onboarding.OnboardingScanDtos.ScanRequest;
import com.pramaan.backend.onboarding.domain.OnboardingScan;
import com.pramaan.backend.onboarding.domain.OnboardingScan.Phase;
import com.pramaan.backend.scheduler.SchedulerDtos.RunRequest;
import com.pramaan.backend.scheduler.SchedulerDtos.RunView;
import com.pramaan.backend.scheduler.SchedulerService;
import com.pramaan.backend.scheduler.domain.SchedulerRun.Trigger;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runs the 5-phase onboarding stepper for one application: register, resolve
 * frameworks/controls, validate evidence sources, trigger baseline collection,
 * compute initial completeness/compliance. Same async shape as {@code SchedulerRunExecutor}.
 */
@Component
public class OnboardingScanExecutor {

    private static final Logger log = LoggerFactory.getLogger(OnboardingScanExecutor.class);

    private final OnboardingScanRepository scans;
    private final ApplicationService applications;
    private final ControlFrameworkCatalog controlFrameworks;
    private final IntegrationRegistry integrations;
    private final SchedulerService scheduler;
    private final CompletenessService completeness;
    private final ComplianceService compliance;
    private final Clock clock;
    private final List<String> defaultSources;

    public OnboardingScanExecutor(OnboardingScanRepository scans, ApplicationService applications,
                                  ControlFrameworkCatalog controlFrameworks, IntegrationRegistry integrations,
                                  SchedulerService scheduler, CompletenessService completeness,
                                  ComplianceService compliance, Clock clock, PramaanProperties props) {
        this.scans = scans;
        this.applications = applications;
        this.controlFrameworks = controlFrameworks;
        this.integrations = integrations;
        this.scheduler = scheduler;
        this.completeness = completeness;
        this.compliance = compliance;
        this.clock = clock;
        this.defaultSources = props.scheduler() != null && props.scheduler().defaultSources() != null
                ? props.scheduler().defaultSources() : List.of();
    }

    @Async
    public void executeAsync(UUID scanId, ScanRequest req) {
        try {
            execute(scanId, req);
        } catch (RuntimeException ex) {
            log.error("onboarding scan {} failed", scanId, ex);
        }
    }

    @Transactional
    public void execute(UUID scanId, ScanRequest req) {
        OnboardingScan scan = scans.findById(scanId).orElseThrow();
        scan.markRunning(clock.instant());
        scans.saveAndFlush(scan);

        List<String> frameworks = req.frameworks() == null ? List.of() : req.frameworks();
        List<String> validatedSources;
        try {
            registerApplication(scan, req);
            resolveFrameworksControls(scan, frameworks);
            validatedSources = validateEvidenceSources(scan, req);
            triggerBaselineCollection(scan, req, frameworks, validatedSources);
            computeInitialPosture(scan, frameworks);
        } catch (RuntimeException ex) {
            if (scan.getCurrentPhase() != null) {
                scan.phaseFailed(Phase.valueOf(scan.getCurrentPhase()), ex.getMessage());
            }
            scan.fail(clock.instant(), "onboarding failed at " + scan.getCurrentPhase() + ": " + ex.getMessage());
            scans.save(scan);
            log.warn("onboarding scan {} failed: {}", scanId, ex.getMessage());
            return;
        }

        scan.complete(clock.instant(), "application '" + req.slug() + "' onboarded");
        scans.save(scan);
        log.info("onboarding scan {} complete for {}", scanId, req.slug());
    }

    private void registerApplication(OnboardingScan scan, ScanRequest req) {
        scan.phaseStarted(Phase.REGISTER_APPLICATION);
        applications.upsert(new UpsertRequest(req.slug(), req.name(), req.businessUnit(),
                req.criticality(), req.owner(), req.technology()));
        applications.setActive(req.slug(), true);
        applications.setOnboardingProfile(req.slug(), req.onboardingProfile());
        scan.phaseCompleted(Phase.REGISTER_APPLICATION, "application '" + req.slug() + "' registered and onboarded");
        scans.saveAndFlush(scan);
    }

    private void resolveFrameworksControls(OnboardingScan scan, List<String> frameworks) {
        scan.phaseStarted(Phase.RESOLVE_FRAMEWORKS_CONTROLS);
        Set<String> wanted = frameworks.stream().map(f -> f.toUpperCase(Locale.ROOT)).collect(Collectors.toSet());
        long controlCount = wanted.isEmpty() ? 0 : controlFrameworks.all().stream()
                .filter(cf -> cf.frameworks().stream().anyMatch(f -> wanted.contains(f.toUpperCase(Locale.ROOT))))
                .count();
        scan.phaseCompleted(Phase.RESOLVE_FRAMEWORKS_CONTROLS,
                controlCount + " control(s) resolved across " + wanted.size() + " framework(s)");
        scans.saveAndFlush(scan);
    }

    private List<String> validateEvidenceSources(OnboardingScan scan, ScanRequest req) {
        scan.phaseStarted(Phase.VALIDATE_EVIDENCE_SOURCES);
        List<String> requested = req.sources() == null || req.sources().isEmpty() ? defaultSources : req.sources();
        Set<String> known = Set.copyOf(integrations.available());
        List<String> unknown = requested.stream()
                .map(s -> s.toUpperCase(Locale.ROOT))
                .filter(s -> !known.contains(s))
                .toList();
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException("unknown evidence source(s): " + unknown + " (available: " + known + ")");
        }
        List<String> validated = requested.stream().map(s -> s.toUpperCase(Locale.ROOT)).distinct().toList();
        scan.phaseCompleted(Phase.VALIDATE_EVIDENCE_SOURCES, validated.size() + " source(s) validated: " + validated);
        scans.saveAndFlush(scan);
        return validated;
    }

    private void triggerBaselineCollection(OnboardingScan scan, ScanRequest req, List<String> frameworks,
                                           List<String> sources) {
        scan.phaseStarted(Phase.TRIGGER_BASELINE_COLLECTION);
        RunView run = scheduler.trigger(new RunRequest(List.of(req.slug()), frameworks, sources,
                req.requestedBy() == null ? "onboarding" : req.requestedBy()), Trigger.MANUAL);
        scan.setSchedulerRunId(run.runId());
        scan.phaseCompleted(Phase.TRIGGER_BASELINE_COLLECTION,
                "baseline collection run " + run.runId() + " triggered (" + run.status() + ")");
        scans.saveAndFlush(scan);
    }

    private void computeInitialPosture(OnboardingScan scan, List<String> frameworks) {
        scan.phaseStarted(Phase.COMPUTE_INITIAL_POSTURE);
        String primaryFramework = frameworks.isEmpty() ? null : frameworks.get(0);
        CompletenessReport cr = completeness.forApplication(scan.getApplicationSlug(), primaryFramework);
        ComplianceReport cp = compliance.forApplication(scan.getApplicationSlug(), primaryFramework);
        scan.setCompletenessPct(cr.completenessPct());
        scan.setCompliancePct(cp.compliancePct());
        scan.phaseCompleted(Phase.COMPUTE_INITIAL_POSTURE,
                "initial completeness " + cr.completenessPct() + "%, compliance " + cp.compliancePct() + "%");
        scans.saveAndFlush(scan);
    }
}
