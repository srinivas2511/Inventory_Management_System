package com.mfg.ims.doccore.customfield;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SaveCustomFieldRequest(
        @NotBlank @Size(max = 40) String entityType,
        @NotBlank @Size(max = 40) String fieldKey,
        @NotBlank @Size(max = 100) String label,
        @NotBlank @Size(max = 15) String fieldType,
        String options,
        boolean mandatory,
        boolean showInList,
        int sortOrder) {
}
