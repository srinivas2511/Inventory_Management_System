package com.mfg.ims.production.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record UpdateRoutingRequest(@NotNull @Valid List<OperationUpdate> operations) {

    public record OperationUpdate(
            @NotNull Long id,
            String name,
            String workcenter,
            Integer plannedDurationMins) {}
}
