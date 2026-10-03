package com.springmfg.ims.crm.dto;

import jakarta.validation.constraints.Size;

/** Existing customer to attach to; when null a new customer is created from the lead. */
public record ConvertLeadRequest(
        Long existingCustomerId,
        @Size(max = 200) String opportunityDescription) {}
