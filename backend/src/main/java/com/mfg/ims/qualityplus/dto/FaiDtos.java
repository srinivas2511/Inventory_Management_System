package com.mfg.ims.qualityplus.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import com.mfg.ims.qualityplus.domain.FaiStatus;
import com.mfg.ims.qualityplus.domain.FaiTrigger;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class FaiDtos {

    private FaiDtos() {
    }

    public record CreateFaiRequest(@NotNull Long productId) {
    }

    public record StartFaiRequest(@NotNull Long productionOrderId) {
    }

    public record CompleteFaiRequest(@Size(max = 1000) String summary) {
    }

    public record FaiSummaryDto(
            Long id, String faiNumber, String productCode, String productName, FaiTrigger triggerType,
            String triggerReference, FaiStatus status, Instant createdAt, Instant completedAt) {
    }

    public record FaiDto(
            Long id, String faiNumber, Long productId, String productCode, String productName,
            FaiTrigger triggerType, String triggerReference, Long inspectionPlanId, String planCode,
            Long productionOrderId, String productionOrderNumber, Long fgBatchId, String fgBatchNumber,
            Long inspectionId, String inspectionNumber, FaiStatus status, Set<FaiStatus> nextStatuses,
            String resultSummary, Long completedBy, Instant completedAt, Instant createdAt) {
    }

    /** One measured value for an item of the FAI's inspection. */
    public record FaiResultValue(@NotNull Long itemId, @NotNull BigDecimal actualValue) {
    }

    public record SubmitFaiResultsRequest(@NotEmpty @Valid List<FaiResultValue> values) {
    }

    public record FaiReportItemDto(
            Long itemId, Integer seq, String parameterName, String specification, BigDecimal lowerLimit, BigDecimal upperLimit,
            String unit, BigDecimal actualValue, String actualText, String result, boolean mandatory) {
    }

    /** Everything the printable first article report shows. */
    public record FaiReportDto(FaiDto fai, List<FaiReportItemDto> items, Instant generatedAt) {
    }
}
