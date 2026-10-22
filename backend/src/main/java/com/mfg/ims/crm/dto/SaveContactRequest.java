package com.mfg.ims.crm.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SaveContactRequest(
        Long customerId,
        @NotBlank @Size(max = 60) String firstName,
        @Size(max = 60) String lastName,
        @Email @Size(max = 160) String email,
        @Size(max = 20) String phone,
        @Size(max = 80) String designation,
        @Size(max = 60) String department,
        Long version) {}
