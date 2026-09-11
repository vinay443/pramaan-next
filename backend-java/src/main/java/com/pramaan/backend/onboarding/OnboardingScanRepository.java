package com.pramaan.backend.onboarding;

import com.pramaan.backend.onboarding.domain.OnboardingScan;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OnboardingScanRepository extends JpaRepository<OnboardingScan, UUID> {
}
