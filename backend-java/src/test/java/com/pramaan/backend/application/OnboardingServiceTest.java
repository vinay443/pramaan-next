package com.pramaan.backend.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pramaan.backend.application.OnboardingService.OnboardingPlan;
import com.pramaan.backend.application.OnboardingService.OnboardingResult;
import com.pramaan.backend.scheduler.SchedulerDtos.RunRequest;
import com.pramaan.backend.scheduler.SchedulerService;
import com.pramaan.backend.scheduler.domain.SchedulerRun.Trigger;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class OnboardingServiceTest {

    @Autowired OnboardingService onboarding;
    @Autowired ApplicationService applications;
    @Autowired SchedulerService scheduler;

    @Test
    void planReflectsTheConfigurationCatalogueWithKnownSources() {
        OnboardingPlan plan = onboarding.plan();
        assertThat(plan.total()).isEqualTo(3);
        assertThat(plan.items()).anyMatch(i -> i.slug().equals("payments")
                && i.sources().contains("SHAREPOINT") && i.sources().contains("SERVICENOW"));
        assertThat(plan.items()).allMatch(i -> i.unknownSources().isEmpty());
    }

    @Test
    void applyIsIdempotentAndUsesTheExistingApplicationStore() {
        OnboardingResult first = onboarding.apply(false);
        assertThat(first.created()).isEqualTo(3);
        assertThat(applications.list()).hasSize(3);

        OnboardingResult again = onboarding.apply(false);
        assertThat(again.created()).isZero();
        assertThat(again.updated()).isEqualTo(3);
        assertThat(applications.list()).hasSize(3);
    }

    @Test
    void applyWithCollectStartsOneSchedulerRunOverTheCataloguedSources() {
        OnboardingResult r = onboarding.apply(true);
        assertThat(r.collectionRunId()).isNotBlank();
    }

    @Test
    void selectiveApplyOnlyOnboardsTheNamedApplications() {
        OnboardingResult r = onboarding.apply(List.of("payments"), false);
        assertThat(r.slugs()).containsExactly("payments");
        assertThat(applications.list()).hasSize(1);
    }

    @Test
    void schedulerIsBlockedUntilAtLeastOneApplicationIsOnboarded() {
        assertThatThrownBy(() -> scheduler.trigger(
                new RunRequest(List.of(), List.of(), List.of("MOCK_JIRA"), "t"), Trigger.MANUAL))
                .hasMessageContaining("No applications onboarded");

        onboarding.apply(List.of("payments"), false);
        // now allowed
        scheduler.trigger(new RunRequest(List.of(), List.of(), List.of("MOCK_JIRA"), "t"), Trigger.MANUAL);
    }

    @Test
    void deboardDeactivatesSoTheSchedulerNoLongerCountsIt() {
        onboarding.apply(List.of("payments"), false);
        applications.setActive("payments", false);
        assertThat(applications.anyOnboarded()).isFalse();
        assertThat(applications.get("payments").active()).isFalse(); // row + evidence retained
    }
}
