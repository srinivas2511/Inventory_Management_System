package com.mfg.ims.analytics.reportbuilder;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

public record CreateSavedReportRequest(
        @NotBlank String name,
        @NotBlank String sourceView,
        Map<String, Object> definition,
        String shareScope
) {}
