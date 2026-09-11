package com.pramaan.backend.application;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class ApplicationDtos {

    private ApplicationDtos() {}

    public record UpsertRequest(
            @NotBlank @Pattern(regexp = "[a-z0-9][a-z0-9-]{1,118}") String slug,
            @NotBlank String name,
            String businessUnit,
            String criticality,
            String owner,
            List<String> technology) {}

    public record ApplicationView(
            String slug,
            String name,
            String businessUnit,
            String criticality,
            String owner,
            List<String> technology,
            boolean autoCreated,
            boolean active,
            Instant createdAt,
            Instant updatedAt,
            Map<String, Object> onboardingProfile) {

        static ApplicationView from(ApplicationEntity e) {
            List<String> tech = (e.getTechnology() == null || e.getTechnology().isBlank())
                    ? List.of()
                    : List.of(e.getTechnology().split("\\s*,\\s*"));
            return new ApplicationView(e.getSlug(), e.getName(), e.getBusinessUnit(),
                    e.getCriticality(), e.getOwner(), tech, e.isAutoCreated(), e.isActive(),
                    e.getCreatedAt(), e.getUpdatedAt(), e.getOnboardingProfile());
        }
    }
}
