package com.pramaan.backend.onboarding;

import static org.assertj.core.api.Assertions.assertThat;

import com.pramaan.backend.application.ApplicationDtos.ApplicationView;
import com.pramaan.backend.application.ApplicationService;
import com.pramaan.backend.onboarding.OnboardingScanDtos.PhaseView;
import com.pramaan.backend.onboarding.OnboardingScanDtos.ScanRequest;
import com.pramaan.backend.onboarding.OnboardingScanDtos.ScanView;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class OnboardingScanServiceTest {

    @Autowired OnboardingScanService service;
    @Autowired OnboardingScanExecutor executor;
    @Autowired ApplicationService applications;

    private ScanRequest request(String slug, List<String> sources) {
        return new ScanRequest(slug, "New Application", "Retail Banking", "HIGH", "jane.doe",
                List.of("Java", "Spring Boot"), List.of("PostgreSQL"), List.of("Nginx"), List.of("Linux"),
                List.of("PCI_DSS"), sources,
                true, true, "PROD", "AWS", "CONFIDENTIAL", "OAUTH2", true, true,
                "s3://evidence/new-app", "CMDB-00123", "tester");
    }

    @Test
    void runsAllFivePhasesAndOnboardsTheApplication() {
        ScanRequest req = request("onboard-new-app", List.of("MOCK_JIRA", "MOCK_GITHUB"));

        ScanView started = service.trigger(req);
        assertThat(started.status()).isEqualTo("PENDING");

        UUID scanId = UUID.fromString(started.scanId());
        executor.execute(scanId, req);

        ScanView done = service.get(scanId);
        assertThat(done.status()).isEqualTo("COMPLETED");
        assertThat(done.phases()).hasSize(5);
        assertThat(done.phases()).allMatch(p -> p.status().equals("COMPLETED"));
        assertThat(done.schedulerRunId()).isNotBlank();
        assertThat(done.completenessPct()).isNotNull();
        assertThat(done.compliancePct()).isNotNull();

        ApplicationView app = applications.get("onboard-new-app");
        assertThat(app.active()).isTrue();
        assertThat(app.name()).isEqualTo("New Application");
        assertThat(app.onboardingProfile()).containsEntry("environment", "PROD")
                .containsEntry("cmdbIdentifier", "CMDB-00123")
                .containsEntry("customerFacing", true);
    }

    @Test
    void failsFastOnAnUnknownEvidenceSourceButKeepsEarlierPhaseResults() {
        ScanRequest req = request("onboard-bad-source", List.of("NOT_A_REAL_SOURCE"));

        ScanView started = service.trigger(req);
        UUID scanId = UUID.fromString(started.scanId());
        executor.execute(scanId, req);

        ScanView done = service.get(scanId);
        assertThat(done.status()).isEqualTo("FAILED");

        PhaseView register = phase(done, "REGISTER_APPLICATION");
        assertThat(register.status()).isEqualTo("COMPLETED");

        PhaseView resolve = phase(done, "RESOLVE_FRAMEWORKS_CONTROLS");
        assertThat(resolve.status()).isEqualTo("COMPLETED");

        PhaseView validate = phase(done, "VALIDATE_EVIDENCE_SOURCES");
        assertThat(validate.status()).isEqualTo("FAILED");
        assertThat(validate.message()).contains("NOT_A_REAL_SOURCE");

        PhaseView trigger = phase(done, "TRIGGER_BASELINE_COLLECTION");
        assertThat(trigger.status()).isEqualTo("PENDING");

        // the application was still registered even though baseline collection never ran
        assertThat(applications.get("onboard-bad-source").active()).isTrue();
    }

    private static PhaseView phase(ScanView view, String name) {
        return view.phases().stream().filter(p -> p.phase().equals(name)).findFirst().orElseThrow();
    }
}
