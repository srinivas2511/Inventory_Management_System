package com.springmfg.ims.qualityplus.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.springmfg.ims.qualityplus.domain.SpcChartType;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class SpcDtos {

    private SpcDtos() {
    }

    public record SaveChartRequest(
            @NotNull Long productId,
            @NotBlank @Size(max = 60) String parameterName,
            @NotNull SpcChartType chartType,
            @Min(1) @Max(10) Integer subgroupSize,
            BigDecimal usl,
            BigDecimal lsl) {
    }

    /** Spec limits are replaced as given (null clears one); active is left alone when null. */
    public record UpdateChartRequest(BigDecimal usl, BigDecimal lsl, Boolean active) {
    }

    /**
     * X-bar/R charts take the measured values in {@code values}; P charts take {@code sampleSize} and
     * {@code defectCount}; C charts take {@code defectCount}.
     */
    public record AddPointsRequest(List<BigDecimal> values, Integer sampleSize, Integer defectCount,
                                   Instant measuredAt) {
    }

    public record ChartSummaryDto(
            Long id, Long productId, String productCode, String productName, String parameterName,
            SpcChartType chartType, int subgroupSize, boolean limitsEstablished, boolean active,
            long pointCount, long outOfControlCount) {
    }

    public record PointDto(
            Long id, Long inspectionId, int subgroupNo, int measurementNo, BigDecimal measuredValue,
            Instant measuredAt, BigDecimal subgroupMean, BigDecimal subgroupRange, Integer sampleSize,
            Integer defectCount, BigDecimal pointUcl, BigDecimal pointLcl, boolean outOfControl,
            String violationRule) {
    }

    public record ChartDetailDto(
            ChartSummaryDto summary,
            BigDecimal cl, BigDecimal ucl, BigDecimal lcl,
            BigDecimal clR, BigDecimal uclR, BigDecimal lclR,
            BigDecimal usl, BigDecimal lsl, Instant limitsEstablishedAt,
            List<PointDto> points) {
    }

    public record CapabilityDto(
            Long chartId, LocalDate from, LocalDate to, BigDecimal cp, BigDecimal cpk, BigDecimal cpu,
            BigDecimal cpl, BigDecimal mean, BigDecimal stdDev, int count) {
    }
}
