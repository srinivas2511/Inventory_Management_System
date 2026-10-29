package com.mfg.ims.settings.onboarding;

import java.time.Instant;
import java.util.List;

public final class OnboardingDtos {

    private OnboardingDtos() {}

    public record OnboardingStepDto(
            Long id,
            String stepKey,
            String stepLabel,
            boolean completed,
            Long completedBy,
            Instant completedAt,
            int sortOrder
    ) {}

    public record OnboardingStatusDto(
            List<OnboardingStepDto> steps,
            int totalSteps,
            int completedSteps,
            int percentComplete
    ) {}

    public record CompleteStepRequest(String stepKey) {}
}
