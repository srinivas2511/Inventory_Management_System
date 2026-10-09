package com.springmfg.ims.partner.dto;

import java.util.List;

import jakarta.validation.constraints.NotNull;

/** The complete approved list: whatever is not in {@code ids} is removed. */
public record SetApprovedItemsRequest(@NotNull List<Long> ids) {
}
