package com.springmfg.ims.partner.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SaveCustomerRequest(
        @NotBlank @Size(max = 20) String customerCode,
        @NotBlank @Size(max = 150) String name,
        @Size(max = 100) String contactPerson,
        @Size(max = 20) String phone,
        @Size(max = 160) String email,
        @Size(max = 400) String address,
        @Pattern(regexp = "^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z]{1}[1-9A-Z]{1}Z[0-9A-Z]{1}$",
                 message = "Invalid GST number format")
        @Size(max = 15) String gstNumber,
        @Size(max = 60) String paymentTerms,
        Long version) {
}
