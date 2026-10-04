package com.springmfg.ims.plm.dto;

import java.time.Instant;
import java.util.List;

import com.springmfg.ims.plm.domain.ProductLifecycleStatus;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class LifecycleDtos {

    private LifecycleDtos() {
    }

    public record TransitionRequest(
            @NotNull ProductLifecycleStatus target,
            @NotBlank @Size(max = 500) String reason) {
    }

    public record LifecycleEventDto(
            Long id,
            Long productId,
            String fromStatus,
            String toStatus,
            Long ecoId,
            Long changedBy,
            Instant changedAt,
            String reason) {
    }

    public record ProductLifecycleRowDto(
            Long productId,
            String productCode,
            String productName,
            ProductLifecycleStatus lifecycleStatus,
            ProductLifecycleStatus nextStatus) {
    }

    public record ProductLifecycleDto(
            Long productId,
            String productCode,
            String productName,
            ProductLifecycleStatus lifecycleStatus,
            ProductLifecycleStatus nextStatus,
            List<LifecycleEventDto> history) {
    }
}
