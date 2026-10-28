package com.mfg.ims.doccore.itemcategory;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SaveItemCategoryRequest(
        @NotBlank @Size(max = 20) String categoryCode,
        @NotBlank @Size(max = 100) String name,
        Long parentId,
        String itemType) {
}
