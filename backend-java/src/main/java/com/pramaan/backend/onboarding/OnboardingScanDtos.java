package com.pramaan.backend.onboarding;

import com.pramaan.backend.onboarding.domain.OnboardingScan;
import com.pramaan.backend.onboarding.domain.OnboardingScan.Phase;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class OnboardingScanDtos {

    private OnboardingScanDtos() {}

    /** Rich onboarding-form request. Everything beyond the core application fields is
     *  stored in the application's additive {@code onboardingProfile} JSON column. */
    public record ScanRequest(
            @NotBlank @Pattern(regexp = "[a-z0-9][a-z0-9-]{1,118}") String slug,
            @NotBlank String name,
            String businessUnit,
            String criticality,
            String owner,
            List<String> technology,
            List<String> dbTechnology,
            List<String> middlewareTechnology,
            List<String> osTechnology,
            List<String> frameworks,
            List<String> sources,
            Boolean customerFacing,
            Boolean internetFacing,
            String environment,
            String hostingCloud,
            String dataClassification,
            String authType,
            Boolean drRequired,
            Boolean backupRequired,
            String objectStorageLocation,
            String cmdbIdentifier,
            String requestedBy) {

        /** Everything not backed by a typed {@code ApplicationEntity} column. */
        Map<String, Object> onboardingProfile() {
            Map<String, Object> profile = new java.util.LinkedHashMap<>();
            profile.put("dbTechnology", dbTechnology == null ? List.of() : dbTechnology);
            profile.put("middlewareTechnology", middlewareTechnology == null ? List.of() : middlewareTechnology);
            profile.put("osTechnology", osTechnology == null ? List.of() : osTechnology);
            profile.put("customerFacing", customerFacing);
            profile.put("internetFacing", internetFacing);
            profile.put("environment", environment);
            profile.put("hostingCloud", hostingCloud);
            profile.put("dataClassification", dataClassification);
            profile.put("authType", authType);
            profile.put("drRequired", drRequired);
            profile.put("backupRequired", backupRequired);
            profile.put("objectStorageLocation", objectStorageLocation);
            profile.put("cmdbIdentifier", cmdbIdentifier);
            return profile;
        }
    }

    public record PhaseView(String phase, String status, String message) {}

    public record ScanView(
            String scanId,
            String applicationSlug,
            String status,
            String currentPhase,
            List<PhaseView> phases,
            String schedulerRunId,
            Double completenessPct,
            Double compliancePct,
            String message,
            Instant createdAt,
            Instant startedAt,
            Instant finishedAt) {

        public static ScanView from(OnboardingScan s) {
            List<PhaseView> phases = new ArrayList<>();
            for (Phase p : Phase.values()) {
                String raw = s.getPhaseSummary().get(p.name());
                if (raw == null) {
                    phases.add(new PhaseView(p.name(), "PENDING", null));
                    continue;
                }
                int sep = raw.indexOf('|');
                String status = sep < 0 ? raw : raw.substring(0, sep);
                String message = sep < 0 || sep == raw.length() - 1 ? null : raw.substring(sep + 1);
                phases.add(new PhaseView(p.name(), status, message));
            }
            return new ScanView(s.getId().toString(), s.getApplicationSlug(), s.getStatus().name(),
                    s.getCurrentPhase(), phases, s.getSchedulerRunId(), s.getCompletenessPct(),
                    s.getCompliancePct(), s.getMessage(), s.getCreatedAt(), s.getStartedAt(), s.getFinishedAt());
        }
    }
}
