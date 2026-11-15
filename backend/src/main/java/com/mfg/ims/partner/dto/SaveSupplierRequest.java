package com.mfg.ims.partner.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SaveSupplierRequest(
        @Size(max = 20) String supplierCode,
        @NotBlank @Size(max = 150) String name,
        @Size(max = 100) String contactPerson,
        @Size(max = 20) String phone,
        @Size(max = 160) String email,
        @Size(max = 400) String address,
        @Size(max = 200) String addressLine1,
        @Size(max = 200) String addressLine2,
        @Size(max = 100) String city,
        @Size(max = 100) String stateName,
        @Size(max = 10) String pinCode,
        @Size(max = 100) String country,
        @Pattern(regexp = "^(|[A-Z]{5}[0-9]{4}[A-Z]{1})$", message = "Invalid PAN format")
        @Size(max = 10) String pan,
        @Pattern(regexp = "^(|[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z]{1}[1-9A-Z]{1}Z[0-9A-Z]{1})$",
                 message = "Invalid GST number format")
        @Size(max = 15) String gstNumber,
        @Pattern(regexp = "^([A-Z]{3})?$", message = "Use a three-letter currency code such as USD")
        @Size(max = 3) String currencyCode,
        @Size(max = 60) String paymentTerms,
        Long paymentTermsId,
        Integer leadTimeDays,
        String notes,
        Long vendorTypeId,
        Long version) {
}
