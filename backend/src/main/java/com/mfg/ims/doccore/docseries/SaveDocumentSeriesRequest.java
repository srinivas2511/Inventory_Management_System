package com.mfg.ims.doccore.docseries;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SaveDocumentSeriesRequest(
        @NotBlank @Size(max = 30) String documentKind,
        @NotBlank @Size(max = 20) String seriesCode,
        Long branchId,
        @NotBlank @Size(max = 80) String format,
        String numbering,
        String resetPolicy,
        boolean isDefault,
        boolean statutory) {
}
