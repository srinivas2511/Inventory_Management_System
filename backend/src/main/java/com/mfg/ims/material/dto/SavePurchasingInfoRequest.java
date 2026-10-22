package com.mfg.ims.material.dto;

import jakarta.validation.constraints.Size;

public record SavePurchasingInfoRequest(
        Long preferredSupplierId,
        @Size(max = 10) String purchaseUom,
        @Size(max = 15) String purchaseTaxCode) {
}
