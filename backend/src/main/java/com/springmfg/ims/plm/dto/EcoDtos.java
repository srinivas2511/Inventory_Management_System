package com.springmfg.ims.plm.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.springmfg.ims.plm.domain.EcoChangeCategory;
import com.springmfg.ims.plm.domain.EcoItemType;
import com.springmfg.ims.plm.domain.EcoStatus;
import com.springmfg.ims.plm.domain.EcoUrgency;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class EcoDtos {

    private EcoDtos() {
    }

    public record SaveEcoItemRequest(
            @NotNull EcoItemType itemType,
            @NotNull Long productId,
            @NotBlank @Size(max = 300) String changeDescription) {
    }

    public record SaveEcoRequest(
            @NotBlank @Size(max = 200) String title,
            String description,
            @NotBlank @Size(max = 500) String reason,
            @NotNull EcoChangeCategory changeCategory,
            EcoUrgency urgency,
            LocalDate effectivityDate,
            String impactAssessment,
            @Valid List<SaveEcoItemRequest> items) {
    }

    public record ReasonRequest(@NotBlank @Size(max = 500) String reason) {
    }

    public record EcoItemDto(
            Long id,
            EcoItemType itemType,
            Long productId,
            String productCode,
            String productName,
            Long oldBomId,
            String oldBomLabel,
            Long newBomId,
            String newBomLabel,
            String changeDescription) {
    }

    public record EcoSummaryDto(
            Long id,
            String ecoNumber,
            String title,
            EcoChangeCategory changeCategory,
            EcoUrgency urgency,
            EcoStatus status,
            LocalDate effectivityDate,
            Long initiatedBy,
            int itemCount,
            Instant createdAt) {
    }

    public record EcoDto(
            Long id,
            String ecoNumber,
            String title,
            String description,
            String reason,
            EcoChangeCategory changeCategory,
            EcoUrgency urgency,
            LocalDate effectivityDate,
            String impactAssessment,
            EcoStatus status,
            Long initiatedBy,
            Long approvedBy,
            Instant approvedAt,
            Instant implementedAt,
            String statusReason,
            Instant createdAt,
            List<EcoItemDto> items,
            List<String> allowedActions) {
    }
}
