package com.springmfg.ims.crm.dto;

import java.util.List;

import jakarta.validation.constraints.NotNull;

/** Replaces the customer's tag set. */
public record SetCustomerTagsRequest(@NotNull List<Long> tagIds) {}
